package com.jq.streamingMoitor.common.util.hdfs

import com.jq.streamingMoitor.common.util.jedis.PropertiesUtil
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.types.{StringType, StructField, StructType}
import org.apache.spark.sql.{DataFrame, Row, SQLContext, SparkSession}


/**
 * 黑名单保存到HDFS服务
 */
object BlackListToHDFS {
  /**
    * 保存黑名单到HDFS
    *
    * @param antiBlackListRDD：传入黑名单RDD
    * @param spark：传入SparkSession创建DataFrame
    */
  def saveAntiBlackList(antiBlackListRDD: RDD[Row], spark: SparkSession) ={

    //构建DataFrame
    val tableCols = List("keyExpTime","key","value")
//    val schemaString=tableCols.mkString("-")
    val schema = StructType(tableCols.map(fieldName => StructField(fieldName, StringType, true)))
    val dataFrame: DataFrame = spark.createDataFrame(antiBlackListRDD,schema)
    val path: String = PropertiesUtil.getStringByKey("blackListPath","HDFSPathConfig.properties")
    HdfsSaveUtil.save(dataFrame,null,path)
  }

  def saveAntiOcpBlackList(): Unit ={


  }

}
