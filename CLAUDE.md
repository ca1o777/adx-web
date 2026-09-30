# ADX – Aplicativo de Xadrez

## Regras do projeto

- Trabalhe só na branch `backend`. Não faça commit na `main` e não dê push sem eu pedir.
- Siga as etapas na ordem: (1) cadastro e login, (2) registro de partidas e histórico, (3) painel, estatísticas e ranking, (4) perfil.
- No fim de cada etapa, pare: rode a aplicação, teste os endpoints e me mostre o resultado. Só comece a próxima etapa quando eu aprovar.
- Implemente apenas o que está nos requisitos e regras da documentação (RF01 a RF05, RN001 a RN005). Se quiser sugerir algo além disso, pergunte antes.
- Quando criar os dados de demonstração, anote no README o e-mail e a senha das contas de demonstração.
- O arquivo `adx.db` fica fora do Git (`.gitignore`).
- Faça commits pequenos.

## Contexto

- O frontend (HTML, CSS e JS puros) fica na raiz e é publicado no GitHub Pages a partir da `main`. O backend do 2º bimestre fica em `backend/`.
- Stack do backend: Spring Boot 3, Spring Data JPA, SQLite e Maven Wrapper. Driver `org.xerial:sqlite-jdbc` e dialeto `org.hibernate.community.dialect.SQLiteDialect` (dependência `hibernate-community-dialects`). O banco é o arquivo `backend/adx.db`.
- Uma API REST em JSON substitui os dados de `assets/js/dados.js`, e o `app.js` usa `fetch`. Em desenvolvimento, o próprio Spring Boot serve as páginas em http://localhost:8080.

## Regras de negócio

- RN001: e-mail único e senha guardada com BCrypt.
- RN003: sem login, só as telas de login e cadastro ficam acessíveis; a sessão dura até 30 dias; 3 senhas erradas seguidas bloqueiam o acesso por 15 minutos.
- RN002/RN004: uma partida tem adversário, modo (Clássico, Rápido ou Blitz), resultado (Vitória, Empate ou Derrota) e data automática, e não pode ser editada nem excluída.
- Estatísticas: aproveitamento = (vitórias + empates/2) / total de partidas. O ranking ordena por vitórias e desempata pelo aproveitamento.
- RN005: o perfil tem nome de exibição, foto JPG/PNG de até 2 MB, modo preferido e "sobre".

## Comandos (dentro de `backend/`)

- Rodar: `./mvnw spring-boot:run` (Git Bash) ou `.\mvnw.cmd spring-boot:run` (PowerShell) e abrir http://localhost:8080
- Testes: `./mvnw test`
