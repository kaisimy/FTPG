/*
  FTPG 代理线程
  @author Xiao Guangting 2017/9/27
 */
package com.bocnb.ftpg;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;


public class FTPGSession implements Runnable {
    private final static Logger logger = Logger.getLogger(FTPGSession.class.getName());
    private ServerSocket clientPassiveDataServerSocket = null;
    private Socket clientSocket;
    private Socket hostSocket;
    private PrintWriter outClient, outServer;
    private Scanner inClient, inServer;

    private Socket clientPassiveDataSocket;
    private Socket serverPassiveDataSocket;

    private String clientUsername;
    private String clientPassword;
    private String clientIP;

    private FTPGServer server;
    private FTPGTarget target;

    private final static int DATA_BUFFER_SIZE = 512;

    FTPGSession(Socket clientSocket, FTPGServer server) {
        this.clientSocket = clientSocket;
        this.server = server;
    }

    public void run() {
        logger.fine("Session initialized");
        try {
            outClient = new PrintWriter(clientSocket.getOutputStream(), true);
            inClient = new Scanner(clientSocket.getInputStream());
            clientSocket.setSoTimeout(6000);

            try {
                showWelcome();
                loginSequence();
                commandLoop();
            } catch (Exception re) {
                // TODO: Treat the following error with some logic. Could many reasons.
                logger.log(Level.SEVERE, "Fatal error in FTPG session. Terminating this client", re);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "", e);
            try {
                if (!clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e1) {
                logger.log(Level.WARNING, "Error closing clientSocket", e1);
            }
        }
    }

    // Main command loop
    private void commandLoop() throws IOException, InterruptedException {
        boolean running = true;
        while (running) {
            // Read command from client
            String cmd = readClient();
            // Special treatment of certain commands
            if (cmd.equalsIgnoreCase("PASV")) {
                openPassiveDC();
                //String resp = readServer(); // 150 <text>  -- Data channel Sok.
                //sendClient(resp+"\r\n"); // Just pass on. TODO: check that it is actually 150 and no error..
            } else if (cmd.regionMatches(true, 0, "PORT", 0, 4)) {
                openActiveDC(cmd);
            } else if (cmd.regionMatches(true, 0, "LIST", 0, 4) ||
                    cmd.regionMatches(true, 0, "NLIST", 0, 4) ||
                    cmd.regionMatches(true, 0, "RETR ", 0, 4) ||
                    cmd.regionMatches(true, 0, "MLSD", 0, 4) || // RFC3659 extension
                    cmd.regionMatches(true, 0, "MLST", 0, 4)) { // RFC3659 extension
                // Server to Client transfer
                sendServer(cmd);
                String resp = readServer(); // 150 <text>  -- Data channel ok.
                sendClient(resp + "\r\n"); // Just pass on. TODO: check that it is actually 150 and no error..
                int bytesTransfered = copyData(serverPassiveDataSocket, clientPassiveDataSocket);
                if (cmd.regionMatches(true, 0, "RETR", 0, 4)) {
                    // Just notify server that a transfer is complete, for audit/log purposes
                    server.transferComplete(new FTPGTransferEvent(clientIP, clientUsername, target.getUsername(), target.getHost() + ":" + target.getPort(), cmd.substring(5), bytesTransfered, false));
                }
                resp = readServer(); // should be 226 Transfer OK TODO: take action on other codes
                sendClient(resp + "\r\n");
            } else if (cmd.startsWith("STOR") ||
                    cmd.startsWith("STOU")) {
                // Server to Client transfer
                sendServer(cmd);
                String resp = readServer(); // 150 <text>  -- Data channel ok.
                sendClient(resp + "\r\n"); // Just pass on. TODO: check that it is actually 150 and no error..
                int bytesTransferred = copyData(clientPassiveDataSocket, serverPassiveDataSocket);
                server.transferComplete(new FTPGTransferEvent(clientIP, clientUsername, target.getUsername(), target.getHost() + ":" + target.getPort(), cmd.substring(5), bytesTransferred, true));
                resp = readServer(); // should be 226 Transfer OK TODO: take action on other codes
                sendClient(resp + "\r\n");
            } else {
                sendServer(cmd);
                String response = readServer();
                sendClient(response);
            }
        }
        // terminate session
    }

    private void openPassiveDC() throws IOException {
        // Only allow one server clientSocket accepting new passive data connections per session to avoid resource leak
        if (clientPassiveDataServerSocket != null && !clientPassiveDataServerSocket.isClosed()) {
            clientPassiveDataServerSocket.close();
        }
        clientPassiveDataServerSocket = new ServerSocket(0);
        int localPort = clientPassiveDataServerSocket.getLocalPort();
        String localAddress = clientPassiveDataServerSocket.getLocalSocketAddress().toString();
        logger.fine("Passive channel listening on " + localAddress + " port " + localPort);
        // waiting for connecting client.
        logger.fine("Waiting for client to connect to clientSocket (60s)");
        // TODO: Use real address of interface!!!
        int clientPortHigh = localPort / 256;
        int clientPortLow = localPort - (clientPortHigh * 256);
        sendClient(227, "Entering Passive Mode (127,0,0,1," + clientPortHigh + "," + clientPortLow + ")");
        clientPassiveDataServerSocket.setSoTimeout(60000);
        clientPassiveDataSocket = clientPassiveDataServerSocket.accept();//TODO Actually catch Timeout exception and handle that according to specs.
        logger.fine("Client connected on data channel from " + clientPassiveDataSocket.getLocalAddress() + ":" + clientPassiveDataSocket.getLocalPort());
        // TODO: Can we close/clean up clientPassiveDataServerSocket here or is it needed by the real clientSocket later on?
        openServerPassiveDC();
    }

    private void openActiveDC(String connStr) throws IOException {
        // Find out PORT
        String[] connDetails = connStr.substring(5).split(",");
        int portHigh = Integer.parseInt(connDetails[4]);
        int portLow = Integer.parseInt(connDetails[5]);
        int port = portHigh * 256 + portLow;
        String host = connDetails[0] + "." + connDetails[1] + "." + connDetails[2] + "." + connDetails[3];

        clientPassiveDataSocket = new Socket(host, port);
        logger.fine("Connected to Client data channel");
        sendClient(200, "Port command successful");

        openServerPassiveDC();
    }

    private void openServerPassiveDC() throws IOException {
        // Connect to server
        sendServer("PASV");
        String line = readServer();
        if (!line.startsWith("227 ")) {
            throw new RuntimeException("Error establisihing backside passive channel." + line);
        }

        String connStr = line.substring(line.indexOf('('), line.indexOf(')') + 1);
        String[] connDetails = connStr.split(",");
        if (connDetails.length != 6) {
            throw new RuntimeException("Error establisihing backside passive channel." + line);
        }
        String serverHost = connDetails[0].substring(1) + "." + connDetails[1] + "." + connDetails[2] + "." + connDetails[3];
        int port = Integer.parseInt(connDetails[4]) * 256 + Integer.parseInt(connDetails[5].substring(0, connDetails[5].length() - 1));
        serverPassiveDataSocket = new Socket(serverHost, port);
        logger.fine("Connected to Server data channel on " + serverPassiveDataSocket.getLocalAddress().toString() + " : " + serverPassiveDataSocket.getLocalPort());
    }

    private void showWelcome() {
        sendClient("220-Welcome to FTPG\r\nHave a nice day\r\n220 :-)\r\n");
    }

    private void loginSequence() throws IOException, InterruptedException {
        String line = "";
        line = readClient();
        while (!line.startsWith("USER ") || line.length() < 6) {
            sendClient(530, "Please login with USER and PASS first");
            line = readClient();
        }

        String username = line.substring(5);
        // Ok. ask for password
        sendClient(331, "Password required for " + username);

        line = readClient();
        while (!line.startsWith("PASS ") || line.length() < 6) {
            sendClient(331, "Password required for " + username);
            line = readClient();
        }

        String password = line.substring(5);

        // We got ok credentials
        clientPassword = password;
        clientUsername = username;

        // Look up target server and target username
        clientIP = clientSocket.getInetAddress().toString();
        clientIP = clientIP.substring(clientIP.indexOf('/') + 1);
        target = lookup(clientUsername, clientIP);
        if (target == null) {
            sendClient(421, "Service not available, closing control connection.");
            server.loginComplete(new FTPGLoginEvent(clientIP, clientUsername, "N/A", "N/A", false));
            throw new RuntimeException("No valid route found for user");
        }

        // Connect to server
        logger.fine("Connecting to backend server " + target.getUsername() + "@" + target.getHost() + ":" + target.getPort());
        try {
            hostSocket = new Socket(target.getHost(), target.getPort());
            inServer = new Scanner(hostSocket.getInputStream());
            outServer = new PrintWriter(hostSocket.getOutputStream(), true);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error connecting to backend server", e);
            sendClient(500, "Error connecting to target server");
            throw e;
        }

        // Hello from server, just discard  TODO: Make sure it's 220
        line = readServer();
        sendServer("USER " + target.getUsername());
        line = readServer();
        if (!line.startsWith("331 ")) {
            sendClient(500, "Error login to server");
            throw new RuntimeException("Error login to server");
        }
        sendServer("PASS " + clientPassword);
        line = readServer();
        // Just pass to client
        sendClient(line + "\r\n");
        if (!line.startsWith("230 ")) {
            server.loginComplete(new FTPGLoginEvent(clientIP, clientUsername, target.getUsername(), target.getHost() + ":" + target.getPort(), false));
            throw new RuntimeException("Login failed. terminating this thread.");
        }

        server.loginComplete(new FTPGLoginEvent(clientIP, clientUsername, target.getUsername(), target.getHost() + ":" + target.getPort(), true));
        // TODO: if wrong password - we should terminate session, or retry the password phase.
    }

    // Any data copy function between Sockets
    private int copyData(Socket sender, Socket receiver) throws IOException {
        int bytesRead = 0;
        int totalBytes = 0;
        InputStream is = null;
        OutputStream os = null;
        try {
            is = sender.getInputStream();
            os = receiver.getOutputStream();
            do {
                byte[] bytes = new byte[256];
                bytesRead = is.read(bytes);
                totalBytes += bytesRead;
                if (bytesRead > 0) { // bytesRead = -1 means EOF
                    os.write(bytes, 0, bytesRead);
                }
            } while (bytesRead > 0);
        } catch (IOException e) {
            // Does not really matter, just close connections when done
            // TODO not sure if it's reasonable to log a warning here?
        } finally {

            // TODO: Close both input and output streams to flush potential data

            // Okay, a stream is closed. Just make sure we do close the other one.
            if (!sender.isClosed()) {
                sender.close();
            }
            if (!receiver.isClosed()) {
                receiver.close();
            }
        }
        return totalBytes;
    }

    private FTPGTarget lookup(String clientUsername, String clientIP) throws IOException {
        List<FTPGRoute> routes = server.getConfig().getRoutes();
        for (FTPGRoute r : routes) {
            if (inRange(clientIP, r.getClientIP()) && clientUsername.equalsIgnoreCase(r.getClientUser())) {
                String[] hostParts = r.getServerHost().split(":");
                int port = 21;
                if (hostParts.length == 2) {
                    port = Integer.parseInt(hostParts[1]);
                }
                return new FTPGTarget(hostParts[0], r.getServerUser(), port);
            }
        }
        return null;
    }

    private boolean inRange(String ipStr, String cidr) {

        int ip = ipStrToInt(ipStr);

        String[] cidrParts = cidr.split("/");
        int netBits = 0;
        if (cidrParts.length == 2) {
            netBits = Integer.parseInt(cidrParts[1]);
        }
        int net = ipStrToInt(cidrParts[0]);
        int mask = 0xffffffff << (32 - netBits);
        return (ip & mask) == net;
    }

    private int ipStrToInt(String ip) {
        String[] d = ip.split("\\.");
        return Integer.parseInt(d[0]) * 256 * 256 * 256 + Integer.parseInt(d[1]) * 256 * 256 + Integer.parseInt(d[2]) * 256 + Integer.parseInt(d[3]);
    }

    private void sendClient(int code, String text) {
        sendClient(Integer.toString(code) + " " + text + "\r\n");
    }

    private void sendClient(String text) {
        outClient.printf(text);
        logger.info("To Client: " + text);
    }

    private String readClient() throws InterruptedException {
        while (!inClient.hasNextLine()) {
            Thread.sleep(50L);
        }
        String data = inClient.nextLine();
        logger.info("From Client: " + data);
        return data;
    }

    private String readServer() {
        String first = inServer.nextLine();
        String data = first + "\r\n";
        if (first.charAt(3) == '-') {
            String code = first.substring(0, 3);
            String next = "";
            do {
                next = inServer.nextLine();
                data += next + "\r\n";
            } while (!next.substring(0, 4).equals(code + " "));
        }
        logger.fine("From Server: " + data);
        return data;
    }

    private void sendServer(String text) {
        logger.info("To Server: " + text);
        outServer.printf(text + "\r\n");
    }

    public Socket getClientSocket() {
        return clientSocket;
    }

    public void setClientSocket(Socket socket) {
        this.clientSocket = socket;
    }

}
