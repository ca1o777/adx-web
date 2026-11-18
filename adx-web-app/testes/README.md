# Testes ampliados do ADX

A suíte reúne 251 casos: 111 Java/JUnit, 39 de integração HTTP e 101 de comportamento/estrutura do frontend. Os subcasos dos métodos HTTP não são somados como testes independentes. O script original de 24 verificações permanece como referência; a nova suíte HTTP cobre e amplia os seus cenários.

## Executar tudo

Requisitos: JDK 17 ou superior, Python 3.9 ou superior e Node.js 20 ou superior. O projeto inclui Maven Wrapper; não é necessário instalar Maven nem adicionar `mvn` ao PATH. O JDK deve estar no PATH ou indicado em `JAVA_HOME`. Na primeira execução, o Wrapper precisa de internet para baixar Maven e dependências. O frontend usa somente módulos nativos do Node, sem `npm install`.

Na raiz `adx-web-app`:

```powershell
python testes/executar_todos.py
```

No Windows também existe `executar-testes.bat`. O executor compila o projeto, roda JUnit, inicia o servidor numa porta local disponível, executa a API, encerra o servidor, remove o banco temporário e executa o frontend. Uma falha de teste não impede a execução das outras camadas. O banco `data/adx.mv.db` não é utilizado.

O resultado fica numa pasta nova em `resultados-testes/`, com `resumo.md`, `resumo.json`, relatórios JUnit/XML, JSON por caso HTTP e frontend, além dos logs de execução. Um banco temporário é criado dentro dessa pasta e removido ao encerrar o servidor.

O comando retorna 0 quando tudo passa, 1 quando há testes falhando e 2 quando há problema de infraestrutura/execução. Na versão corrigida, os 251 casos foram aprovados. As expectativas dos 203 casos anteriores foram preservadas; os 48 adicionais cobrem concorrência, recuperação após falhas, acessibilidade estrutural e atomicidade da carga de demonstração. Não há `expectedFailure` nem testes desativados.

## Executar por camada

```powershell
# Java e banco H2 em memória
.\mvnw.cmd test

# Frontend, sem Java nem servidor
node testes/frontend/executar.cjs --report-dir resultados-testes/frontend-manual

# Somente integração HTTP, com servidor e banco temporários automáticos
python testes/executar_todos.py --suites api

# Repetir a API duas vezes no mesmo banco temporário
python testes/executar_todos.py --suites api --api-runs 2
```

Há opções `--maven`, `--java` e `--node` para informar executáveis fora do PATH; por padrão `--maven` usa o Wrapper desta pasta. `--java` escolhe a JVM do servidor de testes, com fallback para `JAVA_HOME`, enquanto o Maven utiliza o `JAVA_HOME`. `--maven-repo` escolhe o cache de dependências (padrão `.ferramentas/repositorio`). `--report-dir` recebe uma pasta nova, ainda inexistente, para evitar misturar resultados antigos.

Para testar manualmente um servidor já iniciado, a suíte HTTP aceita `python testes/api/main.py --base-url http://127.0.0.1:8080 --report resultados-testes/api-manual.json`. Ela cria contas e partidas de teste, que permanecem nesse servidor. Prefira o executor geral para manter essa atividade num banco descartável.

## Cobertura

| Camada | Casos | Escopo |
|---|---:|---|
| Java | 111 | Validações e limites, Unicode, PBKDF2/sal, estatísticas, H2 real, FK/UNIQUE/CHECK, isolamento, buscas literais, perfil, ranking, paginação, autenticação concorrente e carga de demonstração atômica |
| HTTP | 39 | Rotas privadas, CSRF, cadastro/login, sessões e cookies, limites, filtros, estatísticas, perfil, isolamento e concorrência |
| Frontend | 101 | Renderização de conteúdo, escape, formulários, debounce, filtros, paginação, ordem das respostas, falhas de rede/HTTP, nova tentativa, sessão, logout, foco e associações ARIA |

Os DAOs são exercitados contra o H2 real. O frontend executa o `app.js` da aplicação e lê os HTML reais usando um DOM parcial e respostas de rede controladas. Essa camada não valida CSS, layout, leitor de tela ou execução de JavaScript injetado num navegador. A suíte também não simula a passagem real de 15 minutos ou 30 dias, não é um teste de carga e não comprova persistência após reinício.

O teste de login concorrente usa três clientes e uma barreira. Seu intercalamento interno depende do servidor; pode detectar a corrida, mas uma execução aprovada isoladamente não prova ausência dela. A repetição por até três contas aumenta a chance de encontrar o defeito.

## Regressões corrigidas

Na execução de 20/10/2026, com JDK 25 compilando para Java 17, os 251 testes passaram após corrigir:

- Overflow no deslocamento de página, em DAO e HTTP.
- Desempate do ranking usando porcentagens arredondadas.
- Bloqueio de login que perde tentativas concorrentes.
- Respostas antigas substituindo filtros novos no histórico.
- Consultas com erro que não apresentam informação suficiente ao usuário.
- Detalhes que confundem indisponibilidade com partida inexistente.
- Logout que redireciona apesar de falha no encerramento.
- Consulta de sessão que trata erro interno/rede como necessidade de novo login.

Também foram verificados erros de formulário associados aos campos, foco no primeiro inválido, data definida ao salvar e rollback integral da carga de demonstração. Os cenários de regressão permanecem ativos para detectar a reintrodução desses problemas.
