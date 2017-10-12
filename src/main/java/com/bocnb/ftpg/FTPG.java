/*
  FTPG 主类
  @author Xiao Guangting 2017/9/27
 */
package com.bocnb.ftpg;

import java.io.IOException;

public class FTPG {
    public static void main(String[] args) throws IOException {
        // default parameters
        String configLocation = "file:///" + System.getProperty("user.dir") + "/sample.routes";
        long configCacheTime = 6000; // in milliseconds
        int port = 2121; // usually port 21 needs administrator/root privilege

        // user specified params
        if (args.length == 3) {
            try {
                configLocation = args[0];
                configCacheTime = Long.parseLong(args[1]);
                port = Integer.parseInt(args[2]);
            } catch (NumberFormatException nfe) {
                System.out.println("Config cache timeout and port parameters have to be numeric");
                System.exit(1);
            }
        }
        // incorrect params count
        else if (args.length != 0) {
            System.out.println("Usage: FTPG <config url> <cache config timeout> <port>");
            System.out.println("Example: FTPG file:///C:\\sample.routes 60000 2121");
            System.exit(1);
        }
        FTPGServer server = new FTPGServer(configLocation, configCacheTime);
        server.start(port);
    }
}
