package com.jq.streamingMoitor.rulecompute.launch


import com.jq.streamingMoitor.common.bean.ProcessedData
import com.jq.streamingMoitor.common.util.hdfs.{BlackListToHDFS, BlackListToRedis}
import com.jq.streamingMoitor.common.util.jedis.{JedisConnectionPool, JedisConnectionUtil, JedisPoolUtil, PropertiesUtil}
import com.jq.streamingMoitor.common.util.log4j.LoggerLevels
import com.jq.streamingMoitor.rulecompute.businessprocess.{AnalyzeRuleDB, AntiCalculateResult, BroadcastProcess, CoreRule, FlowScoreResult, QueryDataPackage, RuleUtil}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Row, SparkSession}
import org.apache.spark.streaming.dstream.{DStream, InputDStream}
import org.apache.spark.streaming.kafka010.{ConsumerStrategies, KafkaUtils, LocationStrategies}
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import redis.clients.jedis.JedisCluster

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object ComputeLauncher {

  def main(args: Array[String]): Unit = {

    //设置日志级别
    LoggerLevels.setStreamingLogLevels()
    //当应用被停止的时候，进行如下设置可以保证当前批次执行完成之后再停止应用
    System.setProperty("spark.streaming.stopGracefullyOnShutdown", "true")

    //初始化
    val conf: SparkConf = new SparkConf().setAppName(this.getClass.getName).setMaster("local[2]")
    val spark: SparkSession = SparkSession.builder().config(conf).getOrCreate()
    val sc: SparkContext = spark.sparkContext

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

    val ssc = setupSsc(spark, sc, kafkaParams, topic)
    ssc.start()
    ssc.awaitTermination()

  }

  def setupSsc(spark: SparkSession, sc: SparkContext, kafkaParams: Map[String, Object], topic: Set[String]) = {

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

    //存储规则计算结果RDD（antiCalculateResults）到HDFS
    RuleUtil.lines2HDFS(sc, lines)

    val structuredDataLines: DStream[ProcessedData] = QueryDataPackage.queryDataLoadAndPackage(lines)

    lines.foreachRDD(rdd => {
      //跟新关键页面
      criticalPagesBroadcast = BroadcastProcess.monitorCriticalPagesRule(sc, criticalPagesBroadcast, jedis)
      //更新黑名单
      ipBlackListBroadcast = BroadcastProcess.monitorIpBlackListRule(sc, ipBlackListBroadcast, jedis)
      //
      flowListBroadcast = BroadcastProcess.monitiorFlowListRule(sc, flowListBroadcast, jedis)
    })
    //todo 1 5分钟内ip段(ip前两位)访问量
    val ipSectionCounts: DStream[(String, Int)] = CoreRule.ipBlackAccessCounts(structuredDataLines, Seconds(9), Seconds(3))

    var ipSectionCountsMap = scala.collection.Map[String, Int]()
    ipSectionCounts.foreachRDD(rdd => {
      ipSectionCountsMap = rdd.collectAsMap()
//      rdd.foreach( x => println("计算指标1 " + x))
    })
    //todo 2 5分钟内ip总访问量
    val ipCounts: DStream[(String, Int)] = CoreRule.ipAccessCounts(structuredDataLines, Seconds(9), Seconds(3))
    var ipCountsMap = scala.collection.Map[String, Int]()
    ipCounts.foreachRDD(rdd => {
      ipCountsMap = rdd.collectAsMap()
//      rdd.foreach(x => println("计算指标2 " + x))
    })
    //todo 3 某个ip，5五分钟内的关键页面的总访问
    val criticalPagesCounts: DStream[(String, Int)] = CoreRule.criticalPagesCounts(structuredDataLines, Seconds(9), Seconds(3), criticalPagesBroadcast.value)
    var criticalPagesCountsMap = scala.collection.Map[String, Int]()

    criticalPagesCounts.foreachRDD(rdd =>{
      criticalPagesCountsMap = rdd.collectAsMap()
//      rdd.foreach(x => println("计算指标3 " + x))
    })

    //todo 4 某个ip，5分钟内的UA种类数统计
    val userAgent: DStream[(String, Iterable[String])] = CoreRule.userAgent(structuredDataLines, Seconds(9), Seconds(3))
    var userAgentCountsMap = scala.collection.Map[String, Int]()
    userAgent.map(tup =>{
      val userAgent = tup._2
      val count: Int = RuleUtil.diffUserAgent(userAgent)
      (tup._1, count)
    }).foreachRDD(rdd => {
      userAgentCountsMap = rdd.collectAsMap()
//      rdd.foreach(x => println("计算指标4 " + x))
    })

    //todo 5 某个ip，5分钟内关键页面最短访问时间
    val criticalPagesAccTime: DStream[(String, Iterable[String])] = CoreRule.criticalPagesAccTime(structuredDataLines, Seconds(9), Seconds(3), criticalPagesBroadcast.value)
    var cirticalPagesTimeMap = scala.collection.Map[String, Int]()
    criticalPagesAccTime.map(tup => {

      val accTimes = tup._2
      //取出所有的时间
      val list = RuleUtil.allTimeList(accTimes)
      //取出最小时间间隔
      (tup._1, RuleUtil.minInterval(list))
    }).foreachRDD(rdd => {
      cirticalPagesTimeMap = rdd.collectAsMap()
//      rdd.foreach(x => println("计算指标5 " + x))
    })

    //todo 6 某个ip，5分钟内小于最短时间(自设)的关键页面查询次数
    val aCriticalPagesTime: DStream[((String, String), Iterable[String])] = CoreRule.aCriticalPagesTime(structuredDataLines, Seconds(9), Seconds(3), criticalPagesBroadcast.value)
    var aCriticalPagesTimeMap = scala.collection.Map[(String, String), Int]()
    aCriticalPagesTime.map(tup => {
      val accTime = tup._2
      val count = RuleUtil.calculateIntervals(accTime)
      (tup._1, count)
    }).foreachRDD(rdd => {
      aCriticalPagesTimeMap = rdd.collectAsMap()
//      rdd.foreach(x => println("计算指标6 " + x))
    })

    //todo 7 某个ip，5分钟内查询不同的行程次数
    val flightQuery: DStream[(String, Iterable[(String, String)])] = CoreRule.flightQuery(structuredDataLines, Seconds(9), Seconds(3))
    var flightQueryMap = scala.collection.Map[String, Int]()
    flightQuery.map(rdd => {
      val query = rdd._2
      val count: Int = RuleUtil.calculateDifferentTripQuerys(query)
      (rdd._1, count)
    }).foreachRDD(rdd => {
      flightQueryMap = rdd.collectAsMap()
//      rdd.foreach(x => println("计算指标7 " + x))
    })

    //todo 8 某个ip，5分钟内访问关键页面次数的cookie数
    val criticalCookie: DStream[(String, Iterable[String])] = CoreRule.criticalCoolie(structuredDataLines, Seconds(9), Seconds(3), criticalPagesBroadcast.value)
    var criticalCookieMap = scala.collection.Map[String, Int]()
    criticalCookie.map(tup => {
      val count: Int = RuleUtil.cookieCounts(tup._2)
      (tup._1, count)
    }).foreachRDD(rdd => {
      criticalCookieMap = rdd.collectAsMap()
//      rdd.foreach(x => println("计算指标8 " + x))
    })


    //todo 流程打分
    val antiCalculateResults: DStream[AntiCalculateResult] = structuredDataLines.map(processedData => {
      //获取ip和request，从而可以从上面的计算结果中得到这条记录对应5分钟内的总量，从而匹配数据库流程规则
      val ip = processedData.remoteAddr

      val request = processedData.request

      RuleUtil.calculateAntiResult(processedData, flowListBroadcast.value, ip, request,
        ipSectionCountsMap, ipCountsMap, criticalPagesCountsMap, userAgentCountsMap,
        cirticalPagesTimeMap, aCriticalPagesTimeMap, flightQueryMap, criticalCookieMap)
    })

//    antiCalculateResults.foreachRDD(antiCalculateResult => antiCalculateResult.foreach(
//      t => t.flowsScore.foreach(t => println(t.flowScore))
//    ))

    //剔除非黑名单数据(各流程打分结果均小于阈值， 我们只设置了一个流程，所以循环一次)
    val antiBlackResults: DStream[AntiCalculateResult] = antiCalculateResults.filter(antiCalculateResult => {
      val upLimitedFlows = antiCalculateResult.flowsScore.filter { flowScore =>
        //阈值判断结果，打分值大于阈值，为true
        flowScore.isUpLimited
      }
      //阈值判断结果，打分值大于阈值，为true
      upLimitedFlows.nonEmpty
    })

//    antiBlackResults.foreachRDD(antiBlackResult => {
//      antiBlackResult.foreach(x => println("黑名单" + x))
//    })

    val antiBlackResultsTup: DStream[(String, Array[FlowScoreResult])] = antiBlackResults.map(antiBlackResult => {
      //黑名单ip，黑名单打分
      (antiBlackResult.ip, antiBlackResult.flowsScore)
    })

    antiBlackResultsTup.foreachRDD(rdd => {
      //过滤重复的数据， (ip, 流程分数)
      val distinted: RDD[(String, Array[FlowScoreResult])] = rdd.reduceByKey((x, y) => x)
      //反爬虫黑名单数据
      val antiBlackList: Array[(String, Array[FlowScoreResult])] = distinted.collect()

      if (antiBlackList.nonEmpty) {
        //黑名单DataFrame-后期要被分到HDFS
        val antiBlackListDFR: ArrayBuffer[Row] = new ArrayBuffer[Row]()

        try{
          //创建redis链接
          val jedis: JedisCluster = JedisConnectionUtil.getJedisCluster
          //恢复redis黑名单数据，用于防止程序停止而产生的redis数据丢失
          BlackListToRedis.blackListDataToRedis(jedis, sc, spark)
          //将黑名单数据存储到redis
          //对各个流程的反爬虫数据进行处理Array[(ip, Array[FlowScoreResult])]
          antiBlackList.foreach( antiBlackRecord => {
            //拿到某个反爬虫数据的打分信息Array[flowScoreResult]
            antiBlackRecord._2.foreach( antiBlackRecordByFlow => {
              //Redis基于Key:field - value的方式保存黑名单
              //redis黑名单库中的键ip：flowId
              val blackListKey: String = PropertiesUtil.getStringByKey("cluster.key.anti_black_list", "jedisConfig.properties") + s"${antiBlackRecord._1}:${antiBlackRecordByFlow.flowId}"
              //redis黑名单库中的值：flowScore|strategyCode|hitRules|time
              val blackListValue: String = s"${antiBlackRecordByFlow.flowScore}|${antiBlackRecordByFlow.flowLimitedScore}|${antiBlackRecordByFlow.hitRules}|${antiBlackRecordByFlow.hitTime}"
              //数据的生命周期
              val expSeconds: String = PropertiesUtil.getStringByKey("cluster.exptime.anti_black_list", "jedisConfig.properties")
              //更新黑名单库，超时时间为3600秒
              jedis.setex(blackListKey, expSeconds.toInt, blackListValue)
              //添加黑名单DataFrame-备份到ArrayBuffer
              antiBlackListDFR.append(Row((expSeconds.toLong * 1000 + System.currentTimeMillis()).toString, blackListKey, blackListValue))
            })
          })

        } catch {
          case e : Exception => e.printStackTrace()
        }
        //增加黑名单数据实时存储到HDFS的功能-黑名单数据持久化-用于Redis数据恢复
        println("黑名单列表" + antiBlackListDFR)
        BlackListToHDFS.saveAntiBlackList(sc.parallelize(antiBlackListDFR), spark)
      }

    })


    ssc
  }

}

















