@echo off
rem Starts the VSUM application that was built by Methodologist.
rem Windows: double-click this file.
setlocal
cd /d "%~dp0"

set "JAR=vsum.jar"

if not exist "%JAR%" (
  echo ERROR: '%JAR%' was not found next to this script.
  echo        Extract the whole ZIP archive before starting the application.
  goto :failed
)

set "JAVA_BIN="
where java >nul 2>&1
if not errorlevel 1 set "JAVA_BIN=java"
if not defined JAVA_BIN if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"

if not defined JAVA_BIN (
  echo ERROR: No Java runtime was found.
  echo        Install Java 17 or newer from https://adoptium.net
  echo        and start this script again.
  goto :failed
)

echo Starting the VSUM application ...
echo Close this window or press Ctrl+C to stop it.
echo.

"%JAVA_BIN%" %JAVA_OPTS% -jar "%JAR%" %*
if errorlevel 1 goto :failed

endlocal
exit /b 0

:failed
echo.
pause
endlocal
exit /b 1
