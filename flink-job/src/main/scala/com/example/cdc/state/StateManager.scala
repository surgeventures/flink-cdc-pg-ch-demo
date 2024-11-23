package com.example.cdc.state

import org.apache.flink.api.common.state.{MapState, ListState, MapStateDescriptor, ListStateDescriptor}
import org.apache.flink.api.common.functions.RuntimeContext
import com.example.cdc.model.{Customer, Order}
import scala.collection.JavaConverters._

class StateManager(runtimeContext: RuntimeContext) {
  private var customerState: MapState[String, Customer] = _
  private var pendingOrders: ListState[Order]           = _

  def setup(): Unit = {
    customerState = runtimeContext.getMapState(
      new MapStateDescriptor[String, Customer](
        "customers",
        classOf[String],
        classOf[Customer]
      )
    )

    pendingOrders = runtimeContext.getListState(
      new ListStateDescriptor[Order](
        "pending-orders",
        classOf[Order]
      )
    )
  }

  def addCustomer(customer: Customer): Unit =
    customerState.put(customer.id, customer)

  def getCustomer(id: String): Option[Customer] =
    Option(customerState.get(id))

  def bufferOrder(order: Order): Unit =
    pendingOrders.add(order)

  def processPendingOrders(customerId: String)(process: Order => Unit): Unit = {
    val (matching, remaining) = pendingOrders.get.asScala
      .partition(_.customerId == customerId)

    matching.foreach(process)
    val javaList = remaining.toBuffer.asJava // Convert to Java List
    pendingOrders.update(javaList)
  }
}
