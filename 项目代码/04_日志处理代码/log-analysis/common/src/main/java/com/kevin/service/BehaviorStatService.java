package com.kevin.service;

import com.kevin.hbase.HBaseClient;
import com.kevin.model.UserCityStatModel;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;

import java.util.Properties;

public class BehaviorStatService
{
  private Properties props;
  private static BehaviorStatService service;

  // 单例模式
  public static BehaviorStatService getInstance(Properties props) {
    if (service == null) {
      synchronized (BehaviorStatService.class) {
        if (service == null) {
          service = new BehaviorStatService();
          service.props = props;
        }
      }
    }

    return service;
  }

  public void userNumberStat(UserCityStatModel model) {
    addUserNumOfCity(model);
  }

  /*
  * 实时统计每个城市的用户数量
  * */
  public void addUserNumOfCity(UserCityStatModel model) {
    String tableName = "online_city_click_count" ;
    Table table = HBaseClient.getInstance(this.props).getTable(tableName);
    String rowKey = String.valueOf(model.getCity());
    Long count = model.getNum();

    try {
      table.incrementColumnValue(Bytes.toBytes(rowKey), Bytes.toBytes("StatisticData"), Bytes.toBytes("userNum"), count);
    } catch (Exception ex) {
      HBaseClient.closeTable(table);
      ex.printStackTrace();
    }
  }

}
