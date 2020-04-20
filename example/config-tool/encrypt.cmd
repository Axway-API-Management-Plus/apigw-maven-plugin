@ECHO off
SETLOCAL
SET CMD_HOME=%~dp0
CD /d "%CMD_HOME%"
SET ENCRYPT="..\..\src\main\resources\scripts\encrypt.cmd"

CALL %ENCRYPT% --secrets-file=config/gateway.crypt.json --secrets-passphrase=changeme
ENDLOCAL
