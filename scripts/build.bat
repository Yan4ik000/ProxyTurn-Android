@echo off
setlocal EnableExtensions EnableDelayedExpansion
for /F %%e in ('echo prompt $E^| cmd') do set "E=%%e"
set "R=%~dp0..\"
set "T=%E%[1;96m"
set "A=%E%[1;95m"
set "G=%E%[1;92m"
set "X=%E%[1;91m"
set "D=%E%[90m"
set "Z=%E%[0m"
title WDTT Build

if "%~1"=="" goto menu
if /i "%~1"=="-h" goto help
if /i "%~1"=="--help" goto help
if /i "%~1"=="-?" goto help
if /i "%~1"=="/?" goto help
if /i "%~1"=="-a" goto full_arg
if /i "%~1"=="--all" goto full_arg
if /i "%~1"=="--full" goto full_arg
if /i "%~1"=="-c" goto client_arg
if /i "%~1"=="--client" goto client_arg
if /i "%~1"=="-s" goto server_arg
if /i "%~1"=="--server" goto server_arg
if /i "%~1"=="-f" goto fast_arg
if /i "%~1"=="--fast" goto fast_arg
goto invalid

:client_arg
set "OPTION=%~1"
set "EXPECTED=arm64-v8a, armeabi-v7a, or x86_64"
set "ABI=%~2"
if "%ABI%"=="" goto missing_value
set "UNEXPECTED=%~3"
if not "%UNEXPECTED%"=="" goto unexpected_argument
if /i "%ABI%"=="arm64-v8a" goto client
if /i "%ABI%"=="armeabi-v7a" goto client
if /i "%ABI%"=="x86_64" goto client
goto invalid_android_value
:server_arg
set "OPTION=%~1"
set "EXPECTED=amd64 or arm64"
set "ABI=%~2"
if "%ABI%"=="" goto missing_value
set "UNEXPECTED=%~3"
if not "%UNEXPECTED%"=="" goto unexpected_argument
if /i "%ABI%"=="amd64" goto server
if /i "%ABI%"=="arm64" goto server
goto invalid_linux_value
:fast_arg
set "OPTION=%~1"
set "EXPECTED=arm64-v8a, armeabi-v7a, or x86_64"
set "ABI=%~2"
if "%ABI%"=="" goto missing_value
set "UNEXPECTED=%~3"
if not "%UNEXPECTED%"=="" goto unexpected_argument
if /i "%ABI%"=="arm64-v8a" goto fast
if /i "%ABI%"=="armeabi-v7a" goto fast
if /i "%ABI%"=="x86_64" goto fast
goto invalid_android_value
:full_arg
set "OPTION=%~1"
set "UNEXPECTED=%~2"
if not "%UNEXPECTED%"=="" goto unexpected_argument
goto full

:menu
set "IN_MENU=1"
:menu_loop
cls
echo.
echo  %T%WDTT BUILD%Z%
echo  %D%================================================%Z%
echo.
echo  %A%[1]%Z% Build Full Release
echo  %A%[2]%Z% Build Android Client Library
echo  %A%[3]%Z% Build Linux Server
echo  %A%[4]%Z% Fast Build
echo.
echo  %D%[0] Exit%Z%
echo.
set "C="
set /p "C=%T%Select an option:%Z% "
if "%C%"=="1" goto menu_full
if "%C%"=="2" goto menu_client
if "%C%"=="3" goto menu_server
if "%C%"=="4" goto menu_fast
if "%C%"=="0" exit /b 0
echo.
echo  %X%Invalid selection. Choose 0 to exit or a number from 1 to 4.%Z%
timeout /t 2 /nobreak >nul
goto menu_loop

:result
set "RC=!errorlevel!"
echo.
if "!RC!"=="0" (echo  %G%Completed successfully.%Z%) else (echo  %X%Build failed with exit code !RC!.%Z%)
echo.
pause
goto menu_loop

:menu_full
call :full
goto result

:menu_client
call :pick_android "Build Android Client Library"
if not defined ABI goto menu_loop
call :client
goto result

:menu_server
call :pick_linux "Build Linux Server"
if not defined ABI goto menu_loop
call :server
goto result

:menu_fast
call :pick_android "Fast Build"
if not defined ABI goto menu_loop
call :fast
goto result

:pick_android
set "ABI="
:pick_android_loop
cls
echo.
echo  %T%%~1%Z%
echo  %D%----------------------------------------%Z%
echo.
echo  %A%[1]%Z% arm64-v8a
echo  %A%[2]%Z% armeabi-v7a
echo  %A%[3]%Z% x86_64
echo.
echo  %D%[4] Back%Z%
echo.
set "C="
set /p "C=%T%Select Android ABI:%Z% "
if "%C%"=="1" set "ABI=arm64-v8a"
if "%C%"=="2" set "ABI=armeabi-v7a"
if "%C%"=="3" set "ABI=x86_64"
if "%C%"=="4" exit /b 0
if not defined ABI goto pick_android_loop
exit /b 0

:pick_linux
set "ABI="
:pick_linux_loop
cls
echo.
echo  %T%%~1%Z%
echo  %D%----------------------------------------%Z%
echo.
echo  %A%[1]%Z% linux-amd64
echo  %A%[2]%Z% linux-arm64
echo.
echo  %D%[3] Back%Z%
echo.
set "C="
set /p "C=%T%Select Linux ABI:%Z% "
if "%C%"=="1" set "ABI=amd64"
if "%C%"=="2" set "ABI=arm64"
if "%C%"=="3" exit /b 0
if not defined ABI goto pick_linux_loop
exit /b 0

:full
call :header "Full Release"
call :android arm64-v8a || exit /b 1
call :android armeabi-v7a || exit /b 1
call :android x86_64 || exit /b 1
call :linux amd64 || exit /b 1
call :linux arm64 || exit /b 1
call :apk || exit /b 1
exit /b 0

:client
call :header "Android Client Library: %ABI%"
call :android "%ABI%"
exit /b %errorlevel%
:server
call :header "Linux Server: %ABI%"
call :linux "%ABI%"
exit /b %errorlevel%
:fast
call :header "Fast Build: %ABI%"
call :android "%ABI%" || exit /b 1
call :apk "%ABI%"
exit /b %errorlevel%

:android
setlocal
set "B=%~1"
set "API=%ANDROID_NATIVE_API_LEVEL%"
if "%API%"=="" set "API=28"
if "%B%"=="arm64-v8a" (set "ARCH=arm64"&set "P=aarch64-linux-android") else if "%B%"=="armeabi-v7a" (set "ARCH=arm"&set "GOARM=7"&set "P=armv7a-linux-androideabi") else if "%B%"=="x86_64" (set "ARCH=amd64"&set "P=x86_64-linux-android") else (echo  %X%Unsupported Android ABI: %B%%Z%&endlocal&exit /b 2)
set "SDK=%ANDROID_HOME%"
if "%SDK%"=="" set "SDK=%ANDROID_SDK_ROOT%"
if "%SDK%"=="" for /f "tokens=1,* delims==" %%A in ('findstr /b "sdk.dir=" "%R%local.properties" 2^>nul') do set "SDK=%%B"
if "%SDK%"=="" if exist "%LOCALAPPDATA%\Android\Sdk\ndk" set "SDK=%LOCALAPPDATA%\Android\Sdk"
if "%SDK%"=="" for %%D in (C D E F) do if exist "%%D:\Program Files\Android\Android Studio SDK\ndk" set "SDK=%%D:\Program Files\Android\Android Studio SDK"
if "%SDK%"=="" (echo  %X%Android SDK was not found. Set ANDROID_HOME or ANDROID_SDK_ROOT.%Z%&endlocal&exit /b 1)
set "NDK=%ANDROID_NDK_HOME%"
if "%NDK%"=="" set "NDK=%ANDROID_NDK_ROOT%"
if "%NDK%"=="" for /f "delims=" %%D in ('dir /b /ad "%SDK%\ndk" 2^>nul ^| sort') do set "NDK=%SDK%\ndk\%%D"
set "CLANG=%NDK%\toolchains\llvm\prebuilt\windows-x86_64\bin\%P%%API%-clang.cmd"
if not exist "%CLANG%" (echo  %X%Android NDK compiler was not found.%Z%&endlocal&exit /b 1)
set "W=%SystemRoot%\Temp\wdtt-clang-%RANDOM%-%RANDOM%.cmd"
> "%W%" echo @echo off
>> "%W%" echo call "%CLANG%" %%*
set "GOOS=android"
set "GOARCH=%ARCH%"
set "CGO_ENABLED=1"
set "CC=%W%"
set "OUT=%R%app\src\main\jniLibs\%B%"
if not exist "%OUT%" mkdir "%OUT%"
pushd "%R%app\src\main\assets\android-client"
go mod download && go build -ldflags="-s -w -checklinkname=0" -trimpath -o "%OUT%\libclient.so" .
set "RC=!errorlevel!"
popd
del "%W%" >nul 2>nul
if not "!RC!"=="0" (endlocal&exit /b 1)
echo  %G%Android %B%: OK%Z%
endlocal&exit /b 0

:linux
setlocal
set "B=%~1"
if "%B%"=="amd64" (set "N=server") else if "%B%"=="arm64" (set "N=server-arm64") else (echo  %X%Unsupported Linux ABI: %B%%Z%&endlocal&exit /b 2)
set "GOOS=linux"
set "GOARCH=%B%"
set "CGO_ENABLED=0"
pushd "%R%app\src\main\assets\linux-server"
go build -ldflags="-s -w" -o "%R%app\src\main\assets\%N%" .
set "RC=!errorlevel!"
popd
if not "!RC!"=="0" (endlocal&exit /b 1)
echo  %G%Linux %B%: OK%Z%
endlocal&exit /b 0

:apk
setlocal
set "FAST=%~1"
if "%FAST%"=="" for %%B in (arm64-v8a armeabi-v7a x86_64) do if not exist "%R%app\src\main\jniLibs\%%B\libclient.so" (echo  %X%Missing Android library: %%B%Z%&endlocal&exit /b 1)
pushd "%R%"
echo.
echo  %T%Assembling signed Release APKs...%Z%
call gradlew assembleRelease --no-daemon
if errorlevel 1 goto apk_fail
echo.
if not exist "app\release" mkdir "app\release"
set "APK_DIR=app\build\outputs\apk\release"
if "%FAST%"=="" (
    call :copy app-universal-release.apk WDTT-universal.apk || goto apk_fail
    call :copy app-arm64-v8a-release.apk WDTT-arm64-v8a.apk || goto apk_fail
    call :copy app-armeabi-v7a-release.apk WDTT-armeabi-v7a.apk || goto apk_fail
    call :copy app-x86_64-release.apk WDTT-x86_64.apk || goto apk_fail
) else call :copy app-%FAST%-release.apk WDTT-%FAST%-fast-test.apk || goto apk_fail
popd
echo  %G%Signed APK output: app\release%Z%
endlocal&exit /b 0
:apk_fail
popd
endlocal&exit /b 1

:copy
if not exist "%APK_DIR%\%~1" (echo  %X%APK was not generated: %~1%Z%&exit /b 1)
copy /Y "%APK_DIR%\%~1" "app\release\%~2" >nul
for %%F in ("app\release\%~2") do echo  %G%[OK]%Z% %~2 %D%[%%~zF bytes]%Z%
exit /b 0

:header
cls
echo.
echo  %T%WDTT BUILD%Z% %D%/%Z% %A%%~1%Z%
echo  %D%================================================%Z%
echo.
exit /b 0

:missing_value
echo %X%Error: option "%OPTION%" requires a value. Expected: %EXPECTED%.%Z%
goto usage_error
:unexpected_argument
echo %X%Error: unexpected argument "%UNEXPECTED%" after option "%OPTION%".%Z%
goto usage_error
:invalid
echo %X%Error: unknown option or command "%~1".%Z%
:usage_error
call :usage
exit /b 2
:help
call :usage
exit /b 0
:invalid_android_value
echo %X%Error: invalid value "%ABI%" for option "%OPTION%". Expected: arm64-v8a, armeabi-v7a, or x86_64.%Z%
goto usage_error
:invalid_linux_value
echo %X%Error: invalid value "%ABI%" for option "%OPTION%". Expected: amd64 or arm64.%Z%
goto usage_error
:usage
echo.
echo %T%Usage%Z%
echo   build.bat %A%-a%Z% ^| %A%--all%Z%
echo   build.bat %A%-c%Z% ^| %A%--client%Z% ^<arm64-v8a^|armeabi-v7a^|x86_64^>
echo   build.bat %A%-s%Z% ^| %A%--server%Z% ^<amd64^|arm64^>
echo   build.bat %A%-f%Z% ^| %A%--fast%Z% ^<arm64-v8a^|armeabi-v7a^|x86_64^>
echo   build.bat %A%-h%Z% ^| %A%--help%Z%
echo.
exit /b 0
