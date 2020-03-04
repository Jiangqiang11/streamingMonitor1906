package com.jq.streamingMoitor.dataprocess.businessprocess

import com.jq.streamingMoitor.common.bean.AnalyzeRule
import org.apache.spark.SparkContext
import org.apache.spark.broadcast.Broadcast
import redis.clients.jedis.JedisCluster

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer


object BroadCastProcess {
  def ipBlackListRule(
                       sc: SparkContext,
                       ipBlackListRef: Broadcast[ArrayBuffer[String]],
                       jedis: JedisCluster
                     ) = {
    val needUpdateIpList = jedis.get("IpBlackRuleChangeFlag")
    if ( !needUpdateIpList.isEmpty && needUpdateIpList.toBoolean) {
      //查询高频ip
      val ipList = AnalyzeRuleDB.queryIpBlackList()
      //删除广播变量
      ipBlackListRef.unpersist()
      //重新设置广播变量
      val ipBlackListBroadcast = sc.broadcast(ipList)
      jedis.set("", "false")

      ipBlackListBroadcast
    } else {
      ipBlackListRef
    }
  }


  /**
   * 实时监控更新解析规则
   * AnaluzeRuleChangeFlag
   *
   * @param sc
   * @param queryBooksBroadcast
   * @param jedis
   */
  def queryBooksRule(
                      sc: SparkContext,
                      queryBooksBroadcast: Broadcast[mutable.HashMap[String, List[AnalyzeRule]]],
                      jedis: JedisCluster) = {
    val needUpdateAnalyzeRule = jedis.get("AnaluzeRuleChangeFlag")
    if (!needUpdateAnalyzeRule.isEmpty && needUpdateAnalyzeRule.toBoolean) {
      //查询规则
      val queryRules = AnalyzeRuleDB.queryRule(0)
      //预定规则
      val bookRules: List[AnalyzeRule] = AnalyzeRuleDB.queryRule(1)

      //用于存储规则数据
      val querybooks: mutable.HashMap[String, List[AnalyzeRule]] = new mutable.HashMap[String, List[AnalyzeRule]]()
      querybooks.put("queryRules", queryRules)
      querybooks.put("bookRules", bookRules)

      queryBooksBroadcast.unpersist()

      jedis.set("AnaluzeRuleChangeFlag", "false")

      val value: Broadcast[mutable.HashMap[String, List[AnalyzeRule]]] = sc.broadcast(querybooks)
      value
    } else {
      queryBooksBroadcast
    }
  }


  /**
   * 更新分类规则
   *
   * @param sc
   * @param ruleMapBroadcast
   * @param jedis
   * @return
   */
  def classiferRule(
                     sc: SparkContext,
                     ruleMapBroadcast: Broadcast[mutable.HashMap[String, ArrayBuffer[String]]],
                     jedis: JedisCluster): Broadcast[mutable.HashMap[String, ArrayBuffer[String]]] = {

    val needUpdateClassifyRule: String = jedis.get("ClassifyRuleChangeFlag")
    if (!needUpdateClassifyRule.isEmpty && needUpdateClassifyRule.toBoolean) {

      val ruleMapTemp = AnalyzeRuleDB.queryRuleMap()

      //设置标识的值
      jedis.set("ClassifyRuleChangeFlag", "false")
      //删除广播变量
      ruleMapBroadcast.unpersist()
      //重新设置广播变量
      val value: Broadcast[mutable.HashMap[String, ArrayBuffer[String]]] = sc.broadcast(ruleMapTemp)
      value
    } else {
      ruleMapBroadcast
    }

  }


  /**
   * 更新过滤规则
   *
   * @param sc
   * @param filterRulerRef
   * @param jedis
   */
  def broadFilterRule(
                       sc: SparkContext,
                       filterRulerRef: Broadcast[ArrayBuffer[String]],
                       jedis: JedisCluster) = {
    //查询redis的标识
    val needUpdateFileList = jedis.get("FilterChangeFlag")

    //判断标识是否为空，或者是否发生改变
    if (needUpdateFileList != null && ! needUpdateFileList.isEmpty && needUpdateFileList.toBoolean) {
      //查询数据库过滤规则
      val filterRuleListUpdate: ArrayBuffer[String] = AnalyzeRuleDB.queryFilterRule()
      //删除广播变量的值
      filterRulerRef.unpersist()
      //重新广播
      val value: Broadcast[ArrayBuffer[String]] = sc.broadcast(filterRuleListUpdate)
      //重新设置标识
      jedis.set("FilterChangeFlag", "false")
      value
    } else {
      //如果没有更新，将之前广播的值返回
      filterRulerRef
    }
  }

}
