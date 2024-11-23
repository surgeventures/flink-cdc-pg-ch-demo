package com.example.cdc

import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.CheckpointingMode
import com.example.cdc.config.{AppConfig, DatabaseConfig}
import com.example.cdc.processing.JoinFunction
import com.example.cdc.sink.ClickHouseSink
import com.example.cdc.source.SourceFactory
import com.example.cdc.json.JsonOps
import org.slf4j.LoggerFactory

object Main {
  private val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    val config = AppConfig.load()

    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(config.parallelism)
    env.enableCheckpointing(
      config.checkpointInterval.toMillis,
      CheckpointingMode.EXACTLY_ONCE
    )

    val orderSource    = SourceFactory.createSource(DatabaseConfig.Orders)
    val customerSource = SourceFactory.createSource(DatabaseConfig.Customers)

    logger.info("Starting CDC Enrichment Job")

    env
      .addSource(orderSource)
      .connect(env.addSource(customerSource))
      .keyBy(
        JsonOps.extractCustomerId(_),
        JsonOps.extractCustomerId(_)
      )
      .process(new JoinFunction)
      .addSink(new ClickHouseSink(config))

    env.execute("CDC Enrichment Job")
  }
}
