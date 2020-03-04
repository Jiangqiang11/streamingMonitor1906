package com.jq.streamingMoitor.dataprocess.businessprocess

import com.jq.streamingMoitor.common.bean.AccessLog
import com.jq.streamingMoitor.common.util.jedis.PropertiesUtil
import org.apache.spark.rdd.RDD
import org.json4s.DefaultFormats
import org.json4s.jackson.Json
import redis.clients.jedis.JedisCluster


object BusinessProcess {
  def linkCont(value: RDD[AccessLog], jedis: JedisCluster) = {

    //统计链路数量
    val serverCount: RDD[(String, Int)] = value.map(acc => (acc.server_addr, 1)).reduceByKey(_ + _)

    //活跃连接数
    val activeNum: RDD[(String, Int)] = value.map(scc => (scc.server_addr, scc.connectionActive)).reduceByKey((x, y) => y)

    //将数据存入redis
    if ( !serverCount.isEmpty() && !activeNum.isEmpty()) {

      //将两个RDD调用action转换为数据
      val serverCountMap: collection.Map[String, Int] = serverCount.collectAsMap()
      val activeNumMap: collection.Map[String, Int] = activeNum.collectAsMap()

      val map = Map(
        "activeNumMap" -> activeNumMap,
        "serversCountMap" -> serverCountMap
      )

      try { //获取链路统计的key
        val keyName: String = PropertiesUtil
          .getStringByKey("cluster.key.monitor.linkProcess", "jedisConfig.properties") + System.currentTimeMillis()

        //设置链路统计的过期时间
        val expTime: String = PropertiesUtil.getStringByKey("cluster.exptime.monitor", "jedisConfig.properties")
        val value: String = Json(DefaultFormats).write(map)

        jedis.setex(keyName, expTime.toInt, value)
      } catch {
        case e: Exception =>
          e.printStackTrace()
          jedis.close()
      }
    }

    serverCount
  }
}
