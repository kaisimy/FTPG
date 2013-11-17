/**
 * FTPG
 * @author Petter Nordlander
 */
package com.libzter.ftpg;
import java.io.IOException;

public class FTPG {
	public static void main(String[] args) throws IOException{
		if( args.length != 3){
			System.out.println("Usage: FTPG <config url> <cache config timeout> <port>");
			System.out.println("Example: FTPG http://www.libzter.com/ftpg 60000 2121");
			System.exit(1);
		}
		long timeout = 0;
		int port = 2121;
		try{
			timeout = Long.parseLong(args[1]);
			port = Integer.parseInt(args[2]);
		}catch(NumberFormatException nfe){
			System.out.println("Cache config timeout parameter and port has to be numeric");
			System.exit(1);
		}
		FTPGServer server = new FTPGServer(args[0],timeout);
		server.start(port);
	}
}
