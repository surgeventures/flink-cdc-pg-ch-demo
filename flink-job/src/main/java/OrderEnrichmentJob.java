import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import com.ververica.cdc.connectors.postgres.PostgreSQLSource;
import com.ververica.cdc.debezium.DebeziumSourceFunction;
import com.ververica.cdc.debezium.JsonDebeziumDeserializationSchema;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.co.KeyedCoProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Properties;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

public class OrderEnrichmentJob {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2); 

        Properties debeziumProperties = new Properties();

        Properties orderSourceProperties = new Properties();
        orderSourceProperties.setProperty("metrics.registry.enable", "false");
        orderSourceProperties.setProperty("metrics.jmx.enable", "false");
        orderSourceProperties.setProperty("database.server.name", "orders_server");

        Properties customerSourceProperties = new Properties();
        customerSourceProperties.setProperty("metrics.registry.enable", "false");
        customerSourceProperties.setProperty("metrics.jmx.enable", "false");
        customerSourceProperties.setProperty("database.server.name", "customers_server");

        // Orders CDC Source
        DebeziumSourceFunction<String> orderSource = PostgreSQLSource.<String>builder()
            .hostname("postgres1")
            .port(5432)
            .database("orders_db")
            .schemaList("public")
            .tableList("public.orders")
            .username("user")
            .password("password")
            .decodingPluginName("pgoutput")
            .deserializer(new JsonDebeziumDeserializationSchema())
            .debeziumProperties(orderSourceProperties)
            .build();

        // Customers CDC Source
        DebeziumSourceFunction<String> customerSource = PostgreSQLSource.<String>builder()
            .hostname("postgres2")
            .port(5432)
            .database("customers_db")
            .schemaList("public")
            .tableList("public.customers")
            .username("user")
            .password("password")
            .decodingPluginName("pgoutput")
            .deserializer(new JsonDebeziumDeserializationSchema())
            .debeziumProperties(customerSourceProperties) 
            .build();

        env.addSource(orderSource)
           .connect(env.addSource(customerSource))
           .keyBy(
                orderJson -> {
                    try {
                        return new ObjectMapper().readTree(orderJson)
                            .get("after")
                            .get("customer_id")
                            .asText();
                    } catch (Exception e) {
                        return "";
                    }
                },
                customerJson -> {
                    try {
                        return new ObjectMapper().readTree(customerJson)
                            .get("after")
                            .get("customer_id")
                            .asText();
                    } catch (Exception e) {
                        return "";
                    }
                }
           )
           .process(new JoinFunction())
           .addSink(new ClickHouseSink());

        env.execute("Order Enrichment CDC Job");
    }

    static class JoinFunction extends KeyedCoProcessFunction<String, String, String, String> {
        private transient ObjectMapper objectMapper;
        private MapState<String, JsonNode> customerState;
        private ListState<JsonNode> pendingOrders;  // Buffer for orders waiting for customer data

        @Override
        public void open(Configuration config) {
            objectMapper = new ObjectMapper();
            customerState = getRuntimeContext().getMapState(
                new MapStateDescriptor<>("customers", String.class, JsonNode.class)
            );
            pendingOrders = getRuntimeContext().getListState(
                new ListStateDescriptor<>("pending-orders", JsonNode.class)
            );
        }

        private void processOrder(JsonNode order, Collector<String> out) throws Exception {
            String customerId = order.get("customer_id").asText();
            JsonNode customer = customerState.get(customerId);
            
            if (customer == null) {
                pendingOrders.add(order);
                System.out.println("Buffering order for customer: " + customerId);
                return;
            }

            ObjectNode result = objectMapper.createObjectNode();
            result.put("order_id", order.get("order_id").asText());
            result.put("customer_id", customerId);
            result.put("customer_name", customer.get("name").asText());
            result.put("customer_email", customer.get("email").asText());
            result.put("order_date", order.get("order_date").asText());
            result.put("total_amount", order.get("total_amount").decimalValue());
            result.put("status", order.get("status").asText());
            result.put("created_at", customer.get("created_at").asText());
            out.collect(result.toString());
        }

        @Override
        public void processElement1(String orderJson, Context ctx, Collector<String> out) throws Exception {
            JsonNode order = objectMapper.readTree(orderJson).get("after");
            if (order != null) {
                processOrder(order, out);
            }
        }

        @Override
        public void processElement2(String customerJson, Context ctx, Collector<String> out) throws Exception {
            JsonNode customer = objectMapper.readTree(customerJson).get("after");
            if (customer != null) {
                String customerId = customer.get("customer_id").asText();
                customerState.put(customerId, customer);
                System.out.println("Added customer: " + customerId + ", processing pending orders");
                
                // Process any pending orders for this customer
                Iterator<JsonNode> iterator = pendingOrders.get().iterator();
                List<JsonNode> remainingOrders = new ArrayList<>();
                
                while (iterator.hasNext()) {
                    JsonNode order = iterator.next();
                    if (order.get("customer_id").asText().equals(customerId)) {
                        processOrder(order, out);
                    } else {
                        remainingOrders.add(order);
                    }
                }
                
                // Update pending orders
                pendingOrders.update(remainingOrders);
            }
        }
    }

    static class ClickHouseSink implements SinkFunction<String> {
        @Override
        public void invoke(String value, Context context) throws Exception {
            try (Connection conn = DriverManager.getConnection("jdbc:clickhouse://clickhouse:8123/cdc_demo")) {
                // Direct JSON insert without prepared statement
                String sql = "INSERT INTO enriched_orders FORMAT JSONEachRow " + value;
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                    System.out.println("Inserted into ClickHouse: " + value);
                } catch (Exception e) {
                    System.err.println("Failed to insert: " + value);
                    System.err.println("Error: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }
}