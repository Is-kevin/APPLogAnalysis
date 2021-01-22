package com.kevin.utils;

import java.io.*;
import java.util.Properties;

public final class PropertiesUtil
{
  private PropertiesUtil()
  {
  }

  public static Properties getProperties(String path) throws IOException
  {
    Properties props = null;
    InputStream in = null;

    try {
      in = new BufferedInputStream(new FileInputStream(new File(path)));
      props = new Properties();
      props.load(in);
      return props;
    } catch (IOException e) {
      throw e;
    } finally {
      if (in != null) {
        in.close();
      }
    }
  }
}
