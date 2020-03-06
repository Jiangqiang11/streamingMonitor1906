package com.jq.streamingMoitor.rulecompute.businessprocess

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.jq.streamingMoitor.common.bean.{BookRequestData, CoreRequestParams, ProcessedData, QueryRequestData, RequestType}
import com.jq.streamingMoitor.dataprocess.constants.TravelTypeEnum.TravelTypeEnum
import com.jq.streamingMoitor.dataprocess.constants.{BehaviorTypeEnum, FlightTypeEnum, TravelTypeEnum}
import org.apache.spark.streaming.dstream.DStream

object QueryDataPackage {

  /**
   * 封装查询数据a
   * @param lines
   */
  def queryDataLoadAndPackage(lines: DStream[String]) = {
    lines.mapPartitions(it => {
      //拿到一个json解析实例
      val mapper = new ObjectMapper()
      //注册scala模型
      mapper.registerModule(DefaultScalaModule)
      //将数据进行处理
      it.map(line =>{
        val dataArray: Array[String] = line.split("#CS#")
        val sourceData = dataArray(0)
        val requestMethod = dataArray(1)
        val request = dataArray(2)
        val remoteAddr = dataArray(3)
        val httpUserAgent = dataArray(4)
        val timeIso8601 = dataArray(5)
        val serverAddr = dataArray(6)
        val highFrqIPGroup: Boolean = dataArray(7).equalsIgnoreCase("true")
        val requestType: RequestType = RequestType(FlightTypeEnum.withName(dataArray(8)), BehaviorTypeEnum.withName(dataArray(9)))
        val travelType: TravelTypeEnum = TravelTypeEnum.withName(dataArray(10))
        val requestParams: CoreRequestParams = CoreRequestParams(dataArray(11), dataArray(12), dataArray(13))
        val cookieValue_JSESSIONID: String = dataArray(14)
        val cookieValue_USERID: String = dataArray(15)
        //分析查询请求的时候不需要book数据
        val bookRequestData: Option[BookRequestData] = None
        //封装query数据
        val queryRequestData = if (!dataArray(16).equalsIgnoreCase("NULL")) {
          mapper.readValue(dataArray(16), classOf[QueryRequestData]) match {
            case value if value != null => Some(value)
            case _ => None
          }
        } else {
          None
        }
        val httpReferrer = dataArray(18)

        //封装数据，返回ProcessedData
        ProcessedData(sourceData, requestMethod,request, remoteAddr,
          httpUserAgent,timeIso8601, serverAddr, highFrqIPGroup, requestType,
          travelType, requestParams, cookieValue_JSESSIONID, cookieValue_USERID,
          queryRequestData, bookRequestData, httpReferrer
        )

      })

    })
  }

}













