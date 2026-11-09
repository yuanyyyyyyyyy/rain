@echo off
chcp 65001 >nul
title rain 管理台启动器
cd /d %~dp0

echo ============================================
echo   rain 管理台启动
echo ============================================

rem ---- 1. 后端（8081 未监听才启动）----
netstat -ano | findstr ":8081" | findstr "LISTENING" >nul
if errorlevel 1 (
  if not exist "admin\server\target\rain-admin-server-0.1.0.jar" (
    echo 首次运行：构建后端 jar（约 1 分钟）...
    cd admin\server
    call mvn -q -DskipTests package
    cd ..\..
  )
  echo [1/3] 启动后端 :8081 ...
  start "rain-backend" /min cmd /c "cd /d %~dp0admin\server && java -Dserver.port=8081 -jar target\rain-admin-server-0.1.0.jar"
) else (
  echo [1/3] 后端已在运行，跳过
)

rem ---- 2. 管理台（5175/5176 未监听才启动）----
set ADMIN_STARTED=0
netstat -ano | findstr ":5175" | findstr "LISTENING" >nul
if errorlevel 1 (
  netstat -ano | findstr ":5176" | findstr "LISTENING" >nul
  if errorlevel 1 (
    echo [2/3] 启动管理台 ...
    start "rain-admin" /min cmd /c "cd /d %~dp0admin\ui && npm run dev"
    set ADMIN_STARTED=1
  ) else (
    echo [2/3] 管理台已在运行（5176）
  )
) else (
  echo [2/3] 管理台已在运行（5175）
)

echo [3/3] 等待服务就绪并打开浏览器 ...
timeout /t 9 /nobreak >nul

rem ---- 3. 检测实际端口并打开 ----
set URL=http://localhost:5175
netstat -ano | findstr ":5175" | findstr "LISTENING" >nul
if errorlevel 1 (
  set URL=http://localhost:5176
)
start %URL%

echo.
echo   管理台：%URL%   （登录 admin / admin123）
echo   后端  : http://localhost:8081
echo   公网  : https://yuanyyyyyyyyy.github.io/rain/
echo.
echo 本窗口可以关闭。后端/管理台在各自最小化窗口运行。
timeout /t 6 /nobreak >nul
