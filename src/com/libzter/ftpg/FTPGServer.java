/**
 * FTPG
 * @author Petter Nordlander
 */
package com.libzter.ftpg;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

public class FTPGServer {

	private boolean running = true;
	private HashMap<Socket,FTPGSession> sessions = new HashMap<Socket,FTPGSession>();
	private FTPGConfig config;
	
	public FTPGServer(String configLocation, long cacheTime) {
		config = new FTPGConfig(configLocation,cacheTime);
	}

	public void start(int port) throws IOException{
		ServerSocket serverSocket = new ServerSocket(port);
		
		System.out.println("FTPG started");
		
		while(running){
			try{
				Socket socket = serverSocket.accept();
				FTPGSession session = new FTPGSession(socket,this);
				sessions.put(socket, session);
				(new Thread(session)).start();
			}catch(IOException e){
				System.out.println("Accept failed");
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}

	public boolean isRunning() {
		return running;
	}

	public void setRunning(boolean running) {
		this.running = running;
	}

	public HashMap<Socket,FTPGSession> getSessions() {
		return sessions;
	}
	
	public FTPGConfig getConfig(){
		return config;
	}
	
	public synchronized void transferComplete(FTPGTransferEvent te){
		System.out.println( (te.upload?"Uploaded: " : "Downloaded: ") + te.filename + "(" + te.filesize + "bytes" + ") by " + te.clientUser + "@" + te.clientIP + " at " + te.serverUser + "@" + te.serverHost);
	}
	
	public synchronized void loginComplete(FTPGLoginEvent le){
		System.out.println( (le.success?"Successful login ":"Failed login ") + le.clientUser + "@" + le.clientIP + " at " + le.serverUser + "@" + le.serverHost);
	}
}
