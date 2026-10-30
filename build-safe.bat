@echo off
cd /d "%~dp0"
call gradlew.bat build > build.log 2>&1
set "EXIT_CODE=%ERRORLEVEL%"
echo BUILD_EXIT_CODE=%EXIT_CODE%
exit /b %EXIT_CODE%
