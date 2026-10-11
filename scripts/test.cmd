@echo off
setlocal
cd /d "%~dp0\.."
python scripts\test.py
exit /b %errorlevel%
