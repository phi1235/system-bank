@echo off
title Bank System - Start All 8 Microservices
powershell -ExecutionPolicy Bypass -File "%~dp0start-all-services.ps1"
pause
