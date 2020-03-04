package com.jq.streamingMoitor.dataprocess.businessprocess

import com.jq.streamingMoitor.common.bean.RequestType
import com.jq.streamingMoitor.dataprocess.constants.{BehaviorTypeEnum, FlightTypeEnum}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object RequstTypeClassifer {

  /**
   * 根据request RUL进行标签的生成
   * 国内查询(0, 0)
   * 国内预定(0, 1)
   * 国际查询(1, 0)
   * 国际预定(1, 1)
   * 其他(-1, -1)
   * @param request
   * @param ruleMap
   */
  def classifyByRequest(request: String, ruleMap: mutable.HashMap[String, ArrayBuffer[String]]) = {
    //获取数据的规则(正则信息)
    val nqArr: ArrayBuffer[String] = ruleMap("nq") //国内查询
    val nbArr: ArrayBuffer[String] = ruleMap("nb") //国内预定
    val iqArr: ArrayBuffer[String] = ruleMap("iq")  //国际查询
    val ibArr: ArrayBuffer[String] = ruleMap("ib")  //国际预定

    //可变变量
    var flag = true

    //请求参数
    var requestType :RequestType = null

    //国内查询匹配
    nqArr.foreach(rule => {
      //匹配
      if (request.matches(rule)){
        //匹配上，设置为false，不用再打其他标签
        flag = false
        //打标签(0, 0)
        requestType= RequestType(FlightTypeEnum.National, BehaviorTypeEnum.Query)
      }
    })

    //国内预定匹配
    nbArr.foreach(rule => {
      //匹配
      if (request.matches(rule)){
        //匹配上，设置为false，不用再打其他标签
        flag = false
        //打标签(0, 1)
        requestType = RequestType(FlightTypeEnum.National, BehaviorTypeEnum.Book)
      }
    })

    //国际查询匹配
    iqArr.foreach(rule => {
      //匹配
      if (request.matches(rule)){
        //匹配上，设置为false，不用再打其他标签
        flag = false
        //打标签(1, 0)
        requestType = RequestType(FlightTypeEnum.International, BehaviorTypeEnum.Query)
      }
    })

    //国际预定匹配
    ibArr.foreach(rule => {
      //匹配
      if (request.matches(rule)){
        //匹配上，设置为false，不用再打其他标签
        flag = false
        //打标签(1, 1)
        requestType = RequestType(FlightTypeEnum.International, BehaviorTypeEnum.Book)
      }
    })

    //如果什么都没匹配上，返回(-1, -1)
    if(flag) {
      requestType = RequestType(FlightTypeEnum.Other, BehaviorTypeEnum.Other)
    }
    requestType
  }

}





