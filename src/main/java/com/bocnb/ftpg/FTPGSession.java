/*
  FTPG 代理线程
  @author Xiao Guangting 2017/9/27
 */
package com.bocnb.ftpg;

import java.io.*;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FTPGSession implements Runnable {
    private final static Logger logger = LoggerFactory.getLogger(FTPGSession.class);

    // Booleans
    private boolean userLoggedIn = false;
    private boolean connectionClosed = false;
    private boolean running;
    private boolean serverPassive;

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
    private FTPGDataConnect dataConnect;
    String sLocalClientIP;
    String sLocalServerIP;

    // Static members
    private static String CRLF = "\r\n";
    private static int SOCKET_TIMEOUT = 60000; // in milliseconds
    private static int[] portRanges; // for passive connection
    private final static int lowPort = 50000;
    private final static int highPort = 50100;
    private final static Map lastPorts = new HashMap();

    FTPGSession(Socket clientCtrlSocket, FTPGServer server) {
        this.clientCtrlSocket = clientCtrlSocket;
        this.sLocalClientIP = clientCtrlSocket.getLocalAddress().getHostAddress().replace('.', ',');
        portRanges = getPortRanges();
        this.server = server;
    }

    public void run() {
        logger.debug("Session initialized");
        try {
            outClient = new PrintStream(clientCtrlSocket.getOutputStream(), true);
            inClient = new BufferedReader(new InputStreamReader(clientCtrlSocket.getInputStream()));
            // clientCtrlSocket.setSoTimeout(SOCKET_TIMEOUT);

            try {
                showWelcome();
                loginSequence();
                running = true;
                while (true) {
                    String s = inClient.readLine();
                    if (s == null) {
                        running = false;
                        break;
                    }
                    readCommand(s);
                }
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

    private void readCommand(String fromClient) throws IOException {
        String cmd = fromClient;
        if (!userLoggedIn && (cmd.startsWith("PASV") || cmd.startsWith("PORT"))) {
            sendClient(530, "Not logged in.");
            return;
        }

        if (cmd.startsWith("PASV") || cmd.startsWith("EPSV")) {
            serverPassive = true;
            if (clientDataServerSocket != null) {
                try {
                    clientDataServerSocket.close();
                } catch (IOException ioe) {
                    // Nothing to do here
                }
            }

            if (clientDataSocket != null) {
                try {
                    clientDataSocket.close();
                } catch (IOException ioe) {
                    // Nothing to do here
                }
            }

            if (dataConnect != null) dataConnect.close();

            if (clientDataSocket == null) {
                clientDataServerSocket = getServerSocket(portRanges, clientCtrlSocket.getLocalAddress());
            }

            if (clientDataServerSocket != null) {
                int port = clientDataServerSocket.getLocalPort();
                if (cmd.startsWith("EPSV")) {
                    sendClient(229, "Entering Extended Passive Mode (|||" + port + "|");
                } else {
                    sendClient(227, "Entering Passive Mode(" + sLocalClientIP + "," +
                            (port / 256) + "," + (port % 256) + ")");
                }
                setupServerConnection(clientDataServerSocket);

            } else {
                sendClient(425, "Cannot allocate local port.");
            }
        } else if (cmd.startsWith("PORT")) {
            serverPassive = false;
            int port = parsePort(fromClient);

            if (clientDataServerSocket != null) {
                try {
                    clientDataServerSocket.close();
                } catch (IOException ioe) {
                }
                clientDataServerSocket = null;
            }
            if (clientDataSocket != null) try {
                clientDataSocket.close();
            } catch (IOException ioe) {
            }
            if (dataConnect != null) dataConnect.close();

            try {
                clientDataSocket = new Socket(clientCtrlSocket.getInetAddress(), port);
                sendClient(200, "PORT command successful.");

                setupServerConnection(clientDataSocket);
            } catch (IOException e) {
                sendClient(425, "PORT command failed - try using PASV instead.");
            }
        } else {
            sendServer(fromClient);
            readServer(true);
        }
    }

    public int[] getPortRanges() {
        if (portRanges != null && portRanges.length != 0) {
            return portRanges;
        }
        portRanges = new int[highPort - lowPort];
        for (int i = lowPort, j = 0; i < highPort; i++) {
            portRanges[j] = i;
            j++;
        }
        return portRanges;
    }

    public static int parsePort(String s) throws IOException {
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

    public static synchronized ServerSocket getServerSocket(int[] portRanges, InetAddress ia) throws IOException {
        ServerSocket ss = null;
        if (portRanges != null) {
            // Current index of portRanges array.
            int i;
            int port;

            Integer lastPort = (Integer) lastPorts.get(portRanges);
            if (lastPort != null) {
                port = lastPort.intValue();
                for (i = 0; i < portRanges.length && port > portRanges[i + 1]; i += 2) ;
                port++;
            } else {
                port = portRanges[0];
                i = 0;
            }
            for (int lastTry = -2; port != lastTry; port++) {
                if (port > portRanges[i + 1]) {
                    i = (i + 2) % portRanges.length;
                    port = portRanges[i];
                }
                if (lastTry == -1) lastTry = port;
                try {
                    ss = new ServerSocket(port, 1, ia);
                    lastPorts.put(portRanges, new Integer(port));
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
            } catch (IOException ioe) { }
        }

        if (serverPassive) {
            sendServer("PASV");

            String fromServer = readServer(false);

            int port = parsePort(fromServer);

            logger.debug("Server: " + serverCtrlSocket.getInetAddress() + ":" + port);

            serverDataSocket = new Socket(serverCtrlSocket.getInetAddress(), port);

            (dataConnect = new FTPGDataConnect(s, serverDataSocket)).start();
        } else {
            if (serverDataServerSocket == null) {
                serverDataServerSocket = getServerSocket(null, serverCtrlSocket.getLocalAddress());
            }
            if (serverDataServerSocket != null) {
                int port = serverDataServerSocket.getLocalPort();
                sendServer("PORT " + sLocalServerIP + ',' + port / 256 + ',' + (port % 256));
                readServer(false);

                (dataConnect = new FTPGDataConnect(s, serverDataServerSocket)).start();
            } else {
                sendClient("425 Cannot allocate local port.");
            }
        }
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
            server.loginComplete(new FTPGLoginEvent(clientIP, clientUsername, target.getUsername(), target.getHost() + ":" + target.getPort(), false));
            throw new RuntimeException("Login failed. terminating this thread.");
        }

        server.loginComplete(new FTPGLoginEvent(clientIP, clientUsername, target.getUsername(), target.getHost() + ":" + target.getPort(), true));
        // TODO: if password is wrong - we should terminate session, or retry the password phase.
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
            sendClient(fromServer);
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
}
