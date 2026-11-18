@echo off
setlocal
cd /d "%~dp0"
if errorlevel 1 (
  echo Nao foi possivel abrir a pasta do projeto.
  if not defined ADX_SEM_PAUSA pause
  exit /b 1
)
if not defined MAVEN_USER_HOME set "MAVEN_USER_HOME=%~dp0.ferramentas\maven"
echo ADX - iniciando a aplicacao
echo Na primeira execucao, aguarde o download do Maven e das dependencias.
echo Quando aparecer "ADX no ar", abra o endereco informado no terminal.
echo.
call "%~dp0mvnw.cmd" -B -ntp "-Dmaven.repo.local=%~dp0.ferramentas\repositorio" compile exec:java %*
set "ADX_RESULTADO=%errorlevel%"
if not "%ADX_RESULTADO%"=="0" (
  echo.
  echo Nao foi possivel iniciar o ADX. Confira o erro acima.
  echo O projeto precisa de JDK 17 ou superior e de internet no primeiro uso.
  if not defined ADX_SEM_PAUSA pause
)
exit /b %ADX_RESULTADO%
