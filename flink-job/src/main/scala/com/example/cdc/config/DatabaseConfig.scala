package com.example.cdc.config

case class DatabaseConfig(
  hostname: String,
  port: Int = 5432,
  database: String,
  schema: String = "public",
  username: String = "user",
  password: String = "password",
  serverName: String
) {
  def tableName: String = database.split("_").head
}

object DatabaseConfig {
  val Orders = DatabaseConfig(
    hostname = "postgres1",
    database = "orders_db",
    serverName = "orders_server"
  )

  val Customers = DatabaseConfig(
    hostname = "postgres2",
    database = "customers_db",
    serverName = "customers_server"
  )
}
