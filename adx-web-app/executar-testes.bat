@echo off
setlocal
cd /d "%~dp0"
if errorlevel 1 exit /b 1
where python.exe >nul 2>&1
if not errorlevel 1 goto :python
where py.exe >nul 2>&1
if not errorlevel 1 goto :py
echo Python 3.9 ou superior nao encontrado. Os testes tambem precisam de Node.js 20 e JDK 17.
if not defined ADX_SEM_PAUSA pause
exit /b 2
:python
python testes\executar_todos.py %*
goto :resultado
:py
py -3 testes\executar_todos.py %*
:resultado
set "ADX_RESULTADO=%errorlevel%"
if not "%ADX_RESULTADO%"=="0" (
  echo.
  echo Confira os resultados e erros acima.
  if not defined ADX_SEM_PAUSA pause
)
exit /b %ADX_RESULTADO%
