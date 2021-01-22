package com.kevin.utils;

import org.apache.commons.lang.StringUtils;

public final class StringUtil
{
  private StringUtil()
  {
  }

  public static boolean isEmpty(String s)
  {
    if (s == null || s.replace(" ", "").equals("")) {
      return true;
    }

    return false;
  }

  /*
   * 根据已知字符串生产定长字符串，长度不够左边补0
   */
  public static String getFixedLengthStr(String s, int maxLength)
  {

    s = StringUtil.isEmpty(s) ? "" : s.replace(" ", "");

    if (s.length() >= maxLength) {
      return s;
    }

    return generateZeroString(maxLength - s.length()) + s;

  }

  /*
   * 获取指定长度的0字符串
   */
  /**
   * 生成一个定长的纯0字符串
   * 
   * @param length
   *          字符串长度
   * @return 纯0字符串
   */
  public static String generateZeroString(int length)
  {
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < length; i++) {
      sb.append('0');
    }
    return sb.toString();
  }

  /*
  * 字符串转long
  * */
  public static long getLongFromString(String s)
  {
    long n = 0;

    try {
      n = StringUtils.isEmpty(s) ? 0 : Long.parseLong(s);
    } catch (Exception e) {
      n = 0;
    }

    return n;
  }

  /*
  * 字符串转long
  * */
  public static long getLongFromObject(Object o)
  {
    if (o == null || !(o instanceof Long)) {
      return 0;
    } else {
      return (Long) o;
    }
  }

  public static void main(String[] args)
  {
    System.out.println(getFixedLengthStr("123", 20));
  }
}
