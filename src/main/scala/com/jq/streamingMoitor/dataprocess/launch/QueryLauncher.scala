package com.jq.streamingMoitor.dataprocess.launch

import com.jq.streamingMoitor.common.util.log4j.LoggerLevels

object QueryLauncher {
  def main(args: Array[String]): Unit = {

    //设置日志级别
    LoggerLevels.setStreamingLogLevels()
    //当应用被停止的时候，进行如下设置可以保证当前批次执行完成之后再停止应用

  }

}
