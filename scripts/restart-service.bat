@echo off
title Bank System - Restart Service
powershell -ExecutionPolicy Bypass -File "%~dp0restart-service.ps1" %1
pause
