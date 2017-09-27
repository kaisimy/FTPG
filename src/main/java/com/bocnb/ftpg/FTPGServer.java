/*
  FTPG 服务主线程
  @author Xiao Guangting 2017/9/27
 */
package com.bocnb.ftpg;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class FTPGServer {
    private static final Logger logger = Logger.getLogger(FTPGServer.class.getName());
    private boolean running = true;
    private HashMap<Socket, FTPGSession> sessions = new HashMap<>();
    private FTPGConfig config;
    private static final String LOG_FILENAME = "ftpg.log";

    FTPGServer(String configLocation, long cacheTime) throws SecurityException, IOException {
        configLogging(Level.FINE, LOG_FILENAME);
        config = new FTPGConfig(configLocation, cacheTime);
    }

    private void configLogging(Level level, String filename) throws SecurityException, IOException {
        Logger glbLogger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        glbLogger.setLevel(level); // TODO: Make Configurable

        FileHandler fileTxt = new FileHandler(filename);
        ConsoleHandler ch = new ConsoleHandler();
        SimpleFormatter formatter = new SimpleFormatter();
        fileTxt.setFormatter(formatter);
        glbLogger.addHandler(fileTxt);
        glbLogger.addHandler(ch);
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
                logger.log(Level.SEVERE, "Accept failed", e);

                try { // Clean up server socket.
                    serverSocket.close();
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, "", ex);
                }
                return;
            }
        }
        if (!serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "", e);
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
