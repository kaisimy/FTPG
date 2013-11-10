package com.libzter.ftpg;
public class FTPGTarget {

	private String host;
	private String username;
	private int port;
	
	public FTPGTarget(String host, String username, int port) {
		super();
		this.host = host;
		this.username = username;
		this.port = port;
	}
	
	public FTPGTarget(){
		
	}
	
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	public String getHost() {
		return host;
	}
	public void setHost(String host) {
		this.host = host;
	}
	public int getPort() {
		return port;
	}
	public void setPort(int port) {
		this.port = port;
	}
	
}
