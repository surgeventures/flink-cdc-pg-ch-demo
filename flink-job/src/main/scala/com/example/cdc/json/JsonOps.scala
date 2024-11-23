package com.example.cdc.json

import com.example.cdc.model.{Customer, Order, EnrichedOrder}
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import scala.util.{Try, Success, Failure}
import scala.collection.JavaConverters._
import org.slf4j.LoggerFactory

object JsonOps {
  private val mapper      = new ObjectMapper
  private lazy val logger = LoggerFactory.getLogger(getClass)

  def parse(json: String): Option[JsonNode] =
    Try(mapper.readTree(json)) match {
      case Success(node) => Option(node.get("after"))
      case Failure(e) =>
        logger.error(s"Error parsing JSON: $json", e)
        None
    }

  def extractCustomerId(json: String): String =
    parse(json)
      .flatMap(node => Option(node.get("customer_id")))
      .map(_.asText)
      .getOrElse("")

  def toOrder(node: JsonNode): Option[Order] =
    Try {
      Order(
        id = node.get("order_id").asText,
        customerId = node.get("customer_id").asText,
        orderDate = node.get("order_date").asText,
        totalAmount = BigDecimal(node.get("total_amount").asText()),
        status = node.get("status").asText
      )
    } match {
      case Success(order) => Some(order)
      case Failure(e) =>
        logger.error(s"Error converting JsonNode to Order: $node", e)
        None
    }

  def toCustomer(node: JsonNode): Option[Customer] =
    Try {
      Customer(
        id = node.get("customer_id").asText,
        name = node.get("name").asText,
        email = node.get("email").asText,
        createdAt = node.get("created_at").asText
      )
    } match {
      case Success(customer) => Some(customer)
      case Failure(e) =>
        logger.error(s"Error converting JsonNode to Customer: $node", e)
        None
    }

  def enrichedOrderToJson(enriched: EnrichedOrder): String =
    mapper.writeValueAsString(enriched.toJson.asJava)
}
