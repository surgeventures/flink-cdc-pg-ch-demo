package com.example.cdc.sink

import org.apache.flink.streaming.api.functions.sink.SinkFunction
import com.example.cdc.config.AppConfig
import scala.util.Try
import java.sql.{Connection, DriverManager, Statement}
import org.slf4j.LoggerFactory

class ClickHouseSink(config: AppConfig) extends SinkFunction[String] {
  private val logger = LoggerFactory.getLogger(getClass)

  override def invoke(value: String, context: SinkFunction.Context): Unit = {
    val url = s"jdbc:clickhouse://${config.clickhouse.host}:${config.clickhouse.port}/${config.clickhouse.database}"

    logger.info(s"Attempting to insert value: $value")
    logger.info(s"Using URL: $url")

    var conn: Connection = null
    var stmt: Statement  = null

    try {
      conn = DriverManager.getConnection(url)
      stmt = conn.createStatement()
      val sql = s"INSERT INTO ${config.clickhouse.table} FORMAT JSONEachRow $value"
      logger.info(s"Executing SQL: $sql")

      stmt.execute(sql)
      logger.info("Successfully inserted data")
    } catch {
      case e: Exception =>
        logger.error(s"Failed to insert data: ${e.getMessage}")
        logger.error(s"Full error", e)
        throw e
    } finally {
      if (stmt != null) Try(stmt.close())
      if (conn != null) Try(conn.close())
    }
  }
}
