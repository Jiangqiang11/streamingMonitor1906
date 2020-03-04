package com.jq.streamingMoitor.dataprocess.businessprocess

import java.util.Properties

import com.jq.streamingMoitor.common.bean.ProcessedData
import com.jq.streamingMoitor.common.util.jedis.PropertiesUtil
import com.jq.streamingMoitor.dataprocess.constants.BehaviorTypeEnum
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.apache.spark.rdd.RDD

object DataSend {

  /**
   * 推送book数据
   * @param dataProcess
   */
  def sendBookData2Kafka(dataProcess: RDD[ProcessedData]): Unit = {
    //过滤book数据
    val bookData2Kafka: RDD[String] = dataProcess
      .filter(p => {
        p.requestType.behaviorType == BehaviorTypeEnum.Book
      })
      .map(_.toKafkaString())
    //判断，如果有查询数据则推送到kafka
    if (!bookData2Kafka.isEmpty()) {
      //topic
      val bookTopic: String = PropertiesUtil.getStringByKey("source.book.topic", "kafkaConfig.properties")

      //kafka params
      val kafkaPros: Properties = new Properties
      kafkaPros.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, PropertiesUtil.getStringByKey("default.brokers", "kafkaConfig.properties"))
      kafkaPros.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, PropertiesUtil.getStringByKey("default.key_serializer_class_config", "kafkaConfig.properties"))
      kafkaPros.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, PropertiesUtil.getStringByKey("default.value_serializer_class_config","kafkaConfig.properties"))
      kafkaPros.put(ProducerConfig.BATCH_SIZE_CONFIG, PropertiesUtil.getStringByKey("default.batch_size_config", "kafkaConfig.properties") )
      kafkaPros.put(ProducerConfig.LINGER_MS_CONFIG, PropertiesUtil.getStringByKey("default.linger_ms_config", "kafkaConfig.properties"))

      //发送数据
      bookData2Kafka.foreachPartition(it => {
        val producer: KafkaProducer[String, String] = new KafkaProducer[String, String](kafkaPros)
        it.foreach(line =>{
          val msg: ProducerRecord[String, String] = new ProducerRecord[String, String](bookTopic, null, line)
          producer.send(msg)
        })
        producer.close()
      })
    }
  }


  /**
   * 推送query数据
   *
   * @param dataProcess
   */
  def sendQueryData2Kafka(dataProcess: RDD[ProcessedData]): Unit = {
    //过滤query数据
    val queryData2Kafka: RDD[String] = dataProcess
      .filter(p => {
        p.requestType.behaviorType == BehaviorTypeEnum.Query
      })
      .map(_.toKafkaString())
    //判断，如果有查询数据则推送到kafka
    if (!queryData2Kafka.isEmpty()) {
      //topic
      val queryTopic: String = PropertiesUtil.getStringByKey("source.query.topic", "kafkaConfig.properties")

      //kafka params
      val kafkaPros: Properties = new Properties
      kafkaPros.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, PropertiesUtil.getStringByKey("default.brokers", "kafkaConfig.properties"))
      kafkaPros.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, PropertiesUtil.getStringByKey("default.key_serializer_class_config", "kafkaConfig.properties"))
      kafkaPros.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, PropertiesUtil.getStringByKey("default.value_serializer_class_config","kafkaConfig.properties"))
      kafkaPros.put(ProducerConfig.BATCH_SIZE_CONFIG, PropertiesUtil.getStringByKey("default.batch_size_config", "kafkaConfig.properties") )
      kafkaPros.put(ProducerConfig.LINGER_MS_CONFIG, PropertiesUtil.getStringByKey("default.linger_ms_config", "kafkaConfig.properties"))

      //发送数据
      queryData2Kafka.foreachPartition(it => {
        val producer: KafkaProducer[String, String] = new KafkaProducer[String, String](kafkaPros)
        it.foreach(line =>{
          val msg: ProducerRecord[String, String] = new ProducerRecord[String, String](queryTopic, null, line)
          producer.send(msg)
        })
        producer.close()
      })
    }
  }
}
