@echo off
title Bank System - Health Check
powershell -ExecutionPolicy Bypass -File "%~dp0check-health.ps1"
pause
