package com.libzter.ftpg;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

public class FTPGServer {

	private boolean running = true;
	private HashMap<Socket,FTPGSession> sessions = new HashMap<Socket,FTPGSession>();
	
	public void start(int port) throws IOException{
		ServerSocket serverSocket = new ServerSocket(port);
		
		System.out.println("FTPG started");
		
		while(running){
			try{
				Socket socket = serverSocket.accept();
				FTPGSession session = new FTPGSession(socket);
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
}
