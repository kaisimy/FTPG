/**
 * FTPG
 * @author Petter Nordlander
 */
package com.libzter.ftpg;
import java.io.IOException;


public class FTPG {
	public static void main(String[] args) throws IOException{
		FTPGServer server = new FTPGServer();
		server.start(2121);
	}
}
