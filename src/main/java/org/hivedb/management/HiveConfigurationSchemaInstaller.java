package org.hivedb.management;

import org.hivedb.HiveRuntimeException;
import org.hivedb.configuration.HiveConfigurationSchema;
import org.hivedb.meta.persistence.CachingDataSourceProvider;
import org.hivedb.meta.persistence.HiveSemaphoreDao;
import org.hivedb.util.GetOpt;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;

public class HiveConfigurationSchemaInstaller implements Runnable {
  private String uri;

  public HiveConfigurationSchemaInstaller(String uri) {
    this.uri = uri;
  }

  public void run() {
    try {
      new HiveConfigurationSchema(uri).install();
      new HiveSemaphoreDao(CachingDataSourceProvider.getInstance().getDataSource(uri)).create();
    } catch (Exception e) {
      throw new HiveRuntimeException(e.getMessage(), e);
    }
  }

  public static void main(String[] argz) {
    GetOpt opt = new GetOpt();
    opt.add("host", true);
    opt.add("db", true);
    opt.add("user", true);
    opt.add("pw", true);

    Map<String, String> argMap = opt.toMap(argz);
    if (!opt.validate())
      throw new IllegalArgumentException(
        "Usage: java -jar hivedb-installer.jar -host <host> -db <database name> -user <username> -pw <password>");
    else {
      try {
        //Tickle driver
        Class.forName("com.mysql.jdbc.Driver");
        @SuppressWarnings("unused")
        Connection conn = DriverManager.getConnection(getConnectString(argMap));
      } catch (Exception e) {
        throw new HiveRuntimeException(e.getMessage());
      }
      System.out.println(getConnectString(argMap));
      new HiveConfigurationSchemaInstaller(getConnectString(argMap)).run();
    }
  }

  private static String getConnectString(Map<String, String> argMap) {
    return String.format("jdbc:mysql://%s/%s?user=%s&password=%s",
      argMap.get("host"), argMap.get("db"), argMap.get("user"), argMap.get("pw"));
  }
}
