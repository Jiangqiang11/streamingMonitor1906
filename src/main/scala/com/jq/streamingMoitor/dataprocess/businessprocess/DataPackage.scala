package com.jq.streamingMoitor.dataprocess.businessprocess

import com.jq.streamingMoitor.common.bean.{AccessLog, BookRequestData, CoreRequestParams, ProcessedData, QueryRequestData, RequestType}
import com.jq.streamingMoitor.common.util.decode.MD5
import com.jq.streamingMoitor.dataprocess.constants.TravelTypeEnum

object DataPackage {

  def dataPackage(
                   str: String,
                   log: AccessLog,
                   requestTypeLable: RequestType,
                   travelType: TravelTypeEnum.Value,
                   queryRequestDate: Option[QueryRequestData],
                   bookRequestData: Option[BookRequestData],
                   highFrqIpGroup: Boolean
                 ) = {
    //飞行时间
    var flightData = ""
    bookRequestData match {
      case Some(book) => flightData = book.flightDate.mkString
      case None => println("null")
    }
    queryRequestDate match {
      case Some(query) => flightData = query.flightDate.toString()
      case None => println("null")
    }

    //始发地
    var depCity = ""
    bookRequestData match {
      case Some(book) => depCity = book.depCity.mkString
      case None => println("null")
    }
    queryRequestDate match {
      case Some(query) => depCity = query.depCity.mkString
      case None => println("null")
    }
    //目的地
    var arrCity = ""
    bookRequestData match {
      case Some(book) => arrCity = book.arrCity.mkString
      case None => println("null")
    }
    queryRequestDate match {
      case Some(query) => arrCity = query.arrCity.mkString
      case None => println("null")
    }

    //封装参数
    val params = CoreRequestParams(flightData, depCity, arrCity)


    // 对jessionID和userID做脱敏操作
    val md5 = new MD5
    val jessionID = md5.getMD5ofStr(log.jessionID)
    val userID = md5.getMD5ofStr(log.userID)

    ProcessedData(
      str, log.request_method, log.request,
      log.remote_addr, log.http_user_agent, log.time_iso8601,
      log.server_addr, highFrqIpGroup, requestTypeLable,
      travelType, params, jessionID, userID,
      queryRequestDate, bookRequestData, log.http_referer.toString
    )
  }

}
