@echo off
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle 8.11.1 is required. Install it or generate the Gradle wrapper first.
  exit /b 127
)
gradle --no-daemon --max-workers=2 :app:assembleDebug
