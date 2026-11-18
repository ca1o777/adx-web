@echo off
setlocal
call "%~dp0executar.bat" "-Dexec.args=--demo" %*
exit /b %errorlevel%
