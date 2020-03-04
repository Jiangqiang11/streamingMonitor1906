package com.jq.streamingMoitor.common.bean

/**
  * 日志实体类
  */
case class AccessLog (time_local:String,
                      request:String,
                      request_method:String,
                      content_type:String,
                      request_body:String,
                      http_referer:String,
                      remote_addr:String,
                      http_user_agent:String,
                      time_iso8601:String,
                      server_addr:String,
                      var http_cookie:String,
                      connectionActive:Int,
                      jessionID:String,
                      userID:String
                     )
