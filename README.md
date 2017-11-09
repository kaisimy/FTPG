FTPG is an implementation of reverse proxy functionality for FTP connections.

FTPG features
- Server lookup based on username and source IP.
- Username mapping
- Password passthrough
- Logging
- Cachable config from any URL 

To-do list
- Only uses local interface for passive mode
- Configurable interface for inbound/outbound traffic
- front side extended passive/active mode
- TLS/SSL (FTPS)
- Reusable data channels (i.e. BLOCK transfer mode)
- Correct handling of subsequent commands when a channel drops prematurely

How-to:

- Build: As a plain java project, it can be built in any standard java way. 
  The easiest way to package is to use the supplied maven config.
  "mvn package" and then it will create ftpg.jar in the target folder.
  
- Run:
  `java -jar ftpg.jar <routes url> <cache timeout in ms> <port>`
  - For example: `java -jar ftpg.jar http://www.libzter.com/ftpg 60000 2121`  
  - Or `java -jar ftpg.jar file:///c:/routes/sample.routes 60000 2121`
  
- Edit routes file:
	- A routes file contains mappings on a row basis. 
	`<source username>;<src ip/ip range on cidr format>;<target user>;<target server:port>`
	- For example: `pelle;127.0.0.1;nisse;127.0.0.1:21`

Forked from [northlander/FTPG](https://github.com/northlander/FTPG)