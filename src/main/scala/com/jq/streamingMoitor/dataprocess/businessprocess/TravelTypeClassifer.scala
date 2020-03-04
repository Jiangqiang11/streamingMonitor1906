package com.jq.streamingMoitor.dataprocess.businessprocess

import com.jq.streamingMoitor.dataprocess.constants.TravelTypeEnum
import io.lemonlabs.uri.Url

object TravelTypeClassifer {

  def classiferByRefererAndRequestbody(http_referer: String) = {
    //定义日期个数
    var dateCounts = 0
    //定义匹配日期的正则
    val regex = "^(\\d{4})-(0\\d{1}|1[0-2])-(0\\d{1}|[12]\\d{1}|3[01])$"

//    //判断当前日Url是否携带参数
//    if (http_referer.contains("？") && http_referer.split("\\?").length > 1) {
//      //切分数据
//      val params = http_referer.split("\\?")(1).split("&")
//      //循环params的数据
//      params.foreach(param =>{
//
//      })
//    }
    //用uri解析
    val uri = Url.parse(http_referer)
    //将？后面的所有参数解析
    val paramMap: Map[String, Vector[String]] = uri.query.paramMap
    paramMap.foreach(p =>{
      p._2.foreach(it =>{
        if (it.matches(regex)){
          dateCounts += 1
        }
      })
    })

    //返回标签
    if(dateCounts == 1) {
      //单程
      TravelTypeEnum.OneWay
    } else if (dateCounts == 2) {
      //往返
      TravelTypeEnum.RoundTrip
    } else {
      //其他
      TravelTypeEnum.Unknown
    }

  }

}
