@echo off
REM UPnP CLI Demo 构建脚本 - Windows

set SCRIPT_DIR=%~dp0
set BUILD_DIR=%SCRIPT_DIR%build

mkdir %BUILD_DIR% 2>nul
cd %BUILD_DIR%

echo ========================================
echo Building UPnP CLI Demo
echo ========================================

cmake ..

if exist "%1 == "" (
    cmake -G "NMake Makefiles" ..
    nmake
) else (
    cmake ..
    nmake
)

echo.
echo ========================================
echo Build completed!
echo ========================================
echo.
echo Run the demo:
echo   %BUILD_DIR%\upnp_demo.exe
echo.
