@echo off
setlocal
set PYEXE=%~1
if "%PYEXE%"=="" set PYEXE=.venv\Scripts\python.exe

set REPO=%~dp0..
cd /d %REPO%

"%PYEXE%" ml/touch/score_once_sklearn.py --model models/touch_ae_sklearn < NUL

endlocal
