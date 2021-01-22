package com.kevin.hbase;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Table;

import java.io.IOException;
import java.util.Properties;

public class HBaseClient
{
  private static Log log = LogFactory.getLog(HBaseClient.class);

  private static HBaseClient instance = null;
  private Connection connection;

  private HBaseClient(){
  }

  public static HBaseClient getInstance(Properties props) {
    if (instance == null) {
      synchronized (HBaseClient.class) {
        if (instance == null) {
          instance = new HBaseClient();
          instance.init(props);
        }
      }
    }

    if (instance.connection == null || instance.connection.isClosed()) {
      synchronized (HBaseClient.class) {
        if (instance.connection == null || instance.connection.isClosed()) {
          instance.init(props);
        }
      }
    }

    return instance;
  }


  public void init(Properties prop) {
    Configuration config = HBaseConfiguration.create();
    config.set("hbase.zookeeper.property.clientPort", prop.getProperty("hbase.zookeeper.property.clientPort"));
    config.set("hbase.zookeeper.quorum", prop.getProperty("hbase.zookeeper.quorum"));
    try {
      connection = ConnectionFactory.createConnection(config);
    } catch (Exception e) {
      log.error("create hbase connetion error!", e);
    }
  }

  public Table getTable(String tableName) {
    Table table = null;
    try {
      table = connection.getTable(TableName.valueOf(tableName));
    } catch (Exception e) {
      table = null;
      log.error("get habse table error,tableName=" + tableName, e);
    }

    return table;
  }

  public static void closeTable(Table table) {
    if (table != null) {
      try {
        table.close();
      } catch (Exception e) {
        log.error("close table error,tableName=" + table.getName(), e);
      }
    }
  }

  public Connection getConnection() {
    return connection;
  }

  public void close()
  {
    if (connection != null && !connection.isClosed()) {
      try {
        connection.close();
      } catch (IOException e) {
        log.error("close hbase connect error", e);
      }
    }
  }
}
