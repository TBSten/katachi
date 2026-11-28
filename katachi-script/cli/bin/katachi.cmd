@echo off
rem The `katachi` command on Windows: what bin/katachi is on macOS and Linux. The Scoop manifest puts this on PATH;
rem run from a bare zip, KATACHI_HOME is the directory above this one.
rem
rem Keep this file's line endings CRLF (.gitattributes): cmd.exe can mis-read a label in a file that is LF only.
setlocal EnableExtensions DisableDelayedExpansion

if not defined KATACHI_HOME for %%I in ("%~dp0..") do set "KATACHI_HOME=%%~fI"

where kotlin.bat >nul 2>nul
if errorlevel 1 goto :no_kotlin

rem `kotlin` takes every -D... / -J... on its command line for its own JVM, and reads every @... as an argfile, so katachi's
rem arguments do not go through it one by one. Not through %1 either: cmd.exe splits an unquoted --option=value at the "=".
rem The command line goes in an environment variable exactly as typed (%*), and katachi-script's main() splits it
rem (WINDOWS_COMMAND_LINE_ENV, WINDOWS_COMMAND_LINE_ARG: keep the two in sync).
set KATACHI_WINDOWS_COMMAND_LINE=%*

rem Pass on this shell's JAVA_HOME (empty when it has none: SHELL_JAVA_HOME_ENV in katachi-script).
set "KATACHI_SHELL_JAVA_HOME=%JAVA_HOME%"

rem `kotlin` runs katachi on this shell's JAVA_HOME. katachi-script is compiled for JDK 17, so on an older JDK it dies with an
rem UnsupportedClassVersionError stack trace before `katachi doctor` can say what to do. Keep 17 in sync with JDK_MIN_MAJOR.
rem No ( ) block from here to :check_done: a JAVA_HOME such as "C:\Program Files (x86)\..." would end it early.
if not defined JAVA_HOME goto :check_done
if not exist "%JAVA_HOME%\bin\java.exe" goto :check_done
set "KATACHI_JAVA_VERSION_FILE=%TEMP%\katachi-java-version-%RANDOM%.txt"
"%JAVA_HOME%\bin\java.exe" -version >"%KATACHI_JAVA_VERSION_FILE%" 2>&1
set "KATACHI_JAVA_VERSION="
for /f "usebackq tokens=1-3" %%A in (`findstr /C:" version " "%KATACHI_JAVA_VERSION_FILE%"`) do if not defined KATACHI_JAVA_VERSION set "KATACHI_JAVA_VERSION=%%~C"
del "%KATACHI_JAVA_VERSION_FILE%" >nul 2>nul
if not defined KATACHI_JAVA_VERSION goto :check_done
rem "17.0.9" is 17, "21" is 21, "1.8.0_402" is 8.
set "KATACHI_JAVA_MAJOR="
for /f "tokens=1,2 delims=.-_+" %%A in ("%KATACHI_JAVA_VERSION%") do call :set_major %%A %%B
echo %KATACHI_JAVA_MAJOR%| findstr /R "^[0-9][0-9]*$" >nul
if errorlevel 1 goto :check_done
if %KATACHI_JAVA_MAJOR% LSS 17 goto :old_jdk
:check_done

rem The code page of the console: katachi prints UTF-8, which a console in CP932 (Japanese Windows) shows as garbage.
set "KATACHI_CODEPAGE="
for /f "tokens=2 delims=:" %%C in ('chcp 2^>nul') do set "KATACHI_CODEPAGE=%%C"
if defined KATACHI_CODEPAGE set "KATACHI_CODEPAGE=%KATACHI_CODEPAGE: =%"
if defined KATACHI_CODEPAGE set "KATACHI_CODEPAGE=%KATACHI_CODEPAGE:.=%"
chcp 65001 >nul 2>nul

call kotlin.bat "%KATACHI_HOME%\katachi.main.kts" katachi-windows-command-line
set "KATACHI_STATUS=%ERRORLEVEL%"

if defined KATACHI_CODEPAGE chcp %KATACHI_CODEPAGE% >nul 2>nul
exit /b %KATACHI_STATUS%

:set_major
set "KATACHI_JAVA_MAJOR=%1"
if "%1"=="1" set "KATACHI_JAVA_MAJOR=%2"
goto :eof

:no_kotlin
>&2 echo.
>&2 echo [x] kotlin is not on PATH. Install it with: winget install JetBrains.Kotlin.Compiler
exit /b 1

:old_jdk
>&2 echo.
>&2 echo [x] JAVA_HOME (%JAVA_HOME%) is JDK %KATACHI_JAVA_MAJOR%, but katachi needs JDK 17 or later.
>&2 echo     Point JAVA_HOME at JDK 17 or later (or run set JAVA_HOME=), then run this again.
exit /b 1
