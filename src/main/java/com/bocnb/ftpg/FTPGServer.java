/*
  FTPG 服务主线程
  @author Xiao Guangting 2017/9/27
 */
package com.bocnb.ftpg;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

import org.apache.log4j.PropertyConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FTPGServer {
    private static final Logger logger = LoggerFactory.getLogger(FTPGServer.class);
    private boolean running = true;
    private HashMap<Socket, FTPGSession> sessions = new HashMap<>();
    private FTPGConfig config;

    FTPGServer(String configLocation, long cacheTime) throws SecurityException, IOException {
        String log4jConfPath = "log4j.properties";
        PropertyConfigurator.configure(log4jConfPath);
        config = new FTPGConfig(configLocation, cacheTime);
    }

    public void start(int port) throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);

        logger.info("FTPG Started");

        while (running) {
            try {
                Socket socket = serverSocket.accept();
                FTPGSession session = new FTPGSession(socket, this);
                sessions.put(socket, session);
                (new Thread(session)).start();
            } catch (IOException e) {
                logger.error("Accept failed", e);

                try { // Clean up server socket.
                    serverSocket.close();
                } catch (Exception ex) {
                    logger.error("", ex);
                }
                return;
            }
        }
        if (!serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (Exception e) {
                logger.error("", e);
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public HashMap<Socket, FTPGSession> getSessions() {
        return sessions;
    }

    public FTPGConfig getConfig() {
        return config;
    }

    public synchronized void transferComplete(FTPGTransferEvent te) {
        logger.info((te.upload ? "Uploaded: " : "Downloaded: ") + te.filename + "(" + te.bytes + "bytes" + ") by " + te.clientUser + "@" + te.clientIP + " at " + te.serverUser + "@" + te.serverHost);

    }

    public synchronized void loginComplete(FTPGLoginEvent le) {
        logger.info((le.success ? "Successful login " : "Failed login ") + le.clientUser + "@" + le.clientIP + " at " + le.serverUser + "@" + le.serverHost);
    }
}
