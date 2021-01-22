package com.kevin.registrator;

import com.kevin.model.StartupReportLogs;
import com.esotericsoftware.kryo.Kryo;
import org.apache.spark.serializer.KryoRegistrator;

//注册需要使用kryo序列化的类
public class MyKryoRegistrator implements KryoRegistrator
{
  public void registerClasses(Kryo kryo)
  {
    kryo.register(StartupReportLogs.class);
  }
}
