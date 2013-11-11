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

Run/Build: Import into Eclipse

// Petter Nordlander
