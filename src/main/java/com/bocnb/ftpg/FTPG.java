/*
  FTPG 主类
  @author Xiao Guangting 2017/9/27

  启动FTPG代理进程, 从该主类运行main函数;
  不带参运行main时, 默认使用当前目录下的sample.routes, 监听2121端口, 配置文件缓存1分钟(即修改配置1分钟后生效);
  带参运行时包含三个参数: <配置URL> <配置文件缓存时间(毫秒)> <监听端口>
 */
package com.bocnb.ftpg;

import java.io.IOException;

public class FTPG {

    public static void main(String[] args) throws IOException {
        // Default params
        String configLocation = "file:///" + System.getProperty("user.dir") + "/sample.routes";
        long configCacheTime = 60000; // in milliseconds
        int port = 2121; // 监听21端口通常需要管理员权限

        // User specified params
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
