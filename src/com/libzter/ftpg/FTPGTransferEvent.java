package com.libzter.ftpg;

// Event for logging
public class FTPGTransferEvent {
	
	public FTPGTransferEvent(String clientIP, String clientUser,
			String serverUser, String serverHost, String filename, int filesize,boolean upload) {
		this.clientIP = clientIP;
		this.clientUser = clientUser;
		this.serverUser = serverUser;
		this.serverHost = serverHost;
		this.filename = filename;
		this.filesize = filesize;
		this.upload = upload;
	}
	public String clientIP;
	public String clientUser;
	public String serverUser;
	public String serverHost;
	public String filename;
	public int filesize;
	public boolean upload;
}
