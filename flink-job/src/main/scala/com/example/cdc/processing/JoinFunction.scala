package com.example.cdc.processing

import org.apache.flink.streaming.api.functions.co.KeyedCoProcessFunction
import org.apache.flink.util.Collector
import org.apache.flink.configuration.Configuration
import com.example.cdc.json.JsonOps
import com.example.cdc.state.StateManager
import com.example.cdc.model.EnrichedOrder
import org.slf4j.LoggerFactory

class JoinFunction extends KeyedCoProcessFunction[String, String, String, String] {
  private lazy val logger         = LoggerFactory.getLogger(getClass)
  private var state: StateManager = _

  override def open(config: Configuration): Unit = {
    state = new StateManager(getRuntimeContext)
    state.setup()
    logger.info("JoinFunction initialized")
  }

  override def processElement1(
    orderJson: String,
    ctx: KeyedCoProcessFunction[String, String, String, String]#Context,
    out: Collector[String]
  ): Unit = {
    logger.info(s"Processing order: $orderJson")

    val maybeEnriched = for {
      node     <- JsonOps.parse(orderJson)
      order    <- JsonOps.toOrder(node)
      customer <- state.getCustomer(order.customerId)
    } yield EnrichedOrder(order, customer)

    maybeEnriched match {
      case Some(enriched) =>
        val json = JsonOps.enrichedOrderToJson(enriched)
        logger.info(s"Emitting enriched order: $json")
        out.collect(json)
      case None =>
        JsonOps
          .parse(orderJson)
          .flatMap(JsonOps.toOrder)
          .foreach { order =>
            state.bufferOrder(order)
            logger.info(s"Buffered order ${order.id} for customer ${order.customerId}")
          }
    }
  }

  override def processElement2(
    customerJson: String,
    ctx: KeyedCoProcessFunction[String, String, String, String]#Context,
    out: Collector[String]
  ): Unit = {
    logger.info(s"Processing customer: $customerJson")

    for {
      node     <- JsonOps.parse(customerJson)
      customer <- JsonOps.toCustomer(node)
    } {
      state.addCustomer(customer)
      logger.info(s"Added customer ${customer.id}")

      state.processPendingOrders(customer.id) { order =>
        val enriched = EnrichedOrder(order, customer)
        val json     = JsonOps.enrichedOrderToJson(enriched)
        logger.info(s"Processing pending order: $json")
        out.collect(json)
      }
    }
  }
}
