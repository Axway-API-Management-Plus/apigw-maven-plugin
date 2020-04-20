@ECHO off
SETLOCAL
SET CMD_HOME=%~dp0
CD /d "%CMD_HOME%"
SET BUILDFED="..\..\src\main\resources\scripts\buildfed.cmd"

REM Define environment variables for field value and password configuration
SET INFO_NAME=Demo
SET NEW_SERVER_PASSWORD=changeme

CALL %BUILDFED% -v -e src\gateway.env -p src\gateway.pol -c config\gateway.config.json --cert=config\gateway.certs.json --prop=config\gateway.props.json --secrets-file=config\gateway.crypt.json --secrets-passphrase=changeme -D artifact:demo-1.0.0 -F info.descr:config\description.txt --output-fed=gateway.fed --passphrase-in=changeme --passphrase-out=changed --base-dir=config/certs
ENDLOCAL
