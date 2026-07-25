@echo off
setlocal
set "JDK17=%USERPROFILE%\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2"
if not exist "%JDK17%\bin\java.exe" (
  echo JDK 17 not found at %JDK17%
  echo Install Temurin 17 or let Gradle Foojay download it, then fix this path.
  exit /b 1
)
set "JAVA_HOME=%JDK17%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Using JAVA_HOME=%JAVA_HOME%
call gradlew.bat %*
