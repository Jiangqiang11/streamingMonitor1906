package com.jq.streamingMoitor.rulecompute.businessprocess

import com.jq.streamingMoitor.common.bean.FlowCollocation
import org.apache.spark.SparkContext
import org.apache.spark.broadcast.Broadcast
import redis.clients.jedis.{Jedis, JedisCluster}

import scala.collection.mutable.ArrayBuffer

object BroadcastProcess {


  /**
   * 更新关键页面
   *
   * @param sc
   * @param criticalPagesBroadcast
   * @param jedis
   * @return
   */
  def monitorCriticalPagesRule(
                                sc: SparkContext,
                                criticalPagesBroadcast: Broadcast[ArrayBuffer[String]],
                                jedis: JedisCluster
                              ): Broadcast[ArrayBuffer[String]] = {
    val needUpdateCriticalPages = jedis.get("QueryCriticalPages")
    if ( !needUpdateCriticalPages.isEmpty &&
      needUpdateCriticalPages != null &&
      needUpdateCriticalPages.toBoolean) {
      val criticalPages = AnalyzeRuleDB.queryCriticalPages()
      criticalPagesBroadcast.unpersist()
      val value: Broadcast[ArrayBuffer[String]] = sc.broadcast(criticalPages)
      jedis.set("QueryCriticalPages", "false")
      value
    } else {
      criticalPagesBroadcast
    }

  }

  /**
   * 更新黑名单
   * @param sc
   * @param ipBlackListBroadcast
   * @param jedis
   * @return
   */
  def monitorIpBlackListRule(
                              sc: SparkContext,
                              ipBlackListBroadcast: Broadcast[ArrayBuffer[String]],
                              jedis: JedisCluster
                            ): Broadcast[ArrayBuffer[String]] = {
    val needUpdateIpBlackList = jedis.get("IpBlackRuleChangeFlag")
    if ( !needUpdateIpBlackList.isEmpty &&
      needUpdateIpBlackList != null &&
      needUpdateIpBlackList.toBoolean) {
      val ipBlackList = AnalyzeRuleDB.queryIpblackList()
      ipBlackListBroadcast.unpersist()
      val value: Broadcast[ArrayBuffer[String]] = sc.broadcast(ipBlackList)
      jedis.set("IpBlackRuleChangeFlag", "false")
      value
    } else {
      ipBlackListBroadcast
    }
  }

  /**
   * 更新流程规则
   * @param sc
   * @param flowListBroadcast
   * @param jedis
   * @return
   */
  def monitiorFlowListRule(
                            sc: SparkContext,
                            flowListBroadcast: Broadcast[ArrayBuffer[FlowCollocation]],
                            jedis: JedisCluster
                          ): Broadcast[ArrayBuffer[FlowCollocation]] = {
    val needUpdateFlowList = jedis.get("QueryFlowChangeFlag")
    if ( !needUpdateFlowList.isEmpty &&
      needUpdateFlowList != null &&
      needUpdateFlowList.toBoolean) {
      val flowList = AnalyzeRuleDB.createFlow(0)
      flowListBroadcast.unpersist()
      val value: Broadcast[ArrayBuffer[FlowCollocation]] = sc.broadcast(flowList)
      jedis.set("QueryFlowChangeFlag", "false")
      value
    } else {
      flowListBroadcast
    }
  }

}
