package com.jq.streamingMoitor.dataprocess.launch

import com.jq.streamingMoitor.common.bean.{AccessLog, AnalyzeRule, BookRequestData, ProcessedData, QueryRequestData, RequestType}
import com.jq.streamingMoitor.common.util.jedis.{JedisConnectionUtil, PropertiesUtil}
import com.jq.streamingMoitor.dataprocess.businessprocess._
import com.jq.streamingMoitor.dataprocess.constants.TravelTypeEnum
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.dstream.InputDStream
import org.apache.spark.streaming.kafka010.{ConsumerStrategies, KafkaUtils, LocationStrategies}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}
import redis.clients.jedis.JedisCluster

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object DataProcessLauncher {
  def main(args: Array[String]): Unit = {
    //当应用被停止的时候，进行如下设置可以保证当前批次执行完之后再停止应用。
    System.setProperty("spark.streaming.stopGracefullyOnShutdown", "true")

    val conf: SparkConf = new SparkConf().setAppName("DataProcessLauncher").setMaster("local[2]")
    val sc: SparkContext = new SparkContext(conf)

    //请求kafka的参数
    //consumer grouping
    val groupid = "group01"

    //kafka params
    val kafkas = PropertiesUtil.getStringByKey("default.brokers", "kafkaConfig.properties")


    val kafkaParams = Map[String, Object](
      //指定消费kafka的ip和端口
      "bootstrap.servers"-> kafkas,
      "group.id"->groupid,
      "key.deserializer"->classOf[StringDeserializer],
      "value.deserializer" -> classOf[StringDeserializer],
      "auto.offset.reset" -> "earliest",
      "enable.auto.commit" -> (false: java.lang.Boolean)
    )


    val topic = PropertiesUtil.getStringByKey("source.nginx.topic", "kafkaConfig.properties")

    val topics = Set(topic)

    val scc = setupScc(sc, kafkaParams, topics)


    scc.start()
    scc.awaitTermination()
  }

  def setupScc(sc: SparkContext, kafkaParams: Map[String, Object], topics: Set[String]) = {
    val scc: StreamingContext = new StreamingContext(sc, Seconds(3))

    val stream: InputDStream[ConsumerRecord[String, String]] = KafkaUtils.createDirectStream(
      scc,
      LocationStrategies.PreferConsistent,
      ConsumerStrategies.Subscribe[String, String](topics, kafkaParams)
    )

/*    stream.map(_.value()).foreachRDD(rdd =>{
      rdd.foreach(println)
    })*/
    //获取数据库的过滤规则，然后同步到广播变量
    val filterRuleList: ArrayBuffer[String] = AnalyzeRuleDB.queryFilterRule()
    //获取数据库的分类规则
    val ruleMapTemp: mutable.HashMap[String, ArrayBuffer[String]] = AnalyzeRuleDB.queryRuleMap()
    //获取查询规则
    val queryRules: List[AnalyzeRule] = AnalyzeRuleDB.queryRule(0)
    //获取预定规则
    val bookRules: List[AnalyzeRule] = AnalyzeRuleDB.queryRule(1)
    val queryBooks: mutable.HashMap[String, List[AnalyzeRule]] = new mutable.HashMap[String, List[AnalyzeRule]]()
    queryBooks.put("queryRules", queryRules)
    queryBooks.put("bookRules", bookRules)
    //查询高频ip列表
    val ipBlackList: ArrayBuffer[String] = AnalyzeRuleDB.queryIpBlackList()

    //广播过滤规则 为了供给多线程使用,优化代码，加入注解
    @volatile var filterRulerRef: Broadcast[ArrayBuffer[String]] = sc.broadcast(filterRuleList)
    //广播分类规则
    @volatile var ruleMapBroadcast = sc.broadcast(ruleMapTemp)
    //广播查询预定规则
    @volatile var queryBooksBroadcast: Broadcast[mutable.HashMap[String, List[AnalyzeRule]]] = sc.broadcast(queryBooks)
    //广播高频ip
    var ipBlackListRef: Broadcast[ArrayBuffer[String]] = sc.broadcast(ipBlackList)

    val jedis: JedisCluster = JedisConnectionUtil.getJedisCluster

    //数据处理
    stream.map(_.value()).foreachRDD(rdd => {
      //开启缓存
      rdd.cache()
      //监控过滤规则是否发生变化，如果改变，则更新广播变量
      filterRulerRef = BroadCastProcess.broadFilterRule(sc, filterRulerRef, jedis)
      //监控分类规则是否发生变化，如果改变，则更新广播变量
      ruleMapBroadcast = BroadCastProcess.classiferRule(sc, ruleMapBroadcast, jedis)
      //监控查询预定规则
      queryBooksBroadcast = BroadCastProcess.queryBooksRule(sc, queryBooksBroadcast, jedis)
      //监控高频ip列表
      ipBlackListRef = BroadCastProcess.ipBlackListRule(sc, ipBlackListRef, jedis)
      //对数据进行切分处理

      val value: RDD[AccessLog] = DataSplit.parseAccessLog(rdd)
      //链路统计
      val serverCount: RDD[(String, Int)] = BusinessProcess.linkCont(value, jedis)
      //为了做监控统计，需要调用action拿到链路的流量
      val serverCountMap: collection.Map[String, Int] = serverCount.collectAsMap()
      //实时监控
      SparkStreamingMonitor.streamMonitor(sc, rdd, serverCountMap, jedis)
      //数据清洗
      val filteredRDD: RDD[AccessLog] = value.filter(log => UrlFilter.filterUrl(log, filterRulerRef.value))

      //数据脱敏
      val dataProcess: RDD[ProcessedData] = filteredRDD.map(log => {
        //手机号脱敏
        log.http_cookie = EncryedData.encryptedPhone(log.http_cookie)
        //身份证号脱敏
        log.http_cookie = EncryedData.encryptedId(log.http_cookie)

        //分类打标签：国内查询、国际查询、国内预定、国际预定
        val requestTypeLable: RequestType = RequstTypeClassifer.classifyByRequest(
          log.request, ruleMapBroadcast.value
        )
        //返程往返
        val travelType: TravelTypeEnum.Value = TravelTypeClassifer.classiferByRefererAndRequestbody(log.http_referer)

        //数据解析
        //查询数据的解析
        val queryRequestDate: Option[QueryRequestData] = AnalyzeRequest.analyzeQueryRequest(
          requestTypeLable,
          log.request_method,
          log.content_type,
          log.request,
          log.request_body,
          travelType,
          queryBooksBroadcast.value("queryRules")
        )
        //预定数据的解析
        val bookRequestData: Option[BookRequestData] = AnalyzeBookRequest.analyzeBookRequest(
          requestTypeLable,
          log.request_method,
          log.content_type,
          log.request,
          log.request_body,
          travelType,
          queryBooksBroadcast.value("bookRules")
        )
        val highFrqIpGroup: Boolean = IpOperation.isFrequencyIp(log.remote_addr, ipBlackListRef.value)

        //数据结构化
        DataPackage.dataPackage(
          "", log, requestTypeLable, travelType, queryRequestDate,
          bookRequestData, highFrqIpGroup
        )
      })
//      val datas: Array[ProcessedData] = dataProcess.collect()

      //数据推送到kafka
      //查询行为数据
      DataSend.sendQueryData2Kafka(dataProcess)
      //预定行为数据
      DataSend.sendBookData2Kafka(dataProcess)

    })


    scc
  }

}
