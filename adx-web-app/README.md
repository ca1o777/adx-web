# ADX – Aplicativo de Xadrez (Aplicação Web)

Aplicação web para registrar e acompanhar partidas de xadrez: cadastro e login de jogadores, registro de partidas, histórico com filtros, estatísticas, ranking e perfil. Projeto acadêmico do curso de Análise e Desenvolvimento de Sistemas.

Esta pasta contém o frontend **e o backend**. O protótipo estático do 1º bimestre (somente telas, com dados de demonstração) continua na raiz do repositório.

## Tecnologias

| Camada | Tecnologia |
|---|---|
| Telas (visão) | HTML5, CSS3 e JavaScript puro |
| Controle | Java 17+, Jakarta Servlet 6 em servidor **Tomcat embutido** |
| Modelo e acesso a dados | Classes de modelo (records) e DAOs com JDBC |
| Banco de dados | H2 (relacional, em arquivo local `data/adx.mv.db`, sem instalação) |
| JSON | Gson |
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

## Regras de negócio implementadas

- **RN001** – nome e sobrenome, e-mail válido e único (maiúsculas ignoradas), senha com no mínimo 8 caracteres.
- **RN002** – estatísticas e ranking calculados pelo servidor a partir do histórico; partidas não podem ser excluídas.
- **RN003** – usuário sem login só acessa login e cadastro; sessão de até 30 dias; 3 senhas incorretas bloqueiam o acesso por 15 minutos.
- **RN004** – partida exige adversário, modo e resultado; a data é gerada pelo servidor (horário de Brasília).
- **RN005** – o jogador altera nome de exibição, modo preferido e o texto "Sobre você".

## Segurança

- Senhas guardadas com **PBKDF2-HMAC-SHA256** (120 mil iterações, sal aleatório); nunca em texto puro.
- Consultas SQL sempre parametrizadas (`PreparedStatement`).
- Cookie de sessão `HttpOnly` e `SameSite=Lax`; proteção contra requisições forjadas pelo cabeçalho `X-Requested-With`.
- Cabeçalhos `Content-Security-Policy`, `X-Frame-Options` e `X-Content-Type-Options`.
- Todo texto vindo do servidor é escapado antes de entrar no HTML.
- Resposta de login igual para e-mail inexistente e senha errada.

## Pendências desta versão

Dependem de envio de e-mail ou de armazenamento de arquivos e ficam para a próxima etapa: alterar a foto do perfil, alterar o e-mail com código de confirmação e redefinir a senha por e-mail (RN005). As telas já mostram o aviso correspondente.

## Testes

Veja `testes/README.md` para a suíte ampliada, que reúne testes Java/JUnit, integração HTTP e comportamento do frontend. Na raiz do projeto:

```bash
python testes/executar_todos.py
```

Requer JDK 17+, Python 3.9+ e Node.js 20+. O executor usa o Maven Wrapper incluído, inicia e encerra o servidor de testes, usa um banco temporário próprio e salva relatórios em `resultados-testes/`. Não é necessário apagar `data/` nem iniciar a aplicação manualmente.

Na versão corrigida, os 251 casos da suíte foram aprovados. Os testes de regressão continuam ativos para detectar a reintrodução dos problemas encontrados na revisão. O resumo distingue asserções que falharam, erros de execução e problemas de infraestrutura.

`testes/verificar_api.py` é o script original de 24 verificações, preservado como referência da entrega. Ele exige dados iniciais específicos; para novas execuções, prefira o executor ampliado.
