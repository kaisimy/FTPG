/*
  FTPG 配置类
  @author Xiao Guangting 2017/9/27
 */
package com.bocnb.ftpg;

import java.io.IOException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class FTPGConfig {
    private static final Logger logger = LoggerFactory.getLogger(FTPGConfig.class.getName());

    private String location; // URL format. Could be remote
    private long lastUpdate = 0;
    private long cacheTime;

    private List<FTPGRoute> routes = null;

    FTPGConfig(String location, long cacheTime) {
        this.location = location;
        this.cacheTime = cacheTime;
    }

    synchronized List<FTPGRoute> getRoutes() throws IOException {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdate > cacheTime) {
            loadRoutes();
        }

        return routes;
    }

    private void loadRoutes() throws IOException {
        Scanner scanner = null;
        int lineNum = 0;
        try {
            URL configURL = new URL(location);
            List<FTPGRoute> routes = new LinkedList<>();
            scanner = new Scanner(configURL.openStream(), "UTF-8");
            while (scanner.hasNextLine()) {
                lineNum++;
                String line = scanner.nextLine();
                if (line.startsWith("#") || line.trim().equals(""))
                    continue; // Skip comment(begins with "#") and blank lines
                String[] details = line.split(";");
                routes.add(new FTPGRoute(details[0], details[1], details[2], details[3]));
            }
            lastUpdate = System.currentTimeMillis();
            this.routes = routes;
        } catch (ArrayIndexOutOfBoundsException ee) {
            logger.error("Config syntax error found in line " + lineNum, ee);
        } catch (IOException e) {
            if (routes == null) {
                // we really need to load them at least once
                throw e;
            } else {
                logger.warn("Could not load configuration. Using old config.", e);
            }
        } finally {
            try { // Avoid resource leak and clean up Scanner
                if (scanner != null) {
                    scanner.close();
                }
            } catch (Exception e) {
                logger.warn("Error closing config scanner.", e);
            }
        }
    }
}
