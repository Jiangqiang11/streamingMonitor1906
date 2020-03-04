package com.jq.streamingMoitor.dataprocess.businessprocess

import java.util.regex.{Matcher, Pattern}

import com.jq.streamingMoitor.common.util.decode.MD5

object EncryedData {
  /**
   * 身份证脱敏
   * @param http_cookie
   * @return
   */
  def encryptedId(http_cookie: String): String = {
    //MD5实例
    val md5 = new MD5()

    //获取对应的字段
    var cookie = http_cookie

    //正则
    val idPattern = Pattern
      .compile("(\\d{18})|(\\d{17}(\\d|X|x))|(\\d{15})")

    val idMatcher: Matcher = idPattern.matcher(http_cookie)

    while (idMatcher.find()) {
      //获取到身份证之后, 在获取身份证的前一个字符和后一个字符
      //数据里的身份证前后是由前缀("=") 和 后缀(":") 这样的字符，所以需要根据数据进行判断处理
      //身份证号的前一个index
      val lowIndex = http_cookie.indexOf(idMatcher.group()) - 1
      //身份证号的后一个index
      val highIndex = lowIndex + idMatcher.group().length + 1
      //身份证号的前一个字符
      val lowLetter = http_cookie.charAt(lowIndex).toString

      //匹配当前第一位不是数字
      if ( !lowLetter.matches("^[0-9]&")) {
        //如果字符串的最后是身份证号，直接替换即可
        if (highIndex < http_cookie.length) {
          //获取身份证号的后一个字符
          val highLetter = http_cookie.charAt(highIndex).toString
          //判断身份证号后一位是不是数字
          if ( !highLetter.matches("^[0-9]&")) {
            //开始替换
            cookie = cookie.replace(idMatcher.group(), md5.getMD5ofStr(idMatcher.group()))
          }
        }
      }
    }
    cookie
  }


  /**
   * 手机号脱敏
   * @param http_cookie
   * @return
   */
  def encryptedPhone(http_cookie: String): String = {

    //MD5实例
    val md5 = new MD5()

    //获取对应的字段
    var cookie = http_cookie

    //正则
    val phonePattern = Pattern
      .compile("((13[0-9])|(14[5|7])|(15([0-3]|[5-9]))|(17[0-9])|(18[0-9]))\\d{8}")

    val phoneMatcher: Matcher = phonePattern.matcher(http_cookie)

    while (phoneMatcher.find()) {
      //获取到手机号之后, 在获取手机号的前一个字符和后一个字符
      //数据里的手机号前后是由前缀("=") 和 后缀(":") 这样的字符，所以需要根据数据进行判断处理
      //手机号的前一个index
      val lowIndex = http_cookie.indexOf(phoneMatcher.group()) - 1
      //手机号的后一个index
      val highIndex = lowIndex + phoneMatcher.group().length + 1
      //手机号的前一个字符
      val lowLetter = http_cookie.charAt(lowIndex).toString

      //匹配当前第一位不是数字
      if ( !lowLetter.matches("^[0-9]&")) {
        //如果字符串的最后是手机号，直接替换即可
        if (highIndex < http_cookie.length) {
          //获取手机号的后一个字符
          val highLetter = http_cookie.charAt(highIndex).toString
          //判断手机号后一位是不是数字
          if (!highLetter.matches("^[0-9]&")) {
            //开始替换
            cookie = cookie.replace(phoneMatcher.group(), md5.getMD5ofStr(phoneMatcher.group()))
          }
        }
      }

    }
    cookie
  }

}











