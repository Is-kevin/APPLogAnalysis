package com.kevin.hbase;

import com.kevin.utils.StringUtil;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;

public class HbaseGet {
    public static void main( String[] args ) throws Exception
    {
        String table = "online_city_users";
        String rowkey = "Beijing";
        String family = "StatisticData";
        String column = "userNum";
        int targetNum = getHBaseHexNum(table,rowkey, family, column);
        String resultString = "The UserNum Of " + rowkey + " is : " + targetNum;
        System.out.println(resultString);
    }

    /**
     * 获取HBase中的十六进制数值
     * @param tableName
     * @param rowKey
     * @param family
     * @param column
     * @return
     * @throws Exception
     */
    private static int getHBaseHexNum(String tableName, String rowKey, String family, String column) throws Exception{
        //创建配置对象
        Configuration conf = HBaseConfiguration.create();

        //通过连接工厂创建连接对象
        Connection conn = ConnectionFactory.createConnection(conf);

        //通过连接获取table信息
        Table table = conn.getTable(TableName.valueOf(tableName));

        //创建Get
        Get get = new Get(Bytes.toBytes(rowKey));

        get.addColumn(Bytes.toBytes(family), Bytes.toBytes(column));

        //将这一行存储到HBase
        Result result = table.get(get);

        int num = 0;

        // 对获取到的结果以Cell为单位进行处理
        for(Cell cell: result.rawCells()){
            String value = Bytes.toStringBinary(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength());
            num = hexTranformToInt(value);
        }

        //关闭表格
        table.close();

        //关闭连接
        conn.close();

        return num;
    }

    /**
     * 完成HBase中十六进制数值的解析
     * @param hex
     * @return
     */
    private static int hexTranformToInt(String hex){
        String[] hexNum = hex.split("\\\\x");
        String finalNum = "0";

        for(int i = 0; i <hexNum.length; i++){
            String subNum = hexNum[i];
            // 滤除为空的字符串和为00的字符串
            if(!"00".equals(subNum) && !StringUtil.isEmpty(subNum)){
                // 当字符串以"0"开头并且不全为0，那么提取有价值字符
                if(subNum.startsWith("0")){
                    // 获得最后一个'0'的index
                    int index = subNum.lastIndexOf("0");
                    // 切分字符串
                    subNum = subNum.substring(index + 1, subNum.length());
                }
            finalNum += subNum;
            }
        }
        return Integer.parseInt(finalNum, 16);
    }
}
