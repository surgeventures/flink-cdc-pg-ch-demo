CREATE DATABASE IF NOT EXISTS cdc_demo;

CREATE TABLE IF NOT EXISTS cdc_demo.enriched_orders (
    order_id UInt32,
    customer_id UInt32,
    customer_name String,
    customer_email String,
    order_date DateTime64(6),
    total_amount Decimal(10,2),
    status String,
    created_at DateTime64(6)
) ENGINE = MergeTree()
ORDER BY (order_date, order_id);
