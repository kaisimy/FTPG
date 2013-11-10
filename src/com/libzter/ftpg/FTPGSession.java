package com.libzter.ftpg;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;


public class FTPGSession implements Runnable{

	private Socket socket;
	private Socket serverSocket;
	private PrintWriter outClient,outServer;
	private Scanner inClient,inServer;
	
	private Socket clientPassiveDataSocket;
	private Socket serverPassiveDataSocket;
	
	private String clientUsername;
	private String clientPassword;
	private String clientIP;
	
	
	public FTPGSession(Socket socket){
		this.socket = socket;
	}
		
	@Override
	public void run() {
		System.out.println("Session initialised");
		try {
			outClient = new PrintWriter(socket.getOutputStream(), true);
			inClient = new Scanner(socket.getInputStream());
			socket.setSoTimeout(240000);
			
			try{
				showWelcome();
				loginSequence();
				commandLoop();
			}catch(Exception re){
				System.out.println("Fatal error in FTPG session. Terminating this client");
				re.printStackTrace();
				return;
			}
			
		} catch (IOException e) {
			System.out.println();
			e.printStackTrace();
			try {
				if( !socket.isClosed()){
					socket.close();
				}
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}
	
	// Main command loop
	private void commandLoop() throws IOException, InterruptedException {
		boolean running = true;
		while(running){
			// Read command from client
			String cmd = readClient();
			// Special treatment of certain commands
			if( cmd.equalsIgnoreCase("PASV")){
				openPassiveDC();
				//String resp = readServer(); // 150 <text>  -- Data channel Sok.
				//sendClient(resp+"\r\n"); // Just pass on. TODO: check that it is actually 150 and no error..
			
			}else if(cmd.startsWith("LIST") ||
					cmd.startsWith("NLIST") || 
					cmd.startsWith("RETR ") || 
					cmd.startsWith("MLSD")){
				// Server to Client transfer
				sendServer(cmd);
				String resp = readServer(); // 150 <text>  -- Data channel ok.
				sendClient(resp+"\r\n"); // Just pass on. TODO: check that it is actually 150 and no error..
				copyData(serverPassiveDataSocket,clientPassiveDataSocket);
				resp = readServer(); // should be 226 Transfer OK TODO: take action on other codes
				sendClient(resp+"\r\n");
			}else if(cmd.startsWith("STOR")||
					 cmd.startsWith("STORU")){
				// Server to Client transfer
				sendServer(cmd);
				String resp = readServer(); // 150 <text>  -- Data channel ok.
				sendClient(resp+"\r\n"); // Just pass on. TODO: check that it is actually 150 and no error..
				copyData(clientPassiveDataSocket,serverPassiveDataSocket);
				resp = readServer(); // should be 226 Transfer OK TODO: take action on other codes
				sendClient(resp+"\r\n");
			}else{
				sendServer(cmd);
				String response = readServer();
				sendClient(response);	
			}
		}
	}

	private void openPassiveDC() throws IOException {
		ServerSocket clientPassiveDataServerSocket = new ServerSocket(0);
		int localPort = clientPassiveDataServerSocket.getLocalPort();
		String localAddress = clientPassiveDataServerSocket.getLocalSocketAddress().toString();
		System.out.println("Passive channel listening on " + localAddress + " port " + localPort);
		
		// waiting for connecting client.
		System.out.println("Waiting for client to connect to socket (60s)");
		// TODO: Use real address of interface!!!
		int clientPortHigh = localPort  / 256;
		int clientPortLow = localPort - (clientPortHigh*256);
		sendClient(227,"Entering Passive Mode (127,0,0,1,"+clientPortHigh+","+clientPortLow+")");
		clientPassiveDataServerSocket.setSoTimeout(60000);
		clientPassiveDataSocket = clientPassiveDataServerSocket.accept();//TODO Actually catch Timeout exception and handle that according to specs.
		System.out.println("Client connected on data channel from " + clientPassiveDataSocket.getLocalAddress() + " : " + clientPassiveDataSocket.getLocalPort());
		openServerPassiveDC();
	}
	
	private void openActiveDC(String connStr) throws IOException{
		// Find out PORT
		
		
		clientPassiveDataSocket = new Socket();
		
		openServerPassiveDC();
	}
	
	private void openServerPassiveDC() throws UnknownHostException, IOException{
		// Connect to server
		sendServer("PASV");
		String line = readServer(); 
		if(!line.startsWith("227 ")){
			throw new RuntimeException("Error establisihing backside passive channel." + line);
		}
		
		String connStr = line.substring(line.indexOf('('),line.indexOf(')')+1);
		String[] connDetails = connStr.split(",");
		if(connDetails.length != 6){
			throw new RuntimeException("Error establisihing backside passive channel." + line);
		}
		String serverHost = connDetails[0].substring(1) + "." + connDetails[1] + "." + connDetails[2] + "." + connDetails[3];
		int port = Integer.parseInt(connDetails[4]) * 256 + Integer.parseInt(connDetails[5].substring(0, connDetails[5].length()-1));
		serverPassiveDataSocket = new Socket(serverHost,port);
		System.out.println("Connected to Server data channel on " + serverPassiveDataSocket.getLocalAddress().toString() + " : " + serverPassiveDataSocket.getLocalPort());
	}

	private void showWelcome(){
		sendClient("220-Welcome to FTPG\r\nHave a nice day\r\n220 :-)\r\n");
	}

	private void loginSequence() throws IOException, InterruptedException {
		String line = "";
		//sendClient(FTPGConstants.SERVICE_READY_CODE,FTPGConstants.SERVICE_READY_TEXT);
		line = readClient();
		while( !line.startsWith("USER ") || line.length() < 6){
			sendClient(530,"Please login with USER and PASS first");
			line = readClient();
		}
		
		String username = line.substring(5);
		// Ok. ask for password
		sendClient(331, "Password required for " + username);
		
		line = readClient();
		while(!line.startsWith("PASS ") || line.length() < 6 ){
			sendClient(331, "Password required for " + username);
			line = readClient();
		}

		String password = line.substring(5);
		
		// We got ok credentials
		clientPassword = password;
		clientUsername = username;
		
		// Look up target server and target username
		clientIP = socket.getInetAddress().toString();
		clientIP = clientIP.substring(clientIP.indexOf('/'));
		FTPGTarget target = lookup(clientUsername, clientIP);
		
		// Connect to server	
		System.out.println("Connecting to backend server " + target.getUsername() + "@" + target.getHost() + ":" + target.getPort());
		try {
			serverSocket = new Socket(target.getHost(),target.getPort());
			inServer = new Scanner(serverSocket.getInputStream());
			outServer = new PrintWriter(serverSocket.getOutputStream(), true);
		} catch (IOException e) {
			System.out.println("Error connecting to backend server");
			sendClient(500,"Error connecting to target server");
			e.printStackTrace();
			throw e;
		}
		
		// Hello from server, just discard  TODO: Make sure it's 220
		line = readServer();
		sendServer("USER " + target.getUsername());
		line = readServer();
		if( !line.startsWith("331 ")){
			sendClient(500,"Error login to server");
			throw new RuntimeException("Error login to server");
		}
		sendServer("PASS " + clientPassword);
		line = readServer();
		// Just pass to client
		sendClient(line+"\r\n");
		if( !line.startsWith("230 ")){
			throw new RuntimeException("Login failed. terminating this thread.");
		}
		// TODO: if wrong password - we should terminate session, or retry the password phase.
	}
	
	// Any data copy function between Sockets
	private void copyData(Socket sender, Socket receiver) throws IOException{
		int bytesRead = 0;
		
		try {
			InputStream is = sender.getInputStream();
			OutputStream os = receiver.getOutputStream();		
			do{ 
				byte[] bytes = new byte[256];
				bytesRead = is.read(bytes);
		//	
				System.out.println("DATA>>>" + (bytesRead > 0 ? new String(bytes) : "EOF"));
				if( bytesRead > 0){ // bytesRead = -1 means EOF
					os.write(bytes, 0, bytesRead);
				}
			}while(bytesRead >0);
		} catch (IOException e) {
			// Does not really matter, just close connections when done
		}finally{
		
			// Okay, a stream is closed. Just make sure we do close the other one.
			if( !sender.isClosed()) {
				sender.close();
			}
			if( !receiver.isClosed()){
				receiver.close();
			}
		}
	}

	private FTPGTarget lookup(String clientUsername, String clientIP) {
		// TODO Lookup in XML routing file
		return new FTPGTarget("127.0.0.1","nisse",21);
	}

	private void sendClient(int code, String text){
		sendClient(Integer.toString(code) + " " + text + "\r\n");
	}
	
	private void sendClient(String text){
		outClient.printf(text);
		System.out.println("To Client: " + text);
	}
	
	private String readClient() throws InterruptedException{
		
		while(!inClient.hasNextLine()){
			Thread.sleep(50L);
		}
		String data =  inClient.nextLine();
		System.out.println("From Client: " + data);
		return data;
	}
	
	private String readServer(){
		String first = inServer.nextLine();
		String data = first + "\r\n";
		if( first.charAt(3) == '-' ){
			String code = first.substring(0,3);
			String next = "";
			do{
				next = inServer.nextLine();
				data += next + "\r\n";
			}while( !next.substring(0,4).equals(code+" "));
		}
		System.out.println("From Server: " + data);
		return data;
	}
	
	private void sendServer(String text){
		System.out.println("To Server: " + text);
		outServer.printf(text + "\r\n");
	}
	
	public Socket getSocket() {
		return socket;
	}

	public void setSocket(Socket socket) {
		this.socket = socket;
	}
}