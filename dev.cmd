@echo off
setlocal
rem RuinHome dev launcher - dat JAVA_HOME chi cho phien nay (khong doi config he thong).
rem Usage:
rem   dev backend [mvnw args...]     mac dinh: spring-boot:run
rem   dev frontend [npm script]      mac dinh: dev     (vi: dev frontend build)

set "JAVA_HOME=C:\Users\Admin\.jdks\corretto-17.0.17"
set "TARGET=%~1"

if /I "%TARGET%"=="backend" goto :target_ok
if /I "%TARGET%"=="frontend" goto :target_ok
echo Usage: dev [backend^|frontend] [args...]
exit /b 1

:target_ok
shift
set "ARGS="

:collect
if "%~1"=="" goto :run
set ARGS=%ARGS% "%~1"
shift
goto :collect

:run
if "%TARGET%"=="backend" goto :run_backend
if "%TARGET%"=="frontend" goto :run_frontend
exit /b 1

:run_backend
if exist "%~dp0.env" for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%~dp0.env") do set "%%A=%%B"
pushd "%~dp0backend"
if "%ARGS%"=="" goto :backend_default
call mvnw.cmd%ARGS%
goto :backend_done

:backend_default
call mvnw.cmd spring-boot:run

:backend_done
set "RC=%ERRORLEVEL%"
popd
exit /b %RC%

:run_frontend
pushd "%~dp0frontend"
if "%ARGS%"=="" goto :frontend_default
call npm.cmd run%ARGS%
goto :frontend_done

:frontend_default
call npm.cmd run dev

:frontend_done
set "RC=%ERRORLEVEL%"
popd
exit /b %RC%
