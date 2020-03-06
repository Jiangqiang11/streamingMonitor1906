package com.jq.streamingMoitor.rulecompute.businessprocess

import com.jq.streamingMoitor.common.bean.ProcessedData
import org.apache.spark.streaming.Duration
import org.apache.spark.streaming.dstream.DStream

object CoreRule {


  /**
   * 单位时间内ip总访问量(五分钟)
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

}
