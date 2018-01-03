/*
  FTPG 代理线程
  @author Xiao Guangting 2017/9/27
 */
package com.bocnb.ftpg;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FTPGSession implements Runnable {
    private final static Logger logger = LoggerFactory.getLogger(FTPGSession.class);

    // Booleans
    private boolean userLoggedIn = false;
    private boolean running;
    private boolean serverPassive;

    // Control sockets & io
    private Socket clientCtrlSocket;
    private Socket serverCtrlSocket;
    private PrintStream outClient, outServer;
    private BufferedReader inClient, inServer;

    // Data sockets
    private ServerSocket clientDataServerSocket, serverDataServerSocket;
    private Socket clientDataSocket, serverDataSocket;

    // Others
    private FTPGServer server;
    private FTPGDataConnect dataConnect;
    private FTPGLoginEvent loginEvent;
    String sLocalClientIP;
    String sLocalServerIP;

    // Static members
    private static String CRLF = "\r\n";
    private final static int lowPort = 50000;
    private final static int highPort = 50500;
    private static int TIME_OUT = 240000;

    FTPGSession(Socket clientCtrlSocket, FTPGServer server) {
        this.clientCtrlSocket = clientCtrlSocket;
        this.sLocalClientIP = clientCtrlSocket.getLocalAddress().getHostAddress().replace('.', ',');
        this.server = server;
    }

    public void run() {
        logger.debug("Session initialized");
        try {
            outClient = new PrintStream(clientCtrlSocket.getOutputStream(), true);
            inClient = new BufferedReader(new InputStreamReader(clientCtrlSocket.getInputStream()));
            this.clientCtrlSocket.setSoTimeout(TIME_OUT);

            try {
                welcome();
                running = login();
                while (running) {
                    String s = inClient.readLine();
                    if (s == null) {
                        running = false;
                    }
                    else {
                        readCommand(s);
                    }
                }
            } catch (RuntimeException rte) {
                // TODO: Treat the following error with some logic. There could be many reasons.
                logger.error("Fatal error in FTPG session, terminating this client", rte);
            } catch (Exception e) {
                // Usually time out exception
                logger.debug("Session terminated.", e);
            } finally {
                terminate();
            }
        } catch (IOException ioe) {
            logger.error("", ioe);
            terminate();
        }
    }

    private void readCommand(String fromClient) throws IOException {
        if (!userLoggedIn && (fromClient.startsWith("PASV") || fromClient.startsWith("PORT"))) {
            sendClient(530, "Not logged in.");
            return;
        }

        if (fromClient.startsWith("PASV") || fromClient.startsWith("EPSV")) {
            serverPassive = true;
            if (clientDataServerSocket != null) {
                try {
                    clientDataServerSocket.close();
                    clientDataServerSocket = null;
                } catch (IOException ioe) {
                    // Nothing to do here
                }
            }

            if (clientDataSocket != null) {
                try {
                    clientDataSocket.close();
                    clientDataSocket = null;
                } catch (IOException ioe) {
                    // Nothing to do here
                }
            }

            if (dataConnect != null) {
                dataConnect = null;
            }

            if (clientDataServerSocket == null) {
                clientDataServerSocket = getServerSocket(true, clientCtrlSocket.getLocalAddress());
            }

            if (clientDataServerSocket != null) {
                int port = clientDataServerSocket.getLocalPort();
                if (fromClient.startsWith("EPSV")) {
                    sendClient(229, "Entering Extended Passive Mode (|||" + port + "|)");
                } else {
                    sendClient(227, "Entering Passive Mode(" + sLocalClientIP + "," +
                            (port / 256) + "," + (port % 256) + ")");
                }
                setupServerConnection(clientDataServerSocket);

            } else {
                sendClient(425, "Cannot allocate local port.");
            }
        } else if (fromClient.startsWith("PORT")) {
            serverPassive = false;
            int port = parsePort(fromClient);

            if (clientDataServerSocket != null) {
                try {
                    clientDataServerSocket.close();
                } catch (IOException ioe) {
                    // Nothing to do
                }
                clientDataServerSocket = null;
            }
            if (clientDataSocket != null) try {
                clientDataSocket.close();
            } catch (IOException ioe) {
                // Nothing to do
            }
            if (dataConnect != null) {
                dataConnect = null;
            }

            try {
                clientDataSocket = new Socket(clientCtrlSocket.getInetAddress(), port);
                sendClient(200, "PORT command successful.");

                setupServerConnection(clientDataSocket);
            } catch (IOException e) {
                sendClient(425, "PORT command failed - try using PASV instead.");
            }
        } else {
            if (fromClient.startsWith("RETR") || fromClient.startsWith("REST")) {
                logger.debug("File download: " + fromClient.substring(5));
                sendServer(fromClient);
                String line = readServer(true);
                if (line.startsWith("226")) { // transfer success
                    server.transferComplete(new FTPGTransferEvent(loginEvent.clientIP, loginEvent.clientUser, loginEvent.serverUser, loginEvent.serverHost, fromClient.substring(5), false));
                }
            }
            else if (fromClient.startsWith("STOR") || fromClient.startsWith("STOU")) {
                logger.debug("File upload: " + fromClient.substring(5));
                sendServer(fromClient);
                String line = readServer(true);
                if (line.startsWith("226")) { // transfer success
                    server.transferComplete(new FTPGTransferEvent(loginEvent.clientIP, loginEvent.clientUser, loginEvent.serverUser, loginEvent.serverHost, fromClient.substring(5), true));
                }
            }
            else {
                sendServer(fromClient);
                readServer(true);
            }
        }
    }

    private static int parsePort(String s) throws IOException {
        int port;
        try {
            int i = s.lastIndexOf('(');
            int j = s.lastIndexOf(')');
            if ((i != -1) && (j != -1) && (i < j)) {
                s = s.substring(i + 1, j);
            }

            i = s.lastIndexOf(',');
            j = s.lastIndexOf(',', i - 1);

            port = Integer.parseInt(s.substring(i + 1));
            port += 256 * Integer.parseInt(s.substring(j + 1, i));
        } catch (Exception e) {
            throw new IOException();
        }
        return port;
    }

    private int parseIp(String s) {
        String[] d = s.split("\\.");
        return Integer.parseInt(d[0]) * 256 * 256 * 256 + Integer.parseInt(d[1]) * 256 * 256 +
                Integer.parseInt(d[2]) * 256 + Integer.parseInt(d[3]);
    }

    private static synchronized ServerSocket getServerSocket(Boolean bindPorts, InetAddress ia) throws IOException {
        ServerSocket ss = null;
        if (bindPorts) {
            int port;

            int count = 0;
            while (count < highPort - lowPort) {
                count++;
                Random r = new Random();
                port = r.nextInt(highPort - lowPort) + lowPort;
                try {
                    ss = new ServerSocket(port, 1, ia);
                    break;
                } catch (BindException e) {
                    // Port already in use.
                }
            }

        } else {
            ss = new ServerSocket(0, 1, ia);
        }
        return ss;
    }

    private void setupServerConnection(Object s) throws IOException {
        if (serverDataSocket != null) {
            try {
                serverDataSocket.close();
            } catch (IOException ioe) {
                // Nothing to do here
            }
        }

        if (serverPassive) {
            sendServer("PASV");

            String fromServer = readServer(false);

            int port = parsePort(fromServer);

            serverDataSocket = new Socket(serverCtrlSocket.getInetAddress(), port);

            logger.debug("Proxy -> Server: " + serverCtrlSocket.getInetAddress() + ":" + port);

            dataConnect = new FTPGDataConnect(s, serverDataSocket);
            this.server.executor.execute(dataConnect);
        } else {
            if (serverDataServerSocket == null) {
                serverDataServerSocket = getServerSocket(false, serverCtrlSocket.getLocalAddress());
            }
            if (serverDataServerSocket != null) {
                int port = serverDataServerSocket.getLocalPort();
                sendServer("PORT " + sLocalServerIP + ',' + port / 256 + ',' + (port % 256));
                readServer(false);

                dataConnect = new FTPGDataConnect(s, serverDataServerSocket);
                this.server.executor.execute(dataConnect);
            } else {
                sendClient(425, "Cannot allocate local port.");
            }
        }
    }

    /**
     * Send welcome message to client
     */
    private void welcome() {
        sendClient("220-Welcome to the desert of the real" + CRLF +
                "220-You will be disconnected after 4 minutes of inactivity" + CRLF +
                "220-Have a nice day" + CRLF +
                "220 :-)");
    }

    private boolean login() throws IOException, InterruptedException {
        String line = inClient.readLine();

        while (!line.startsWith("USER ") || line.length() < 6) {
            sendClient(530, "Please login with USER and PASS first");
            line = inClient.readLine();
            if (line == null) {
                return false;
            }
        }

        String clientUsername = line.substring(5);
        // Ok. ask for password
        sendClient(331, "Password required for " + clientUsername);

        line = inClient.readLine();
        while (!line.startsWith("PASS ") || line.length() < 6) {
            sendClient(331, "Password required for " + clientUsername);
            line = inClient.readLine();
            if (line == null) {
                return false;
            }
        }

        // We got credentials
        String clientPassword = line.substring(5);

        // Look up target server and target username
        String clientIP = clientCtrlSocket.getInetAddress().toString();
        clientIP = clientIP.substring(clientIP.indexOf('/') + 1);
        FTPGTarget target = lookup(clientUsername, clientIP);
        if (target == null) {
            sendClient(421, "Service not available, closing control connection.");
            server.loginComplete(new FTPGLoginEvent(clientIP, clientUsername,
                    "N/A", "N/A", false));
            throw new RuntimeException("No valid route found for user");
        }

        // Connect to server
        logger.debug("Connecting to backend server " +
                target.getUsername() + "@" + target.getHost() + ":" + target.getPort());
        try {
            serverCtrlSocket = new Socket(target.getHost(), target.getPort());
            inServer = new BufferedReader(new InputStreamReader(serverCtrlSocket.getInputStream()));
            outServer = new PrintStream(serverCtrlSocket.getOutputStream(), true);
            sLocalServerIP = serverCtrlSocket.getLocalAddress().getHostAddress().replace(".", ",");
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
            server.loginComplete(new FTPGLoginEvent(clientIP, clientUsername, target.getUsername(),
                    target.getHost() + ":" + target.getPort(), false));
            throw new RuntimeException("Login failed. terminating this thread.");
        }

        loginEvent = new FTPGLoginEvent(clientIP, clientUsername, target.getUsername(),
                target.getHost() + ":" + target.getPort(), true);
        server.loginComplete(loginEvent);
        return true;
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

        int ip = parseIp(ipStr);

        String[] cidrParts = cidr.split("/");
        int netBits = 0;
        if (cidrParts.length == 2) {
            netBits = Integer.parseInt(cidrParts[1]);
        }
        int net = parseIp(cidrParts[0]);
        int mask = 0xffffffff << (32 - netBits);
        return (ip & mask) == net;
    }

    private void sendClient(int code, String text) {
        sendClient(Integer.toString(code) + " " + text);
    }

    private void sendClient(String text) {
        outClient.print(text + CRLF);
        logger.trace("To Client: " + text);
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
                logger.trace("From server: " + fromServer);

                fromServer = inServer.readLine();
            }
        }

        // Check for successful login.
        if (response == 230) {
            userLoggedIn = true;
        }
        // 221 Goodbye, 421 Service not available, 530 Login failed
        else if (response == 221 || response == 421 || response == 530) {
            userLoggedIn = false;
            running = false;
        }

        if (forwardToClient || response == 110) {   // 110 Restart marker reply
            sendClient(fromServer);
        }
        logger.trace("From server: " + fromServer);

        if (response >= 100 && response <= 199) {
            firstLine = readServer(true);
        }

        return firstLine;
    }

    private void sendServer(String text) {
        logger.trace("To Server: " + text);
        outServer.print(text + CRLF);
    }

    private void terminate() {
        try {
            logger.debug("Session terminated.");
            running = false;

            if (!clientCtrlSocket.isClosed()) {
                clientCtrlSocket.close();
            }
            if (clientDataSocket != null && !clientDataSocket.isClosed()) {
                clientDataSocket.close();
            }
            if (serverCtrlSocket != null && !serverCtrlSocket.isClosed()) {
                serverCtrlSocket.close();
            }
            if (serverDataSocket != null && !serverDataSocket.isClosed()) {
                serverDataSocket.close();
            }
        } catch (IOException e) {
            // Nothing to do here
        }
    }
}
