/*
  FTPG 配置类(单条路由)
  @author Xiao Guangting 2017/9/27
 */
package com.bocnb.ftpg;

class FTPGRoute {
    private String clientIP;
    private String clientUser;
    private String serverUser;
    private String serverHost;

    FTPGRoute(String clientUser, String clientIP, String serverUser, String serverHost) {
        this.clientIP = clientIP;
        this.clientUser = clientUser;
        this.serverUser = serverUser;
        this.serverHost = serverHost;
    }

    String getClientIP() {
        return clientIP;
    }

    String getClientUser() {
        return clientUser;
    }

    String getServerUser() {
        return serverUser;
    }

    String getServerHost() {
        return serverHost;
    }
}
