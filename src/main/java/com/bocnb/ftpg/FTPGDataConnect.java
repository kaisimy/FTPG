package com.bocnb.ftpg;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class FTPGDataConnect implements Runnable {
    private final static int DATA_BUFFER_SIZE = 512;

    private byte buffer[] = new byte[DATA_BUFFER_SIZE];
    private final Socket[] sockets = new Socket[2];
    private boolean isInitialized;
    private final Object[] o;

    private final Object mutex = new Object();

    // Each argument may be either a Socket or a ServerSocket.
    FTPGDataConnect(Object o1, Object o2) {
        this.o = new Object[]{o1, o2};
    }

    public void run() {
        BufferedInputStream bis;
        BufferedOutputStream bos;

        try {
            // n = 0 - Thread Copy socket 0 to socket 1
            // n = 1 - Thread Copy socket 1 to socket 0
            int n = isInitialized ? 1 : 0;
            if (!isInitialized) {
                for (int i = 0; i < 2; i++) {
                    if (o[i] instanceof ServerSocket) {
                        ServerSocket ss = (ServerSocket) o[i];
                        sockets[i] = ss.accept();
                    } else {
                        sockets[i] = (Socket) o[i];
                    }
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

            int i;
            while ((i = bis.read(buffer, 0, DATA_BUFFER_SIZE)) != -1) {
                bos.write(buffer, 0, i);
            }
            bos.flush();
        } catch (IOException e) {
            // Nothing to do here
        }
        close();
    }

    void close() {
        try {
            sockets[0].close();
            sockets[1].close();
        } catch (Exception e) {
            // Nothing to do here
        }
    }
}