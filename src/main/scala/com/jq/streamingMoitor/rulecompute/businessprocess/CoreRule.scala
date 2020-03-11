package com.jq.streamingMoitor.rulecompute.businessprocess

import com.jq.streamingMoitor.common.bean.ProcessedData
import org.apache.spark.streaming.Duration
import org.apache.spark.streaming.dstream.DStream

import scala.collection.mutable.ArrayBuffer

object CoreRule {



  /**
   * 单位时间内ip总访问量(五分钟)
 *
   * @param structuredDataLines
   * @param duration
   * @param duration1
   */
  def ipAccessCounts(
                      structuredDataLines: DStream[ProcessedData],
                      duration: Duration,
                      duration1: Duration
                    ) = {
    structuredDataLines
      .map(processData => (processData.remoteAddr, 1))
      .reduceByKeyAndWindow((x:Int, y:Int) => x + y, duration, duration1)
  }


  /**
   * 单位时间内ip段访问量
   *
   * @param structuredDataLines
   * @param duration 窗口大小
   * @param duration1 窗口滑动时间
   */
  def ipBlackAccessCounts(
                           structuredDataLines: DStream[ProcessedData],
                           duration: Duration,
                           duration1: Duration
                         ) = {
    //获取ip字段
    structuredDataLines.map(processData => {
      //拿到ip
      val remoteAddr = processData.remoteAddr
      //判断当前ip是否存在
      if (remoteAddr.equalsIgnoreCase("NULL")) {
        (remoteAddr, 1)
      } else {
        val arr = remoteAddr.split("\\.")
        (arr(0) + "." + arr(1), 1)
      }
    }).reduceByKeyAndWindow((x:Int, y:Int) => x + y, duration, duration1)
  }

  /**
   * 关键页面，5分钟内的ip的总访问量
   * @param structuredDataLines
   * @param duration
   * @param duration1
   * @param value 关键页面广播变量值
   * @return
   */
  def criticalPagesCounts(
                           structuredDataLines: DStream[ProcessedData],
                           duration: Duration,
                           duration1: Duration,
                           value: ArrayBuffer[String]
                         ) = {

    structuredDataLines.map( processedData => {
      //获取request字段
      val request: String = processedData.request
      //判断是否是关键页面
      var flag = false
      for (page <- value) {
        if(request.matches(page)) {
          flag = true
        }
      }
      //如果是关键页面,则记录当前ip
      if (flag) {
        (processedData.remoteAddr, 1)
      } else {
        (processedData.remoteAddr, 0)
      }
    }).reduceByKeyAndWindow((x:Int, y:Int) => x + y, duration, duration1)
  }

  /**
   * 统计UA的种类数
   * @param structuredDataLines
   * @param duration
   * @param duration1
   */
  def userAgent(
                       structuredDataLines: DStream[ProcessedData],
                       duration: Duration,
                       duration1: Duration
                     ) = {
    structuredDataLines.map(processeddata => {
      (processeddata.remoteAddr, processeddata.httpUserAgent)
    }).groupByKeyAndWindow(duration, duration1)
  }

  /**
   * 关键页面最小访问时间间隔
   * @param structuredDataLines
   * @param duration
   * @param duration1
   * @param value
   */
  def criticalPagesAccTime(
                            structuredDataLines: DStream[ProcessedData],
                            duration: Duration,
                            duration1: Duration,
                            value: ArrayBuffer[String]
                          ) = {
    structuredDataLines.map(processedData => {
      val accessTime = processedData.timeIso8601
      val request = processedData.request
      var flag = false
      for (page <- value) {
        if(request.matches(page)){
          flag = true
        }
      }

      if (flag) {
        (processedData.remoteAddr, accessTime)
      } else {
        (processedData.remoteAddr, "0")
      }
    }).groupByKeyAndWindow(duration, duration1)
  }

  /**
   * 一定时间小于最短时间间隔的关键页面查询次数
   * @param structuredDataLines
   * @param duration
   * @param duration1
   * @param value
   */
  def aCriticalPagesTime(
                          structuredDataLines: DStream[ProcessedData],
                          duration: Duration,
                          duration1: Duration,
                          value: ArrayBuffer[String]
                        ) = {
    structuredDataLines.map(processedData => {
      val accessTime = processedData.timeIso8601
      val request = processedData.request
      var flag = false
      for ( page <- value) {
        if (request.matches(page)) {
          flag = true
        }
      }
      if (flag) {
        ((processedData.remoteAddr, request), accessTime)
      } else {
        ((processedData.remoteAddr, request), "0")
      }
    }).groupByKeyAndWindow(duration, duration1)
  }

  /**
   * 统计不同行程的次数
   * @param structuredDataLines
   * @param duration
   * @param duration1
   */
  def flightQuery(
                   structuredDataLines: DStream[ProcessedData],
                   duration: Duration,
                   duration1: Duration
                 ) = {
    structuredDataLines.map(processedData => {
      val ip = processedData.remoteAddr
      val depCity = processedData.requestParams.depcity
      val arrCity = processedData.requestParams.arrcity
      (ip, (depCity, arrCity))
    }).groupByKeyAndWindow(duration, duration1)
  }

  /**
   * 5分钟内关键页面访问次数的cookie数
   * @param structuredDataLines
   * @param duration
   * @param duration1
   * @param value
   */
  def criticalCoolie(
                      structuredDataLines: DStream[ProcessedData],
                      duration: Duration,
                      duration1: Duration,
                      value: ArrayBuffer[String]
                    ) = {
    structuredDataLines.map(processedData => {
      val request = processedData.request
      var flag = false
      for (page <- value) {
        if (request.matches(page)) {
          flag = true
        }
      }

      if (flag) {
        (processedData.remoteAddr, processedData.cookieValue_JSESSIONID)
      } else {
        (processedData.remoteAddr, "0")
      }
    }).groupByKeyAndWindow(duration, duration1)
  }
}
