package com.kevin.monitor;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.log4j.Logger;
import org.apache.spark.streaming.StreamingContextState;
import org.apache.spark.streaming.api.java.JavaStreamingContext;

import java.net.URI;
import java.util.Properties;

public class MonitorStopThread implements Runnable {
  private Logger log = Logger.getLogger(MonitorStopThread.class);

  private String nameservices;
  private String hadoopUser;
  private String nn1;
  private String nn2;
  private String nn1Rpc;
  private String nn2Rpc;
  private String stopPath;

  private JavaStreamingContext javaStreamingContext;

  public MonitorStopThread(JavaStreamingContext javaStreamingContext, Properties prop) {
    this.javaStreamingContext = javaStreamingContext;

    this.nameservices = prop.getProperty("hadoop.nameservices");
    this.hadoopUser = prop.getProperty("hadoop.user");
    this.nn1 = prop.getProperty("hadoop.nn1");
    this.nn2 = prop.getProperty("hadoop.nn2");
    this.nn1Rpc = prop.getProperty("hadoop.nn1.rpc");
    this.nn2Rpc = prop.getProperty("hadoop.nn2.rpc");
    this.stopPath = prop.getProperty("streaming.stop.path");
  }

  public boolean isExists(String hdfsFile) {
    FileSystem fs = null;

    try {
      Configuration conf = new Configuration();
      conf.set("fs.defaultFS", "hdfs://" + nameservices);
      conf.set("dfs.nameservices", nameservices);
      conf.set("dfs.ha.namenodes." + nameservices, nn1 + "," + nn2);
      conf.set("dfs.namenode.rpc-address." + nameservices + "." + nn1, nn1Rpc);
      conf.set("dfs.namenode.rpc-address." + nameservices + "." + nn2, nn2Rpc);
      conf.set("dfs.client.failover.proxy.provider." + nameservices, "org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider");

      fs = FileSystem.get(new URI("hdfs://" + nameservices), conf, hadoopUser);
      return fs.exists(new Path(hdfsFile));
    } catch (Exception e) {
      return false;
    } finally {
      if (fs != null) {
        try {
          fs.close();
        } catch (Exception ex) {
          System.out.println("close fs error");
        }
      }
    }
  }

  public void run() {
    while (true) {
      try {
        Thread.sleep(20000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }

      StreamingContextState state = javaStreamingContext.getState();
      log.info("the streamingContextState of javaStreamingContext is " + state.toString());

      if (isExists(stopPath)) {
        log.info("hdfs stop path exists,begin to stop streaming...");

        if (state == StreamingContextState.ACTIVE) {
          javaStreamingContext.stop(true, true);
        } else if (state == StreamingContextState.STOPPED) {
          log.info("hdfs stop path exists,streaming be stoped");
          break;
        }
      }
    }
  }
}
