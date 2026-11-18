# ADX – Aplicativo de Xadrez (Aplicação Web)

Aplicação web para registrar e acompanhar partidas de xadrez. Inclui cadastro, login, registro de partidas, histórico com filtros, estatísticas, ranking, foto de perfil, alteração de e-mail e recuperação de senha. Projeto acadêmico do curso de Análise e Desenvolvimento de Sistemas.

Esta pasta contém o frontend **e o backend**. O protótipo estático do 1º bimestre (somente telas, com dados de demonstração) continua na raiz do repositório.

## Tecnologias

| Camada | Tecnologia |
|---|---|
| Telas (visão) | HTML5, CSS3 e JavaScript puro |
| Controle | Java 17+, Jakarta Servlet 6 em servidor **Tomcat embutido** |
| Modelo e acesso a dados | Classes de modelo (records) e DAOs com JDBC |
| Banco de dados | H2 (relacional, em arquivo local `data/adx.mv.db`, sem instalação) |
| JSON | Gson |
| E-mail | Jakarta Mail com SMTP |
| Build | Maven |

## Como executar

Requisito para abrir a aplicação: **JDK 17 ou superior**. O Maven Wrapper está incluído e baixa o Maven automaticamente no primeiro uso; essa primeira execução precisa de internet.

No Windows, extraia todo o ZIP e dê dois cliques em **executar.bat**. Ele entra na pasta correta e inicia o projeto. Mantenha a janela aberta enquanto usa a aplicação.

Se preferir o PowerShell, abra-o na pasta `adx-web-app`, onde está `pom.xml`, e execute:

```powershell
.\executar.bat
```

Depois que aparecer **ADX no ar**, acesse **http://localhost:8080**. O comando não deve ser executado em `C:\Windows\System32`. Os arquivos BAT já cuidam da mudança de pasta, inclusive quando o caminho contém espaços.

Para usar os comandos Maven diretamente no PowerShell, a partir da pasta do projeto, use `./mvnw.cmd` no lugar de `mvn`. Em Linux/macOS, use `sh ./mvnw`. [Documentação oficial do Maven Wrapper](https://maven.apache.org/tools/wrapper/).

Para começar com 7 jogadores fictícios (útil para ver o ranking funcionando), use `executar-demo.bat` ou:

```powershell
.\executar-demo.bat
```

Os jogadores de demonstração não têm senha utilizável. Crie a sua conta pela tela **Criar conta**.

Opções: `./executar.bat -Dadx.porta=9090` altera a porta e `-Dadx.db=jdbc:h2:file:./outra/pasta/adx` altera o banco. Para encerrar o servidor, use Ctrl+C na janela da aplicação.

Os arquivos BAT guardam o Maven e suas dependências na pasta local `.ferramentas/`. O Maven não precisa estar instalado globalmente ou no PATH.

## E-mails de confirmação

Para alterar o e-mail, abra **Meu perfil**, informe o novo endereço e a senha atual. O ADX envia o código ao **endereço atual**. Na recuperação de senha, abra **Esqueci minha senha**, solicite o código pelo endereço cadastrado e informe uma nova senha. Os códigos valem 15 minutos, só podem ser usados uma vez e são inutilizados após cinco erros de confirmação. Os pedidos de novo código têm intervalo mínimo de um minuto. Ao concluir a recuperação, entre novamente com a nova senha.

O ADX lê a configuração SMTP do ambiente no início da execução. Reinicie o servidor após alterá-la.

| Variável | Valor |
|---|---|
| `ADX_SMTP_HOST` | Endereço do servidor SMTP |
| `ADX_SMTP_REMETENTE` | E-mail autorizado para o envio |
| `ADX_SMTP_MODO` | `starttls` (padrão), `ssl` ou `local` |
| `ADX_SMTP_PORTA` | Padrão 587 em STARTTLS ou 465 em SSL. No Mailpit use 1025. |
| `ADX_SMTP_USUARIO` e `ADX_SMTP_SENHA` | Credenciais do provedor quando exigidas. Defina apenas no ambiente do servidor. |

Para entregar mensagens a caixas externas, configure um provedor SMTP que autorize o remetente. Os modos `starttls` e `ssl` verificam o certificado do servidor e exigem conexão criptografada. Sem configuração SMTP, o ADX continua disponível para as demais funções e informa que o serviço de e-mail está indisponível.

### Apresentação local com Mailpit

Use [Mailpit](https://mailpit.axllent.org/docs/install/) para receber os e-mails de demonstração no próprio notebook. Baixe e extraia o binário Windows da [página oficial de versões](https://github.com/axllent/mailpit/releases) antes da apresentação. Depois de preparar as dependências do ADX, esta demonstração pode funcionar sem internet.

Em um PowerShell na pasta do Mailpit, execute:

```powershell
.\mailpit.exe --listen 127.0.0.1:8025 --smtp 127.0.0.1:1025 --disable-version-check --smtp-disable-rdns --allowed-hosts 127.0.0.1
```

Em outro PowerShell na pasta `adx-web-app`, inicie uma demonstração isolada do banco habitual:

```powershell
$env:ADX_SMTP_HOST = '127.0.0.1'
$env:ADX_SMTP_PORTA = '1025'
$env:ADX_SMTP_MODO = 'local'
$env:ADX_SMTP_REMETENTE = 'adx@example.test'
Remove-Item Env:ADX_SMTP_USUARIO, Env:ADX_SMTP_SENHA -ErrorAction SilentlyContinue
.\executar-demo.bat '-Dadx.endereco=127.0.0.1' '-Dadx.db=jdbc:h2:file:./data/apresentacao'
```

Abra [o ADX](http://127.0.0.1:8080) e [a caixa local](http://127.0.0.1:8025). Cadastre um e-mail fictício, como `jogador@example.test`. Faça o pedido no ADX, leia o código no Mailpit e volte ao formulário para confirmar. Quando a primeira execução terminar com sucesso, acrescente `-o` ao comando do ADX para ensaiar offline. Mantenha os dois terminais abertos durante a demonstração.

## Estrutura

```
adx-web-app/
├── pom.xml
├── src/main/java/br/edu/adx/
│   ├── Main.java              inicia o Tomcat, o filtro e os Servlets
│   ├── DadosDemo.java         jogadores fictícios (opção --demo)
│   ├── model/                 Usuario, Partida, Contagem, LinhaRanking
│   ├── dao/                   Banco (conexões e tabelas), UsuarioDao, PartidaDao
│   ├── web/                   Servlets da API e FiltroAcesso
│   └── util/                  Regras (validações), Seguranca (hash de senha), Formatos
├── src/main/webapp/           telas: *.html e assets/ (css, js, fontes)
└── testes/verificar_api.py    24 verificações das regras de negócio
```

## API

Todas as rotas respondem JSON. Rotas que alteram dados exigem o cabeçalho `X-Requested-With: ADX`.

| Método | Rota | Função | Caso de uso / regra |
|---|---|---|---|
| POST | `/api/cadastro` | Criar conta | UC01 · RN001 |
| POST | `/api/login` | Entrar | UC02 · RN003 |
| POST | `/api/logout` | Sair | UC02 |
| GET | `/api/me` | Jogador da sessão | UC02 |
| GET | `/api/painel` | Estatísticas, posição e últimas partidas | RN002 |
| GET | `/api/partidas?busca=&modo=&resultado=&pagina=` | Histórico com filtros | UC04 · RN002 |
| POST | `/api/partidas` | Registrar partida | UC04 · RN004 |
| GET | `/api/partidas/{id}` | Detalhes e retrospecto | UC03 |
| DELETE/PUT | `/api/partidas/{id}` | Sempre recusado (HTTP 405) | RN002 · RN004 |
| GET | `/api/ranking` | Ranking de jogadores | RN002 |
| GET / POST | `/api/perfil` | Consultar e atualizar perfil | UC05 · RN005 |
| GET / POST / DELETE | `/api/perfil/foto` | Consultar, enviar ou remover foto própria | UC05 · RN005 |
| POST | `/api/conta/email/solicitar` | Pedir código no e-mail atual | UC05 · RN005 |
| POST | `/api/conta/email/confirmar` | Confirmar código e mudar o e-mail | UC05 · RN005 |
| POST | `/api/conta/senha/solicitar` | Pedir código de recuperação | RN005 |
| POST | `/api/conta/senha/confirmar` | Confirmar código e redefinir senha | RN005 |

## Regras de negócio implementadas

- **RN001** – nome e sobrenome, e-mail válido e único (maiúsculas ignoradas), senha com no mínimo 8 caracteres.
- **RN002** – estatísticas e ranking calculados pelo servidor a partir do histórico; partidas não podem ser excluídas.
- **RN003** – visitantes podem acessar login, cadastro e recuperação de senha. Três senhas incorretas bloqueiam o acesso por 15 minutos. A sessão permanece válida no servidor por até 30 dias de inatividade.
- **RN004** – partida exige adversário, modo e resultado; a data é gerada pelo servidor (horário de Brasília).
- **RN005** – o jogador altera nome de exibição, modo preferido, apresentação e foto. O e-mail só muda após confirmação pelo endereço anterior. A redefinição da senha exige o código recebido no endereço cadastrado. A exclusão de conta não é oferecida na interface.

## Segurança

- Senhas guardadas com **PBKDF2-HMAC-SHA256** (120 mil iterações, sal aleatório); nunca em texto puro.
- Consultas SQL sempre parametrizadas (`PreparedStatement`).
- Cookie de sessão `HttpOnly` e `SameSite=Lax`; proteção contra requisições forjadas pelo cabeçalho `X-Requested-With`.
- Cabeçalhos `Content-Security-Policy`, `X-Frame-Options` e `X-Content-Type-Options`.
- Todo texto vindo do servidor é escapado antes de entrar no HTML.
- Resposta de login igual para e-mail inexistente e senha errada.
- Foto JPEG ou PNG de até 2 MB e 2.048 pixels por lado. O servidor verifica o formato, reduz o maior lado para até 512 pixels e salva um PNG sem os metadados originais.
- Códigos de confirmação mantidos como hash, vinculados à conta e à finalidade. A recuperação de senha revoga as sessões anteriores.

## Testes

Veja `testes/README.md` para a suíte ampliada, que reúne testes Java/JUnit, integração HTTP e comportamento do frontend. Na raiz do projeto:

```bash
python testes/executar_todos.py
```

Requer JDK 17+, Python 3.9+ e Node.js 20+. O executor usa o Maven Wrapper incluído, inicia e encerra o servidor de testes, usa um banco temporário próprio e salva relatórios em `resultados-testes/`. Não é necessário apagar `data/` nem iniciar a aplicação manualmente.

A suíte inclui testes de regressão dos fluxos básicos, da persistência de fotos e dos códigos de alteração de e-mail e recuperação de senha. O resumo distingue asserções que falharam, erros de execução e problemas de infraestrutura. A demonstração com uma caixa SMTP local permite verificar a entrega e a confirmação no navegador.

`testes/verificar_api.py` é o script original de 24 verificações, preservado como referência da entrega. Ele exige dados iniciais específicos; para novas execuções, prefira o executor ampliado.
