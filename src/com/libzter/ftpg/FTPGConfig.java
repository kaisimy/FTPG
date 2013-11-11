/**
 * FTPG 
 * @author Petter Nordlander
 */
package com.libzter.ftpg;

import java.io.IOException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

public class FTPGConfig {
	
	private String location; // URL format. Could be remote
	private long lastUpdate = 0;
	private long cacheTime;
	
	private List<FTPGRoute> routes = null;
	
	public FTPGConfig(String location,long cacheTime){
		this.location = location;
		this.cacheTime = cacheTime;
	}
	
	public synchronized List<FTPGRoute> getRoutes() throws IOException{
		long currentTime = System.currentTimeMillis();
		if( currentTime - lastUpdate > cacheTime ){
			loadRoutes();
		}
		
		return routes;
	}

	private void loadRoutes() throws IOException{
		try{
			URL configURL = new URL(location);
			//String configStr = new Scanner(configURL.openStream(),"UTF-8").useDelimiter("\\A").next();
			List<FTPGRoute> routes = new LinkedList<FTPGRoute>();
			Scanner sc = new Scanner(configURL.openStream(),"UTF-8");
			while(sc.hasNextLine()){
				String line = sc.nextLine();
				String[] details = line.split(";");
				routes.add(new FTPGRoute(details[0],details[1],details[2],details[3]));
			}
			
			this.routes = routes;
		}catch(IOException e){
			if( routes == null){
				// we really need to load them at least once
				throw e;
			}else{
				System.out.println("Could not load configuration. Using old config");
				e.printStackTrace();
			}
		}
	}
}
