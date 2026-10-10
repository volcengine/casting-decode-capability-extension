@echo off

rem pupnp Windows 构建脚本
rem 需要在 Windows 环境下运行，需要安装 Visual Studio 2019 或更高版本

set "SRC_DIR=%~dp0"
set "BUILD_DIR=%SRC_DIR%build_windows"
set "OUTPUT_DIR=%SRC_DIR%output_windows"

mkdir "%BUILD_DIR%" 2>nul
mkdir "%OUTPUT_DIR%" 2>nul

cd "%BUILD_DIR%"
echo =========================================
echo Building for Windows
echo =========================================

rem 清理旧的构建文件
del /q * 2>nul

rem 运行 CMake (使用 Visual Studio 2019 生成器)
cmake -G "Visual Studio 16 2019" -A x64 ^
    -DUPNP_BUILD_SHARED=ON ^
    -DUPNP_BUILD_STATIC=ON ^
    -DUPNP_BUILD_SAMPLES=ON ^
    -DBUILD_TESTING=ON ^
    -DCMAKE_BUILD_TYPE=Release ^
    "%SRC_DIR%"

rem 编译 (使用 MSBuild)
msbuild libupnp.sln /p:Configuration=Release /m

rem 拷贝输出文件
copy upnp\Release\*.dll "%OUTPUT_DIR%" 2>nul
copy ixml\Release\*.dll "%OUTPUT_DIR%" 2>nul
copy upnp\Release\*.lib "%OUTPUT_DIR%" 2>nul
copy ixml\Release\*.lib "%OUTPUT_DIR%" 2>nul

rem 拷贝头文件
mkdir "%OUTPUT_DIR%\include\upnp" 2>nul
mkdir "%OUTPUT_DIR%\include\ixml" 2>nul
xcopy "%SRC_DIR%\upnp\inc\*" "%OUTPUT_DIR%\include\upnp\" /s /e 2>nul
xcopy "%SRC_DIR%\ixml\inc\*" "%OUTPUT_DIR%\include\ixml\" /s /e 2>nul
copy "%BUILD_DIR%\upnp\inc\upnpconfig.h" "%OUTPUT_DIR%\include\upnp\" 2>nul

rem 拷贝示例程序
mkdir "%OUTPUT_DIR%\samples" 2>nul
copy upnp\sample\tv_ctrlpt\Release\*.exe "%OUTPUT_DIR%\samples" 2>nul
copy upnp\sample\tv_device\Release\*.exe "%OUTPUT_DIR%\samples" 2>nul
copy upnp\sample\tv_combo\Release\*.exe "%OUTPUT_DIR%\samples" 2>nul

echo =========================================
echo Build completed!
echo Output: %OUTPUT_DIR%
dir "%OUTPUT_DIR%"
