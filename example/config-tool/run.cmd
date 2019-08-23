@ECHO off
SETLOCAL
SET CMD_HOME=%~dp0
CD /d "%CMD_HOME%"
SET BUILDFED="..\..\src\main\resources\scripts\buildfed.cmd"

CALL %BUILDFED% -e src\gateway.env -p src\gateway.pol -c config\gateway.config.json --cert=config/gateway.certs.json --prop=config/gateway.props.json --output-fed=gateway.fed --passphrase-in=changeme --passphrase-out=changed -D artifact:demo-1.0.0
ENDLOCAL
