package com.jq.streamingMoitor.rulecompute.businessprocess

import java.sql.{Connection, PreparedStatement, ResultSet}

import com.jq.streamingMoitor.common.bean.{FlowCollocation, RuleCollocation}
import com.jq.streamingMoitor.common.util.database.{C3p0Util, QueryDB}

import scala.collection.mutable.{ArrayBuffer, ListBuffer}

object AnalyzeRuleDB {




  /**
   * 黑名单
   */
  def queryIpblackList() = {
    val sql = "select ip_name from nh_ip_blacklist"
    val field = "ip_name"
    QueryDB.queryData(sql, field)
  }

  /**
   * 关键页面
   */
  def queryCriticalPages() = {
    val sql = "select criticalPageMatchExpression from nh_query_critical_pages "
    val field = "criticalPageMatchExpression"
    QueryDB.queryData(sql, field)
  }

  /**
   * 流程规则
   * 注意: 流程是动态变化的，而且是运行时只能有一个流程起作用，多个流程会逻辑混乱
   * 根据页面内容，流程分实时、准实时、离线，我们现在仅实现实时
   *
   * @param n 0代表开启，1代表关闭
   */
  def createFlow(n: Int) = {

    var array = new ArrayBuffer[FlowCollocation]

    val sql =
      if ( n == 0) {
        """
          |select
          |nh_process_info.id, nh_process_info.process_name, nh_strategy.crawler_blacklist_thresholds
          |from nh_process_info, nh_strategy
          |where
          |nh_process_info.id = nh_strategy.id and nh_process_info.status = 0
          |""".stripMargin
      } else if ( n == 1) {
        """
          |select
          |nh_process_info.id, nh_process_info.process_name, nh_strategy.crawler_blacklist_thresholds
          |from nh_process_info, nh_strategy
          |where
          |nh_process_info.id = nh_strategy.id and nh_process_info.status = 1
          |""".stripMargin
      }
    var conn:Connection = null
    var ps : PreparedStatement = null
    var rs : ResultSet = null
    try{

      conn = C3p0Util.getConnection
      ps = conn.prepareStatement(sql.toString)
      rs = ps.executeQuery()

      while (rs.next()) {

        val flowId = rs.getString("id")
        val flowName = rs.getString("process_name")
        if(n == 0){

          val flowLimitScore = rs.getDouble("crawler_blacklist_thresholds")
          array += FlowCollocation(flowId, flowName,createRuleList(flowId,n), flowLimitScore, flowId)
        }else if(n == 1){

          val flowLimitScore = rs.getDouble("occ_blacklist_thresholds")
          array += new FlowCollocation(flowId, flowName,createRuleList(flowId,n), flowLimitScore, flowId)
        }
      }
    }catch{
      case e : Exception => e.printStackTrace()
    }finally {
      C3p0Util.close(conn, ps, rs)
    }
    array

  }

  def createRuleList(flowId: String, n: Int): List[RuleCollocation] = {
    //用于存放规则配置信息
    var list = new ListBuffer[RuleCollocation]

    val sql =
      s"""
        |select
        |*
        |from
        |(
        |select
        |nh_rule.id,nh_rule.process_id, nh_rule.rule_type, nh_rule.status, nh_rule.crawler_type,
        |nh_rule.arg0, nh_rule.arg1, nh_rule.score,
        |nh_rules_maintenance_table.rule_real_name
        |from
        |nh_rule, nh_rules_maintenance_table
        |where
        |nh_rules_maintenance_table.rule_name = nh_rule.rule_name
        |) as t
        |where
        |process_id = '$flowId' and crawler_type = '$n'
        |""".stripMargin

    var conn: Connection = null
    var ps: PreparedStatement = null
    var rs:ResultSet = null

    try{
      conn = C3p0Util.getConnection
      ps = conn.prepareStatement(sql)
      rs = ps.executeQuery()
      while ( rs.next() ) {
        val ruleId = rs.getString("id")
        val flowId = rs.getString("process_id")
        val ruleName = rs.getString("rule_real_name")
        val ruleType = rs.getString("rule_type")
        val ruleStatus = rs.getInt("status")
        val ruleCrawlerType = rs.getInt("crawler_type")
        val ruleValue0 = rs.getDouble("arg0")
        val ruleValue1 = rs.getDouble("arg1")
        val ruleScore = rs.getInt("score")
        val ruleCollocation = new RuleCollocation(ruleId,flowId,ruleName,ruleType,ruleStatus,ruleCrawlerType,ruleValue0,ruleValue1,ruleScore)

        list += ruleCollocation
      }
    }catch {
      case e : Exception => e.printStackTrace()
    }finally {
      C3p0Util.close(conn, ps, rs)
    }
    list.toList
  }

}
