@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0prepare_emulator_test_files.ps1" %*
exit /b %errorlevel%
