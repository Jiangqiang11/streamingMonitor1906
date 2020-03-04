package com.jq.streamingMoitor.dataprocess.businessprocess

import com.jq.streamingMoitor.common.bean.AccessLog
import scala.collection.mutable.ArrayBuffer

object UrlFilter {

  def filterUrl(log: AccessLog, filterRulerRef: ArrayBuffer[String]):Boolean = {

    var isMatch = true
    filterRulerRef.foreach(str =>{
      if (log.request.matches(str)) {
        isMatch = false
      }
    })
    isMatch
  }

}
