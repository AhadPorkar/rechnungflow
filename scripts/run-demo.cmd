@echo off
cd /d "%~dp0.."
if not exist "build\rechnungflow-0.1.0.jar" (
  call scripts\build.cmd
  if errorlevel 1 exit /b 1
)
java -jar build\rechnungflow-0.1.0.jar --demo %*
