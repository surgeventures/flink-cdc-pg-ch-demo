package com.example.cdc.model

case class Order(
  id: String,
  customerId: String,
  orderDate: String,
  totalAmount: BigDecimal,
  status: String
)
