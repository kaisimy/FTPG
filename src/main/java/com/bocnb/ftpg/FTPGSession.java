/*
  FTPG 代理线程
  @author Xiao Guangting 2017/9/27
 */
package com.bocnb.ftpg;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FTPGSession implements Runnable {
    private final static Logger logger = LoggerFactory.getLogger(FTPGSession.class);

    // Booleans
    private boolean userLoggedIn = false;
    private boolean connectionClosed = false;

    // Control sockets & io
    private Socket clientCtrlSocket;
    private Socket serverCtrlSocket;
    private PrintStream outClient, outServer;
    private BufferedReader inClient, inServer;

    // Data sockets & io
    private ServerSocket clientDataServerSocket, serverDataServerSocket;
    private Socket clientDataSocket, serverDataSocket;

    // Client info
    private String clientUsername;
    private String clientPassword;
    private String clientIP;

    // Others
    private FTPGServer server;
    private FTPGTarget target;

    // Static members
    private static String CRLF = "\r\n";
    private static int SOCKET_TIMEOUT = 240000; // in milliseconds

    FTPGSession(Socket clientCtrlSocket, FTPGServer server) {
        this.clientCtrlSocket = clientCtrlSocket;
        this.server = server;
    }

    public void run() {
        logger.debug("Session initialized");
        try {
            outClient = new PrintStream(clientCtrlSocket.getOutputStream(), true);
            inClient =new BufferedReader(new InputStreamReader(clientCtrlSocket.getInputStream()));
            clientCtrlSocket.setSoTimeout(SOCKET_TIMEOUT);

            try {
                showWelcome();
                loginSequence();
                commandLoop();
            } catch (Exception re) {
                // TODO: Treat the following error with some logic. There could be many reasons.
                logger.error("Fatal error in FTPG session. Terminating this client", re);
            }
        } catch (IOException e) {
            logger.error("", e);
            try {
                if (!clientCtrlSocket.isClosed()) {
                    clientCtrlSocket.close();
                }
            } catch (IOException e1) {
                logger.warn("Error closing clientCtrlSocket", e1);
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
            } else if (cmd.regionMatches(true, 0, "PORT", 0, 4)) {
                openActiveDC(cmd);
            } else if (cmd.regionMatches(true, 0, "LIST", 0, 4) ||
                    cmd.regionMatches(true, 0, "NLIST", 0, 4) ||
                    cmd.regionMatches(true, 0, "RETR ", 0, 4) ||
                    cmd.regionMatches(true, 0, "MLSD", 0, 4) || // RFC3659 extension
                    cmd.regionMatches(true, 0, "MLST", 0, 4)) { // RFC3659 extension
                // Server to Client transfer
                sendServer(cmd);
                int bytesTransferred = copyData(serverDataSocket, clientDataSocket);
                if (cmd.regionMatches(true, 0, "RETR", 0, 4)) {
                    // Just notify server that a transfer is complete, for audit/log purposes
                    server.transferComplete(new FTPGTransferEvent(clientIP, clientUsername, target.getUsername(), target.getHost() + ":" + target.getPort(), cmd.substring(5), bytesTransferred, false));
                }
                readServer(true); // should be 226 Transfer OK TODO: take action on other codes
            } else if (cmd.startsWith("STOR") ||
                    cmd.startsWith("STOU")) {
                // Server to Client transfer
                sendServer(cmd);
                String line = readServer(true);   // FIXME after this line connection closed
                int bytesTransferred = copyData(clientDataSocket, serverDataSocket);
                server.transferComplete(new FTPGTransferEvent(clientIP, clientUsername, target.getUsername(), target.getHost() + ":" + target.getPort(), cmd.substring(5), bytesTransferred, true));
                readServer(true); // should be 226 Transfer OK TODO: take action on other codes
            } else {
                sendServer(cmd);
                readServer(true);
            }
        }
        // terminate session
    }

    private void openActiveDC(String connStr) throws IOException {
        // Find out PORT
        String[] connDetails = connStr.substring(5).split(",");
        int portHigh = Integer.parseInt(connDetails[4]);
        int portLow = Integer.parseInt(connDetails[5]);
        int port = portHigh * 256 + portLow;
        String host = connDetails[0] + "." + connDetails[1] + "." + connDetails[2] + "." + connDetails[3];

        clientDataSocket = new Socket(host, port);
        logger.debug("Connected to Client data channel");
        sendClient(200, "Port command successful");

        serverDataServerSocket = new ServerSocket(0);
        port = serverDataServerSocket.getLocalPort();
        String localServerIP = serverCtrlSocket.getLocalAddress().getHostAddress();
        String toServer = "PORT " + localServerIP.replace('.' ,',') + ',' + port / 256 + ',' + (port % 256);
        sendServer(toServer);
        readServer(false);
        serverDataSocket = serverDataServerSocket.accept();
        logger.debug("Server connected on data channel from " + serverDataSocket.getLocalPort() + ":" + serverDataSocket.getLocalPort());
    }

    private void openPassiveDC() throws IOException {
        // Only allow one clientDataServerSocket accepting new passive data connections per session to avoid resource leak
        if (clientDataSocket != null && !clientDataServerSocket.isClosed()) {
            clientDataServerSocket.close();
        }
        clientDataServerSocket = new ServerSocket(0);
        int localPort = clientDataServerSocket.getLocalPort();
        String localAddress = clientCtrlSocket.getLocalAddress().getHostAddress();
        logger.debug("Passive channel listening on " + localAddress + " port " + localPort);
        // waiting for connecting client.
        logger.debug("Waiting for client to connect to clientCtrlSocket (240s)");
        // TODO: Use real address of interface!!!
        int clientPortHigh = localPort / 256;
        int clientPortLow = localPort - (clientPortHigh * 256);
        sendClient(227, "Entering Passive Mode (" + localAddress.replace('.', ',') + "," + clientPortHigh + "," + clientPortLow + ")");
        clientDataSocket = clientDataServerSocket.accept(); // TODO Actually catch Timeout exception and handle that according to specs.
        logger.debug("Client connected on data channel from " + clientDataSocket.getLocalAddress() + ":" + clientDataSocket.getLocalPort());
        // TODO: Can we close/clean up clientDataServerSocket here or is it needed by the real clientDataSocket later on?
        openServerPassiveDC();
    }

    private void openServerPassiveDC() throws IOException {
        // Connect to server
        sendServer("PASV");
        String line = readServer(false);
        if (!line.startsWith("227 ")) {
            throw new RuntimeException("Error establishing backside passive channel." + line);
        }

        String connStr = line.substring(line.indexOf('('), line.indexOf(')') + 1);
        String[] connDetails = connStr.split(",");
        if (connDetails.length != 6) {
            throw new RuntimeException("Error establishing backside passive channel." + line);
        }
        String serverHost = connDetails[0].substring(1) + "." + connDetails[1] + "." + connDetails[2] + "." + connDetails[3];
        int port = Integer.parseInt(connDetails[4]) * 256 + Integer.parseInt(connDetails[5].substring(0, connDetails[5].length() - 1));
        serverDataSocket = new Socket(serverHost, port);
        logger.debug("Connected to Server data channel on " + serverDataSocket.getLocalAddress().toString() + " : " + serverDataSocket.getLocalPort());
    }

    private void showWelcome() {
        sendClient("220-Welcome to FTPG" + CRLF + "220-Have a nice day" + CRLF + "220 :-)");
    }

    private void loginSequence() throws IOException, InterruptedException {
        String line = readClient();
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

        // We got ok credentials
        clientPassword = line.substring(5);
        clientUsername = username;

        // Look up target server and target username
        clientIP = clientCtrlSocket.getInetAddress().toString();
        clientIP = clientIP.substring(clientIP.indexOf('/') + 1);
        target = lookup(clientUsername, clientIP);
        if (target == null) {
            sendClient(421, "Service not available, closing control connection.");
            server.loginComplete(new FTPGLoginEvent(clientIP, clientUsername, "N/A", "N/A", false));
            throw new RuntimeException("No valid route found for user");
        }

        // Connect to server
        logger.debug("Connecting to backend server " + target.getUsername() + "@" + target.getHost() + ":" + target.getPort());
        try {
            serverCtrlSocket = new Socket(target.getHost(), target.getPort());
            inServer = new BufferedReader(new InputStreamReader(serverCtrlSocket.getInputStream()));
            outServer = new PrintStream(serverCtrlSocket.getOutputStream(), true);
        } catch (IOException e) {
            logger.error("Error connecting to backend server", e);
            sendClient(500, "Error connecting to target server");
            throw e;
        }

        readServer(false);  // Hello from server, just discard
        // TODO: Make sure it's 220

        sendServer("USER " + target.getUsername());
        line = readServer(false);
        if (!line.startsWith("331 ")) {
            sendClient(500, "Error login to server");
            throw new RuntimeException("Error login to server");
        }
        sendServer("PASS " + clientPassword);
        readServer(true);
        if (!userLoggedIn) {
            server.loginComplete(new FTPGLoginEvent(clientIP, clientUsername, target.getUsername(), target.getHost() + ":" + target.getPort(), false));
            throw new RuntimeException("Login failed. terminating this thread.");
        }

        server.loginComplete(new FTPGLoginEvent(clientIP, clientUsername, target.getUsername(), target.getHost() + ":" + target.getPort(), true));
        // TODO: if password is wrong - we should terminate session, or retry the password phase.
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
        sendClient(Integer.toString(code) + " " + text);
    }

    private String readClient() throws InterruptedException, IOException {
        String line;
        while ((line = inClient.readLine()) == null) {
            Thread.sleep(50L);
        }
        logger.debug("From Client: " + line);
        return line;
    }

    private void sendClient(String text) {
        outClient.print(text + CRLF);
        logger.debug("To Client: " + text);
    }

    private String readServer(boolean forwardToClient) throws IOException {
        String fromServer = inServer.readLine();
        String firstLine = fromServer;

        int response = Integer.parseInt(fromServer.substring(0, 3));
        if (fromServer.charAt(3) == '-') {
            String multiLine = fromServer.substring(0, 3) + ' ';
            while (!fromServer.startsWith(multiLine)) {
                if (forwardToClient) {
                    sendClient(fromServer);
                }
                logger.debug("From server: " + fromServer);

                fromServer = inServer.readLine();
            }
        }

        // Check for successful login.
        if (response == 230) {
            userLoggedIn = true;
        }
        // 221 Goodbye, 421 Service not available, 530 Login failed
        else if (response == 221 || response == 421 || response == 530) {
            if (userLoggedIn) {
                connectionClosed = true;
            }
            userLoggedIn = false;
        }

        if (forwardToClient || response == 110) {   // 110 Restart marker reply
            outClient.print(fromServer + CRLF);
        }
        logger.debug("From server: " + fromServer);

        if (response >= 100 && response <= 199) {
            firstLine = readServer(true);
        }

        return firstLine;
    }

    private void sendServer(String text) {
        logger.debug("To Server: " + text);
        outServer.print(text + CRLF);
    }

    Socket getClientCtrlSocket() {
        return clientCtrlSocket;
    }

}
