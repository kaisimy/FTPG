package com.libzter.ftpg;

// Event for logging
public class FTPGLoginEvent {
	
	public FTPGLoginEvent(String clientIP, String clientUser,
			String serverUser, String serverHost, boolean success) {
		this.clientIP = clientIP;
		this.clientUser = clientUser;
		this.serverUser = serverUser;
		this.serverHost = serverHost;
		this.success = success;
	}
	public String clientIP;
	public String clientUser;
	public String serverUser;
	public String serverHost;
	public boolean success;
}
