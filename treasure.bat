@echo off
git pull origin main
git checkout treasure/aurascore-ui 2>nul || git checkout -b treasure/aurascore-ui
git add .
git commit -m "feat: Treasure UI update"
git push origin treasure/aurascore-ui
pause
