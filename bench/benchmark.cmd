@echo off
setlocal
cd /d "%~dp0"

if not exist "%~dp0vertex-benchmark.jar" (
    echo The file vertex-benchmark.jar is missing.
    echo Extract all files from the benchmark ZIP, and then try again.
    pause
    exit /b 2
)

set "BENCH_JAVA="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "BENCH_JAVA=%JAVA_HOME%\bin\java.exe"

if not defined BENCH_JAVA (
    for /f "delims=" %%J in ('where java.exe 2^>nul') do if not defined BENCH_JAVA set "BENCH_JAVA=%%J"
)

if not defined BENCH_JAVA if exist "%APPDATA%\.minecraft\runtime" (
    for /f "delims=" %%J in ('where /r "%APPDATA%\.minecraft\runtime" java.exe 2^>nul') do if not defined BENCH_JAVA set "BENCH_JAVA=%%J"
)

if not defined BENCH_JAVA if exist "%LOCALAPPDATA%\Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime" (
    for /f "delims=" %%J in ('where /r "%LOCALAPPDATA%\Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime" java.exe 2^>nul') do if not defined BENCH_JAVA set "BENCH_JAVA=%%J"
)

if not defined BENCH_JAVA (
    echo Java was not found.
    echo Start Minecraft 1.7.10 one time, or install Java 8 or later, and then try again.
    pause
    exit /b 2
)

"%BENCH_JAVA%" -jar "%~dp0vertex-benchmark.jar" quick -- %*
set "BENCH_EXIT=%ERRORLEVEL%"

if not "%BENCH_EXIT%"=="0" (
    echo.
    echo The benchmark did not finish. Review the error above.
    pause
)

exit /b %BENCH_EXIT%
