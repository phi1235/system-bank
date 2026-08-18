@echo off
echo ===================================================
echo   Bank System - Low RAM Runner (256MB per service)
echo ===================================================
set MAVEN_OPTS=-Xms64m -Xmx256m -XX:+UseSerialGC -Dspring.jmx.enabled=false

cd /d %~dp0..\backend
echo Building all microservices with low memory bounds...
call mvnw.cmd clean compile
echo Build complete. Use IntelliJ Run configurations or java -jar with -Xmx256m.
