/*
    FTPG 配置类(目标主机)
    @author Xiao Guangting 2017/9/27
 */

package com.bocnb.ftpg;

class FTPGTarget {

    private String host;
    private String username;
    private int port;

    FTPGTarget(String host, String username, int port) {
        super();
        this.host = host;
        this.username = username;
        this.port = port;
    }

    String getUsername() {
        return username;
    }

    void setUsername(String username) {
        this.username = username;
    }

    String getHost() {
        return host;
    }

    void setHost(String host) {
        this.host = host;
    }

    int getPort() {
        return port;
    }

    void setPort(int port) {
        this.port = port;
    }

}
