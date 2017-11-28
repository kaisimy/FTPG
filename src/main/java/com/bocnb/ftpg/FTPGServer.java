/*
  FTPG 服务主线程
  @author Xiao Guangting 2017/9/27
 */
package com.bocnb.ftpg;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.log4j.PropertyConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FTPGServer {
    private static final Logger logger = LoggerFactory.getLogger(FTPGServer.class);
    private FTPGConfig config;

    /**
     * Executors.newCachedThreadPool() launches new thread as needed and usage idle threads
     * idle threads will die after being unused for 60 seconds
     */
    protected ExecutorService executor;

    /**
     * Constructor for FTPGServer
     *
     * @param configLocation config location, could be any uri
     * @param cacheTime      config will apply changes when exceeding config file cache time (default 1 min)
     */
    FTPGServer(String configLocation, long cacheTime) {
        // setup Log4J properties file
        String log4jConfPath = "log4j.properties";
        PropertyConfigurator.configure(log4jConfPath);
        // setup config params
        config = new FTPGConfig(configLocation, cacheTime);

        executor = Executors.newCachedThreadPool();
    }

    /**
     * Start a server socket listening ftp client connection
     *
     * @param port listening port
     * @throws IOException throws IOException when fail to start server socket
     */
    public void start(int port) throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);

        logger.info("FTPG Started");

        while (true) {
            try {
                Socket socket = serverSocket.accept();
                FTPGSession session = new FTPGSession(socket, this);
                executor.execute(session);
            } catch (IOException e) {
                logger.error("Accept failed", e);

                try { // Clean up server socket.
                    serverSocket.close();
                } catch (Exception ex) {
                    logger.error("", ex);
                }
                break;
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
