/*
  FTPG 通知类(传输)
  @author Xiao Guangting 2017/9/27
 */
package com.bocnb.ftpg;

class FTPGTransferEvent {
    String clientIP;
    String clientUser;
    String serverUser;
    String serverHost;
    String filename;
    boolean upload;

    FTPGTransferEvent(String clientIP, String clientUser,
                      String serverUser, String serverHost, String filename, boolean upload) {
        this.clientIP = clientIP;
        this.clientUser = clientUser;
        this.serverUser = serverUser;
        this.serverHost = serverHost;
        this.filename = filename;
        this.upload = upload;
    }
}
