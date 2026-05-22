@echo off
setlocal

set APP_NAME=WebcamWipeWindow

jpackage ^
  --name %APP_NAME% ^
  --input "package-input" ^
  --main-jar WebcamWipeWindow.jar ^
  --type app-image ^
  --dest "image-out" ^
  --icon icon.ico ^
  --win-console

echo.
echo === jpackage finished ===
pause
endlocal
