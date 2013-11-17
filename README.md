FTPG is a FTP Gateway to implement reverse proxy functionallity for FTP connections.

FTPG features
- Server lookup based on username and source IP.
- Username mapping
- Password passthrough
- Cachable Config from any URL 

Todo list
- Only uses local interface for passive mode
- Configurable interface for inbound/outbound traffic
- front side extended passive/active mode
- TLS/SSL (FTPS)
- Reusable data channels (i.e. BLOCK transfer mode)
- Correct handling of subsequent commands when a channel drops prematurely
- Logging to files (i.e. logback)

Howto:

- Build: As a plain java project, it can be built in any standard java way. 
  The easiest way to package is to use the supplied maven config.
  "mvn package" and then it will create ftpg.jar in the target folder.
  
- Run:
  java -jar ftpg.jar <routes url> <cache timeout in ms> <port>
  i.e. java -jar ftpg.jar http://www.libzter.com/ftpg 60000 2121  
  or java -jar ftpg.jar file://c:/routes/sample.routes 60000 2121
  
- Edit routes file:
	a routes file contains mappings on a row basis. 
	<source username>;<src ip/ip range on cidr format>;<target user>;<target server:port>
	i.e: pelle;127.0.0.1;nisse;127.0.0.1:21

// Petter Nordlander
