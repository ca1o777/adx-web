# ADX – Aplicativo de Xadrez (Aplicação Web)

Aplicação web do ADX. O frontend foi entregue no 1º bimestre; o backend em Java com banco de dados está sendo integrado no 2º bimestre, em etapas.

## Como executar

É preciso ter o JDK 17 ou mais novo instalado. O Maven é baixado sozinho pelo Maven Wrapper na primeira execução.

```
cd backend
./mvnw spring-boot:run        (Git Bash, Linux ou macOS)
.\mvnw.cmd spring-boot:run    (PowerShell)
```

Depois abra http://localhost:8080 no navegador. O próprio Spring Boot serve as páginas.

- O comando deve ser executado de dentro da pasta `backend/`.
- O banco de dados é o arquivo `backend/adx.db` (SQLite), criado na primeira execução. Ele não vai para o Git.
- Para rodar os testes automatizados: `./mvnw test`.

## Telas

| Arquivo | Tela | Caso de uso / Requisito |
|---|---|---|
| `index.html` | Login | UC02 · RF04 |
| `cadastro.html` | Cadastro de usuário | UC01 · RF03 |
| `painel.html` | Painel do jogador | RF02 |
| `registrar-partida.html` | Registrar partida | UC04 · RF02 |
| `historico.html` | Histórico de partidas | UC04 · RF02 |
| `partida.html` | Detalhes da partida | UC03 · RF01 |
| `ranking.html` | Ranking de jogadores | RF02 |
| `perfil.html` | Meu perfil | UC05 · RF05 |

## Estrutura

```
adx-web/
├── *.html                 páginas da aplicação
├── assets/
│   ├── css/style.css      estilos de todas as telas
│   ├── js/app.js          lógica das telas e chamadas à API (fetch)
│   ├── js/dados.js        dados de demonstração das telas ainda não integradas
│   └── fonts/             fontes Barlow (uso offline)
└── backend/               API em Java (Spring Boot 3, Spring Data JPA, SQLite)
    ├── mvnw, mvnw.cmd     Maven Wrapper
    ├── pom.xml
    └── src/
        ├── main/java/br/com/adx/   config, controller, service, repository, model, dto, seguranca, erro
        ├── main/resources/         application.properties, schema.sql (tabelas)
        └── test/java/br/com/adx/   testes de integração
```

## API

Os erros seguem o formato `{"erro": "mensagem", "campos": {"campo": "mensagem"}}`.

| Rota | O que faz |
|---|---|
| `POST /api/auth/cadastro` | Cria a conta com `{nome, email, senha}`. 201; 400 se algum campo for inválido; 409 se o e-mail já existir |
| `POST /api/auth/login` | Recebe `{email, senha}`. 200 e o cookie de sessão; 401 se o e-mail ou a senha estiverem errados; 423 se o acesso estiver bloqueado |
| `POST /api/auth/logout` | Encerra a sessão e apaga o cookie. 204 |
| `GET /api/auth/sessao` | Dados do usuário logado. 200; 401 sem login |

## Regras já implementadas no backend

- **RN001:** o e-mail é único (sem diferenciar maiúsculas) e a senha é guardada com BCrypt.
- **RN003:** sem login, só as telas de login e cadastro abrem, e a API responde 401. A sessão dura até 30 dias. Três senhas erradas seguidas bloqueiam o acesso por 15 minutos.

## Observações

- Login, cadastro e o cabeçalho das telas já usam a API. Painel, partidas, histórico, ranking e perfil ainda mostram os dados de demonstração de `assets/js/dados.js`, e serão integrados nas próximas etapas.
- "Esqueci minha senha", alteração de e-mail, redefinição de senha e exclusão de conta são apenas visuais.
