/**
 * FTPG 
 * @author Petter Nordlander
 */
package com.libzter.ftpg;

public class FTPGRoute {
	private String clientIP;
	private String clientUser;
	private String serverUser;
	private String serverHost;
	
	public FTPGRoute(String clientUser,String clientIP, String serverUser, String serverHost) {
		this.clientIP = clientIP;
		this.clientUser = clientUser;
		this.serverUser = serverUser;
		this.serverHost = serverHost;
	}
	public String getClientIP() {
		return clientIP;
	}
	public String getClientUser() {
		return clientUser;
	}
	public String getServerUser() {
		return serverUser;
	}
	public String getServerHost() {
		return serverHost;
	}
}
