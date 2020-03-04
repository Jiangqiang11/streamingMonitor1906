package com.jq.streamingMoitor.dataprocess.businessprocess

import scala.collection.mutable.ArrayBuffer

object IpOperation {

  def isFrequencyIp(
                     remote_addr: String,
                     value: ArrayBuffer[String]
                   ) = {
    //标记是否是高频ip
    var flag = false
    //从广播变量获取黑名单进行匹配
    val it = value.iterator
    while (it.hasNext) {
      val blackIp = it.next()
      //匹配
      if(blackIp.eq(remote_addr)) {
        flag = true
      }
    }
    flag
  }

}
