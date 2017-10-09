/*
  FTPG 通知类(登录)
  @author Xiao Guangting 2017/9/27
 */
package com.bocnb.ftpg;

class FTPGLoginEvent {

    FTPGLoginEvent(String clientIP, String clientUser,
                   String serverUser, String serverHost, boolean success) {
        this.clientIP = clientIP;
        this.clientUser = clientUser;
        this.serverUser = serverUser;
        this.serverHost = serverHost;
        this.success = success;
    }

    String clientIP;
    String clientUser;
    String serverUser;
    String serverHost;
    boolean success;
}
