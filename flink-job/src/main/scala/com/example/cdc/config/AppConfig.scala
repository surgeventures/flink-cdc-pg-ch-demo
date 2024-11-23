package com.example.cdc.config

import com.typesafe.config.{Config, ConfigFactory}
import scala.concurrent.duration._

case class AppConfig(
  parallelism: Int,
  checkpointInterval: FiniteDuration,
  clickhouse: ClickhouseConfig
)

case class ClickhouseConfig(
  host: String,
  port: Int,
  database: String,
  table: String
)

object AppConfig {
  def load(): AppConfig = {
    val config = ConfigFactory.load()

    AppConfig(
      parallelism = config.getInt("app.parallelism"),
      checkpointInterval = config.getDuration("app.checkpoint-interval").toMillis.millis,
      clickhouse = loadClickhouseConfig(config)
    )
  }

  private def loadClickhouseConfig(config: Config): ClickhouseConfig =
    ClickhouseConfig(
      host = config.getString("clickhouse.host"),
      port = config.getInt("clickhouse.port"),
      database = config.getString("clickhouse.database"),
      table = config.getString("clickhouse.table")
    )
}
