package com.jq.streamingMoitor.rulecompute.launch

import java.io

import com.jq.streamingMoitor.common.bean.ProcessedData
import com.jq.streamingMoitor.common.util.jedis.{JedisConnectionPool, JedisConnectionUtil, JedisPoolUtil, PropertiesUtil}
import com.jq.streamingMoitor.common.util.log4j.LoggerLevels
import com.jq.streamingMoitor.rulecompute.businessprocess.{AnalyzeRuleDB, BroadcastProcess, CoreRule, QueryDataPackage}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.streaming.dstream.{DStream, InputDStream}
import org.apache.spark.streaming.kafka010.{ConsumerStrategies, KafkaUtils, LocationStrategies}
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.streaming.{Seconds, StreamingContext}

import scala.collection.mutable

object ComputeLauncher {



  def main(args: Array[String]): Unit = {

    //设置日志级别
    LoggerLevels.setStreamingLogLevels()
    //当应用被停止的时候，进行如下设置可以保证当前批次执行完成之后再停止应用
    System.setProperty("spark.streaming.stopGracefullyOnShutdown", "true")

    //初始化
    val conf: SparkConf = new SparkConf().setAppName(this.getClass.getName).setMaster("local[2]")
    val sc: SparkContext = new SparkContext(conf)

    //consumer group
    val groupId = "group1906"
    //kafka params
    val kafkaParams = Map[String, Object](
      "group.id" -> groupId,
      "bootstrap.servers" -> PropertiesUtil.getStringByKey("default.brokers", "kafkaConfig.properties"),
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[StringDeserializer],
      "auto.offset.reset" -> "earliest"
    )

    //topic
    val topic = Set(PropertiesUtil.getStringByKey("source.query.topic", "kafkaConfig.properties"))

    val ssc = setupSsc(sc, kafkaParams, topic)
    ssc.start()
    ssc.awaitTermination()

  }

  def setupSsc(sc: SparkContext, kafkaParams: Map[String, Object], topic: Set[String]) = {
    val ssc: StreamingContext = new StreamingContext(sc, Seconds(3))

    val stream: InputDStream[ConsumerRecord[String, String]] = KafkaUtils.createDirectStream(
      ssc,
      LocationStrategies.PreferConsistent,
      ConsumerStrategies.Subscribe[String, String](topic, kafkaParams)
    )

    //查询关键页面病广播
    val criticalPages = AnalyzeRuleDB.queryCriticalPages()
    //广播变量
    @volatile var criticalPagesBroadcast = sc.broadcast(criticalPages)
    //查询黑名单并广播
    val ipblackList = AnalyzeRuleDB.queryIpblackList()
    @volatile var ipBlackListBroadcast = sc.broadcast(ipblackList)
    //查询流程规则并广播
    val flowList = AnalyzeRuleDB.createFlow(0)
    @volatile var flowListBroadcast = sc.broadcast(flowList)

    val jedis = JedisConnectionUtil.getJedisCluster

    val lines: DStream[String] = stream.map(_.value())

    val structuredDataLines: DStream[ProcessedData] = QueryDataPackage.queryDataLoadAndPackage(lines)

    lines.foreachRDD(rdd => {
      //跟新关键页面
      criticalPagesBroadcast = BroadcastProcess.monitorCriticalPagesRule(sc, criticalPagesBroadcast, jedis)
      //更新黑名单
      ipBlackListBroadcast = BroadcastProcess.monitorIpBlackListRule(sc, ipBlackListBroadcast, jedis)
      //
      flowListBroadcast = BroadcastProcess.monitiorFlowListRule(sc, flowListBroadcast, jedis)
    })
    //todo 5分钟内ip段(ip前两位)访问量
    val ipSectionCounts: DStream[(String, Int)] = CoreRule.ipBlackAccessCounts(structuredDataLines, Seconds(9), Seconds(3))

    var ipSectionCountsMap = scala.collection.Map[String, Int]()
    ipSectionCounts.foreachRDD(rdd => {
      ipSectionCountsMap = rdd.collectAsMap()
      rdd.foreach(println)
    })

    val ipCounts: DStream[(String, Int)] = CoreRule.ipAccessCounts(structuredDataLines, Seconds(9), Seconds(3))
    var ipCountsMap = scala.collection.Map[String, Int]()
    ipCounts.foreachRDD(rdd => {
      ipCountsMap = rdd.collectAsMap()
      rdd.foreach(println)
    })




    ssc
  }

}

















