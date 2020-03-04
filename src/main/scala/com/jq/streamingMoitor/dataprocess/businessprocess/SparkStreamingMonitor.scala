package com.jq.streamingMoitor.dataprocess.businessprocess

import java.text.SimpleDateFormat
import java.util.Date
import com.alibaba.fastjson.JSONObject
import com.jq.streamingMoitor.common.util.jedis.PropertiesUtil
import com.jq.streamingMoitor.common.util.spark.SparkMetricsUtils
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.json4s.DefaultFormats
import org.json4s.jackson.Json
import redis.clients.jedis.JedisCluster

object SparkStreamingMonitor {

  /**
   * 实时监控
   * @param sc
   * @param rdd
   * @param serverCountMap
   * @param jedis
   */
  def streamMonitor(
                     sc: SparkContext,
                     rdd: RDD[String],
                     serverCountMap: collection.Map[String, Int],
                     jedis: JedisCluster
                   ) = {
    //获取appID
    val appid: String = sc.applicationId
    //appname
    val appName: String = sc.appName
    //指定当前app的4040路径
    val url = "http://localhost:4040/metrics/json/"
    //通过4040服务获取json数据
    val jsonObj: JSONObject = SparkMetricsUtils.getMetricsJson(url)
    //获取gauges
    val result: JSONObject = jsonObj.getJSONObject("gauges")

    //startTimePath
    val startTimePath = appid + ".driver." + appName + ".StreamingMetrics.streaming.lastCompletedBatch_processingStartTime"
    // startTime
    val startTime = result.getJSONObject(startTimePath)
    // 转换为long类型，便于 计算
    var processStartTime: Long = 0L
    // 判断是否为空
    if (startTime != null) {
      processStartTime = startTime.getLong("value")
    }

    // endTimePath
    val endTimePath = appid + ".driver." + appName + ".StreamingMetrics.streaming.lastCompletedBatch_processingEndTime"
    // endTime
    val endTime = result.getJSONObject(endTimePath)
    var processEndTime: Long = 0L
    // 判断是否为空
    if (endTime != null) {
      processEndTime = endTime.getLong("value")
    }
    //开始计算，计算行数
    val sourceCount: Long = rdd.count()
    //批次所用时间
    val batchTime =  processEndTime - processStartTime
    //平均计算速度
    val countBatchAvg = sourceCount.toDouble / batchTime.toDouble
    //将结束时间戳转换为时间类型
    val dateFormat: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    val dateEndtime: String = dateFormat.format(new Date(processEndTime))

    //将数据封装到map
    val fieldMap = Map(
      "endTime" -> dateEndtime,
      "applicationUniqueName" -> appName.toString,
      "applicationId" -> appid.toString,
      "sourceCount" -> sourceCount.toString,
      "costTime" -> batchTime.toString,
      "countPerMillis" -> countBatchAvg.toString,
      "serversCountMap" -> serverCountMap
    )

    //保存到redis
    try {
      //获取生命周期
      val monitorDataExpTime: Int = PropertiesUtil.getStringByKey("cluster.exptime.monitor", "jedisConfig.properties").toInt
      //需要不重复的key值
      val keyName = PropertiesUtil.getStringByKey("cluster.key.monitor.dataProcess","jedisConfig.properties") + System.currentTimeMillis()
      //获取当前批次最后一天数据的值，因为数据是不断累加的
      val keyNameLast = PropertiesUtil.getStringByKey("cluster.key.monitor.dataProcess", "jedisConfig.properties") + "_LAST"

      jedis.setex(keyName, monitorDataExpTime, Json(DefaultFormats).write(fieldMap))
      jedis.setex(keyNameLast, monitorDataExpTime, Json(DefaultFormats).write(fieldMap))

    } catch {
      case e:Exception => e.printStackTrace()
    }
  }


}
