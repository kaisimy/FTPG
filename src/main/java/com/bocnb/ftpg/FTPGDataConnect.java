package com.bocnb.ftpg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

public class FTPGDataConnect extends Thread {
    private final static Logger logger = LoggerFactory.getLogger(FTPGDataConnect.class);
    private final static int DATA_BUFFER_SIZE = 512;

    private byte buffer[] = new byte[DATA_BUFFER_SIZE];
    private final Socket[] sockets = new Socket[2];
    private boolean isInitialized;
    private final Object[] o;
    private boolean validDataConnection;

    private Object mutex = new Object();

    // Each argument may be either a Socket or a ServerSocket.
    public FTPGDataConnect(Object o1, Object o2) {
        this.o = new Object[]{o1, o2};
    }

    public void run() {
        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;
        validDataConnection = false;

        try {
            // n = 0 - Thread Copy socket 0 to socket 1
            // n = 1 - Thread Copy socket 1 to socket 0
            int n = isInitialized ? 1 : 0;
            if (!isInitialized) {
                for (int i = 0; i < 2; i++) {
                    if (o[i] instanceof ServerSocket) {
                        ServerSocket ss = (ServerSocket) o[i];
                        sockets[i] = ss.accept();
                        /*
                        if (ss == ssDataServer && !config.serverOneBindPort ||
                                ss == ssDataClient && !config.clientOneBindPort) {

                            ss.close();
                        }
                        */
                    } else {
                        sockets[i] = (Socket) o[i];
                    }
                    // Check to see if DataConnection is from same IP address
                    // as the ControlConnection.
                    if (skControlClient.getInetAddress().getHostAddress().
                            compareTo(sockets[i].getInetAddress().getHostAddress()) == 0) {

                        validDataConnection = true;
                    }
                }
                // Check to see if Data InetAddress == Control InetAddress, otherwise
                // somebody else opened a connection!  Close all the connections.
                if (config.validateDataConnection && !validDataConnection) {
                    pwDebug.println("Invalid DataConnection - not from Control Client");
                    throw new SocketException("Invalid DataConnection - not from Control Client");
                }

                isInitialized = true;

                // In some cases thread socket[0] -> socket[1] thread can
                // finish before socket[1] -> socket[0] has a chance to start,
                // so synchronize on a semaphore
                synchronized (mutex) {
                    new Thread(this).start();
                    try {
                        mutex.wait();
                    } catch (InterruptedException e) {
                        // Never occurs.
                    }
                }
            }

            bis = new BufferedInputStream(sockets[n].getInputStream());
            bos = new BufferedOutputStream(sockets[1 - n].getOutputStream());

            synchronized (mutex) {
                mutex.notify();
            }

            for (; ; ) {
                for (int i; (i = bis.read(buffer, 0, DATA_BUFFER_SIZE)) != -1; ) {
                    bos.write(buffer, 0, i);
                }
                break;
            }
            bos.flush();
        } catch (SocketException e) {
            // Socket closed.
        } catch (IOException e) {
            logger.debug("", e);
        }
        close();
    }

    public void close() {
        try {
            sockets[0].close();
        } catch (Exception e) {
        }
        try {
            sockets[1].close();
        } catch (Exception e) {
        }
    }
}