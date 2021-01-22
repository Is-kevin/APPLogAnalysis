package client;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 模拟手机上报日志程序
 */
public class UploadUtil {

	/**
	 * 上传日志
	 */
	public static void upload(String json, int logKind) throws Exception {
		try{
			//不同的日志类型对应不同的URL
			URL[] urls = {new URL("http://hadoop-senior01.itguigu.com:80/log-analysis04/logs/startupLogs"),
					new URL("http://hadoop-senior01.itguigu.com:80/log-analysis04/logs/pageLogs"),
					new URL("http://hadoop-senior01.itguigu.com:80/log-analysis04/logs/errorLogs")};

			URL url = urls[logKind];

			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			//设置请求方式为post
			conn.setRequestMethod("POST");

			//时间头用来供server进行时钟校对的
			conn.setRequestProperty("clientTime",System.currentTimeMillis() + "");
			//允许上传数据
			conn.setDoOutput(true);
			//设置请求的头信息,设置内容类型为JSON
			conn.setRequestProperty("Content-Type", "application/json");

			System.out.println("upload" + json);

			//输出流
			OutputStream out = conn.getOutputStream();
			out.write(json.getBytes());
			out.flush();
			out.close();
			int code = conn.getResponseCode();
			System.out.println(code);
		}
		catch (Exception e){
			e.printStackTrace();
		}
	}

}
