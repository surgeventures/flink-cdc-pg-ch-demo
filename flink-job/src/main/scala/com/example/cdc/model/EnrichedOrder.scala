package com.example.cdc.model

case class EnrichedOrder(
  order: Order,
  customer: Customer
) {
  def toJson: Map[String, String] = Map(
    "order_id"       -> order.id,
    "customer_id"    -> customer.id,
    "customer_name"  -> customer.name,
    "customer_email" -> customer.email,
    "order_date"     -> order.orderDate,
    "total_amount"   -> order.totalAmount.toString,
    "status"         -> order.status,
    "created_at"     -> customer.createdAt
  )
}
