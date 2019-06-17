@ECHO off
SETLOCAL
IF "%AXWAY_HOME%" == "" GOTO no_axway_home

SET JYTHON=%AXWAY_HOME%\apigateway\Win32\bin\jython.bat
IF NOT EXIST %JYTHON% GOTO no_jython

SET CMD_HOME=%~dp0
CALL %JYTHON% "%CMD_HOME%lib/buildfed.py" %*

GOTO exit

:no_axway_home
ECHO ERROR: environment variable AXWAY_HOME not set
GOTO exit

:no_jython
ECHO ERROR: Jython interpreter not found: %JYTHON%

:exit
ENDLOCAL
