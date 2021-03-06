## FTPG

FTPG is an FTP Gateway to implement reverse proxy functionality for FTP connections.

To solve a double-NAT situation(G.1), setting up this tool on a DMZ host.

```plain

                  | |                   | |   .--------------.
                  | |                   | |   | FTP Server 0 |
                  |N|              ..==>|N|==>'--------------'
..............    | |   .--------.//    | |   .--------------.
| FTP Client | ==>|A|==>|  FTPG  |=====>|A|==>| FTP Server 1 |
''''''''''''''    | |   '--------'\\    | |   '--------------'
                  |T|              ''==>|T|==>.--------------.
                  | |                   | |   | FTP Server 2 |
                  | |        DMZ        | |   '--------------' 
```

### FTPG features

- Server lookup based on username and source IP.
- Password passthrough
- Logging
- Cachable config from any URL

### Deployment

- Build: As a plain Java project, it can be built in any standard way. The easiest way to package is to use the supplied maven config. `mvn package` and then it will create `ftpg.jar` in the target folder.

- Edit config file: a routes file contains mappings on a row basis: 
    - `<source username>;<src ip/ip range on CIDR format>;<target user>;<target server:port>`
    - For example: `pelle;127.0.0.1;alice;192.168.1.100:21`

- Run: `java -jar ftpg.jar <config url> <config timeout> <local port>`
    - For example: `java -jar ftpg.jar http://127.0.0.1/ftpg 60000 2121`
    - OR: `java -jar ftpg.jar file:///c:/routes/sample.routes 60000 2121`