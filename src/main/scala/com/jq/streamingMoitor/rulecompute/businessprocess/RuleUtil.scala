package com.jq.streamingMoitor.rulecompute.businessprocess

import java.text.SimpleDateFormat
import java.util

import com.jq.streamingMoitor.common.bean.{FlowCollocation, ProcessedData}
import com.jq.streamingMoitor.common.util.jedis.PropertiesUtil
import org.apache.spark.SparkContext
import org.apache.spark.streaming.dstream.DStream
import org.joda.time.DateTime

import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.scalajs.js.Date

object RuleUtil {


  /**
   * 统计UA
   *
   * @param ua
   * @return
   */
  def diffUserAgent(ua: Iterable[String]): Int = {
    val set = mutable.Set[String]()
    for (agent <- ua) {
      set.add(agent)
    }
    set.size
    // ua.toSet.size
  }

  /**
   * 时间转换
   *
   * @param accTimes
   * @return
   */
  def allTimeList(accTimes: Iterable[String]) = {
    val timeList = new util.ArrayList[Long]()
    val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    for (time <- accTimes) {
      if (!"".equalsIgnoreCase(time)) {
        val timeStr = new DateTime(time).toDate
        val dt = sdf.format(timeStr)
        val timeL = sdf.parse(dt).getTime
        timeList.add(timeL)
      }
    }
    timeList
  }

  /**
   * 时间排序，求差
   *
   * @param list
   * @return
   */
  def allInterval(list: util.ArrayList[Long]) = {
    // 排序：因为获取的数据，不能保证顺序性
    val arr = list.toArray
    util.Arrays.sort(arr)
    // 创建list用于封装数据
    val intervalList = new util.ArrayList[Long]()
    // 计算时间差
    if (arr.length > 1) {
      for (i <- 1 until arr.length) {
        val time1 = arr(i - 1).toString.toLong
        val time2 = arr(i).toString.toLong
        val interval = time2 - time1
        intervalList.add(interval)
      }
    }
    intervalList
  }

  /**
   * 解析最小时间间隔
   *
   * @param list
   * @return
   */
  def minInterval(list: util.ArrayList[Long]) = {
    // 计算时间间隔
    val intervalLsit = allInterval(list)
    // 排序
    val result = intervalLsit.toArray()
    util.Arrays.sort(result)
    result(0).toString.toInt
  }

  /**
   * 某个IP，5分钟内小于最短访问间隔（自设）的关键页面查询次数
   *
   * @param accTime
   * @return
   */
  def calculateIntervals(accTime: Iterable[String]) = {
    // 预设时间间隔 10秒
    val defaultMinInterval = 10
    var count = 0
    val allTime = allTimeList(accTime)
    val interval = allInterval(allTime)
    if (interval != null && interval.size() > 0) {
      for (i <- 0 until interval.size()) {
        if (interval.get(i) < defaultMinInterval) {
          count = count + 1
        }
      }
    }
    count
  }

  /**
   * 统计一个Ip下查询不同行程的次数
   *
   * @param query
   * @return
   */
  def calculateDifferentTripQuerys(query: Iterable[(String, String)]): Int = {
    // 去重统计个数
    val set = scala.collection.mutable.Set[String]()
    for (a <- query) {
      set.add(a._1 + "—>" + a._2)
    }
    set.size
  }

  /**
   * 统计cookie数量
   *
   * @param cookie
   * @return
   */
  def cookieCounts(cookie: Iterable[String]): Int = {
    cookie.toSet.size
  }

  /**
   * 获取每条记录对应的结果
   *
   * @param processedData
   * @param value
   * @param ip
   * @param request
   * @param ipSectionCountsMap
   * @param ipCountsMap
   * @param criticalPagesCountsMap
   * @param userAgentCountsMap
   * @param criticalPagesTimeMap
   * @param aCiticalPagesTimeMap
   * @param flightQueryMap
   * @param criticalCookieMap
   * @return
   */
  def calculateAntiResult(
                           processedData: ProcessedData,
                           value: ArrayBuffer[FlowCollocation],
                           ip: String,
                           request: String,
                           ipSectionCountsMap: collection.Map[String, Int],
                           ipCountsMap: collection.Map[String, Int],
                           criticalPagesCountsMap: collection.Map[String, Int],
                           userAgentCountsMap: collection.Map[String, Int],
                           criticalPagesTimeMap: collection.Map[String, Int],
                           aCiticalPagesTimeMap: collection.Map[(String, String), Int],
                           flightQueryMap: collection.Map[String, Int],
                           criticalCookieMap: collection.Map[String, Int]
                         ) = {
    // 处理当前的ip字段，获取ip段
    val index = ip.split("\\.")
    val ipBlock =
      try {
        index(0) + "." + index(1)
      } catch {
        case e: Exception => ""
      }


    //ip段的访问量
    val ipBlockCounts: Int = ipSectionCountsMap.getOrElse(ipBlock, 0)
    //ip访问量
    val ipAccessCounts: Int = ipCountsMap.getOrElse(ip, 0)
    //关键页面访问量
    val criticalPageAccessCounts: Int = criticalPagesCountsMap.getOrElse(ip, 0)
    //UA种类数统计
    val userAgentCounts: Int = userAgentCountsMap.getOrElse(ip, 0)
    //关键页面最短访问间隔
    val critivalPageMinInterval: Int = criticalPagesTimeMap.getOrElse(ip, 0)
    //最短访问间隔的关键页面的查询次数
    val accessPageIntervalLessThanDefault: Int = aCiticalPagesTimeMap.getOrElse((ip, request), 0)
    //不同行程查询次数
    val differentTripQuerysCounts: Int = flightQueryMap.getOrElse(ip, 0)
    //关键页面的cookie数
    val criticalCookies: Int = criticalCookieMap.getOrElse(ip, 0)
    //这条记录对应的所有标签封装到map中

    val paramMap = scala.collection.mutable.Map[String, Int]()

    paramMap += ("ipBlock" -> ipBlockCounts)
    paramMap += ("ip" -> ipAccessCounts)
    paramMap += ("criticalPages" -> criticalPageAccessCounts)
    paramMap += ("userAgent" -> userAgentCounts)
    paramMap += ("criticalPagesAccTime" -> critivalPageMinInterval)
    paramMap += ("flightQuery" -> differentTripQuerysCounts)
    paramMap += ("criticalCookies" -> criticalCookies)
    paramMap += ("criticalPagesLessThanDefault" -> accessPageIntervalLessThanDefault)

    //计算打分
    val flowScore = calculateFlowScores(paramMap, value)

    //封装结果
    AntiCalculateResult(processedData, ip, ipBlockCounts, ipAccessCounts,
      criticalPageAccessCounts,userAgentCounts, critivalPageMinInterval,
      accessPageIntervalLessThanDefault,differentTripQuerysCounts, criticalCookies,flowScore)
  }

  /**
   * 规则打分
   *
   * @param paramMap
   * @param value
   */
  def calculateFlowScores(
                          paramMap: mutable.Map[String, Int],
                          value: ArrayBuffer[FlowCollocation]
                        ) = {
    //封装最终打分结果：flowId、flowScore、flowLimitedScore、是否超过阈值、flowStrategyCode、命中规则列表、命中时间
    val flowScores = new ArrayBuffer[FlowScoreResult]

    //循环数据库查询出来的所有流程，进行匹配打分
    for( flow <- value) {
      //获取当前流程规则
      val ruleList = flow.rules
      //用来封装命中的规则的ruleId
      val hitRules: ListBuffer[String] = ListBuffer[String]()
      //规则是否启用，
      val isTriggered: ArrayBuffer[Int] = new ArrayBuffer[Int]()

      //创建二维数据，需要进行和列
      //第一维：计算结果
      //第二维：数据库打分结果
      val result: Array[Array[Double]] = Array.ofDim[Double](2, ruleList.size)
      //设置规则下标的自增变量
      var ruleIndex = 0

      for (rule <- ruleList) {
        //讲规则保存到数组
        isTriggered += rule.ruleStatus
        //获取规则名称
        val ruleName: String = rule.ruleName
        //通过规则名称匹配map集合，获取对应的值
        val ruleValue: Int = paramMap.getOrElse(ruleName, 0)
        //将结果封装到二维数组中
        result(0)(ruleIndex) = ruleValue
        //获取数据库对应的这个规则的阈值
        val ruleValue1 = if("accessPageIntervalLessThanDefault".equals(ruleName)){
          rule.ruleValue1
        } else {
          rule.ruleValue0
        }
        //第二维
        val ruleScore: Int = rule.ruleScore
        if(ruleValue > ruleValue1) {
          //如果超过了数据库的阈值，将打分记录到二维数据组中
          result(1)(ruleIndex) = ruleScore
          //命中规则
          hitRules.append(rule.ruleId)
        } else {
          result(1)(ruleIndex) = 0
        }
        ruleIndex += 1
      }
      //计算流程打分
      val flowScore = calculateFlowScore(result, isTriggered.toArray)
      //返回结果
      flowScores.append(FlowScoreResult(
        flow.flowId,flowScore, flow.flowLimitScore, flowScore > flow.flowLimitScore,
        flow.strategyCode, hitRules.toList, new Date().toString
      ))

    }
    flowScores.toArray
  }
  /**
   * 计算流程分数：计算流程分数，需要参考流程打分详细说明书
   * 涉及3个系数
   * 系数2权重： 60%， 数据区间10~60
   * 系数3权重： 40%， 数据区间0~40
   * 所以系数2+系数3权重区间为10~100
   * 系数1为： 平均分/10
   * factor1 * (factor2 + factor3)区间为：平均分~10倍
   * @param result
   * @param isTriggered
   */
  def calculateFlowScore(result: Array[Array[Double]], isTriggered: Array[Int]) = {
    //从二维数组拿到打分列表
    val scores: Array[Double] = result(1)
    //总打分
    val sum: Double = scores.sum
    //打分列表的长度
    val dim: Int = scores.length
    //系数1：平均分/10
    val factor1: Double = sum / (10 * dim)
    //获取到命中数据库启用规则Score
    val xa: Array[Double] = triggeredScore(scores, isTriggered)
    //获取规则中，规则分数最高的
    val maxInxa =
      if (xa.isEmpty) {
        0.0
      } else {
        xa.max
      }
    //系数2， 权重60， 是指最高以60/
    //最高score大于6， 就给满权重60， 如果不足6，就给对应的maxInxa * 10
    val factor2: Double = if (1 < (1.0 / 6.0) * maxInxa) {
      60.0
    } else {
      (1.0 / 6.0) * maxInxa * 60
    }

    //系数3，打开的规则总分占总规则分数的百分比，且系数3的权重是40%
    val factor3: Double = 40 * (xa.sum / sum)

    factor1 * (factor2 + factor3)
  }


  /**
   * 返回启用规则数组
   * @param scores
   * @param isTriggered
   */
  def triggeredScore(scores: Array[Double], isTriggered: Array[Int]) = {
    val scoreBuffer: ArrayBuffer[Double] = new ArrayBuffer[Double]()
    for (i <- 0 until isTriggered.length) {
      if (isTriggered(i) == 0) {
        scoreBuffer += scores(i)
      }
    }
    scoreBuffer.toArray
  }

  /**
   * 将结构化数据持久化到HDFS
   * @param sc
   * @param lines
   */
  def lines2HDFS(sc: SparkContext, lines: DStream[String]) = {
    lines.foreachRDD(rdd =>{
      val date = new SimpleDateFormat("yyyy/MM/dd/HH").format(System.currentTimeMillis())
      val yyyyMMddHH = date.replace("/", "").toInt
      val path: String = PropertiesUtil.getStringByKey("blackListPath", "HDFSPathConfig.properties") + "path/" + yyyyMMddHH
      try {
        // 注意：一定要注意数据量和物理资源的对比，如果资源紧张，就不能这样操作
        sc.textFile(path).union(rdd).repartition(1).saveAsTextFile(path)
      } catch {
        case e: Exception =>
          rdd.repartition(1).saveAsTextFile(path)
      }
    })
  }

}
