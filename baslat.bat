@echo off
rem Warbcraft yerel deneme sunucusu: cift tikla, gerisini betik halleder.
rem  - Java 21 yoksa winget ile kurar (Eclipse Temurin 21)
rem  - Paper 1.21.1 + Warbcraft + ViaVersion/ViaBackwards/ViaRewind indirir ve baslatir
rem  - Deneme modu: sezon acik, yerlesimler spawn cevresinde, 10000 dinar
chcp 65001 >nul
title Warbcraft Deneme Sunucusu
cd /d "%~dp0"

call :bul_temurin
if defined JAVA_HOME goto basla

set JMAJOR=0
where java >nul 2>nul
if not errorlevel 1 (
  for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JV=%%~v"
  for /f "delims=." %%m in ("%JV%") do set "JMAJOR=%%m"
)
if %JMAJOR% GEQ 21 if %JMAJOR% LEQ 24 goto basla

echo Java 21 bulunamadi, winget ile kuruluyor...
winget install --id EclipseAdoptium.Temurin.21.JDK -e --accept-source-agreements --accept-package-agreements
call :bul_temurin
if defined JAVA_HOME goto basla
echo.
echo Java otomatik kurulamadi. https://adoptium.net adresinden "Temurin 21 JDK" kurup bu dosyayi tekrar calistir.
pause
exit /b 1

:basla
echo.
echo  Warbcraft hazirlaniyor. Ilk acilis birkac dakika surer (Gradle, Paper ve eklentiler iner).
echo  Sunucu acilinca Minecraft'ta Cok Oyunculu ^> Dogrudan Baglan ^> localhost
echo  Yonetici olmak icin bu pencereye:  op OYUNCU_ADIN
echo  Kapatmak icin bu pencereye:        stop
echo.
call gradlew.bat :warbcraft-plugin:runServer
pause
exit /b 0

:bul_temurin
for /d %%d in ("%ProgramFiles%\Eclipse Adoptium\jdk-21*") do set "JAVA_HOME=%%d"
exit /b 0
