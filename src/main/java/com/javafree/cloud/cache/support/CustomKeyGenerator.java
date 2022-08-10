package com.javafree.cloud.cache.support;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.StringJoiner;
import java.util.TimeZone;

/**
 * @version V1.0
 * @Description: 自己定义@Cacheable 的keyGenerator
 * 缓存key的生成规则为取
 * @Author gwz  gwz126@126.com
 * @Date 2022/8/5 14:45
 */

public class CustomKeyGenerator implements KeyGenerator {

  private static ObjectMapper objectMapper = new ObjectMapper();


  // 日期格式化
  private static final String STANDARD_FORMAT = "yyyy-MM-dd HH:mm:ss";

  static {
    //忽略 在json字符串中存在，但是在java对象中不存在对应属性的情况。防止错误
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    //对象值不为空的字段列入
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    //对象的所有字段全部列入
    //objectMapper.setSerializationInclusion(Include.ALWAYS);
    //取消默认转换timestamps形式
    objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    //忽略空Bean转json的错误
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    //所有的日期格式都统一为以下的样式，即yyyy-MM-dd HH:mm:ss
    objectMapper.setDateFormat(new SimpleDateFormat(STANDARD_FORMAT));
    objectMapper.setTimeZone(TimeZone.getTimeZone("GMT+8"));//解决时区差8小时问题
  }

  /**
   * 对象转为json字符串
   * @param obj
   * @return
   */
  private static String getJsonStringFromObject(Object obj) {
    StringWriter writer = new StringWriter();
    try {
      objectMapper.writeValue(writer, obj);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return writer.toString();
  }

  /**
   * 对象数组转为字符串
   * @param arr
   * @param delim
   * @return
   */
  private static String arrayToString(@Nullable Object[] arr, String delim) {
    if (ObjectUtils.isEmpty(arr)) {
      return "";
    }
    if (arr.length == 1) {
      return ObjectUtils.nullSafeToString(arr[0]);
    }

    StringJoiner sj = new StringJoiner(delim);
    for (Object elem : arr) {
      sj.add(String.valueOf(getJsonStringFromObject(elem)));
    }
    return sj.toString();
  }

  /**
   * 对字符串取指纹码
   * @param value
   * @return
   */
  private String StringMD5(String value){
    try {
      MessageDigest mDigest = MessageDigest.getInstance("MD5");
      byte[] md5 = mDigest.digest(value.getBytes());
      StringBuilder sb = new StringBuilder();
      //bytesToHex
      for (byte b : md5) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    }
    return "";
  }
  /**
   * cache key的生成
   * @param target
   * @param method
   * @param params
   * @return
   */
  public Object generate(Object target, Method method, Object... params) {
    return target.getClass().getSimpleName() + "_"
            + method.getName() + "_"
            + StringMD5(arrayToString(params, "_"));

}
}
