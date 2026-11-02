@echo off
chcp 65001 >nul
echo 正在停止今日收入...
taskkill /F /IM 今日收入.exe 2>nul
echo 正在取消开机自启...
reg delete "HKCU\Software\Microsoft\Windows\CurrentVersion\Run" /v "DeskIncome" /f 2>nul
echo 正在删除桌面快捷方式...
del "%USERPROFILE%\Desktop\今日收入.lnk" 2>nul
echo 已完成清理与退出。
pause
