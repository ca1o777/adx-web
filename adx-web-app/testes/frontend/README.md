# Testes de comportamento do frontend

Requer Node.js 20 ou superior. Não requer navegador, servidor Java, banco, npm ou acesso à rede.

São 101 casos: 67 existentes preservados, 19 de recuperação/validação e 15 de estrutura dos HTML. Todos passaram na versão corrigida. O executor descobre os arquivos `*.test.cjs` desta pasta.

Na raiz do projeto:

```powershell
node testes/frontend/executar.cjs
node testes/frontend/executar.cjs --report-dir testes/resultados/frontend
node testes/frontend/executar.cjs --name "segurança|sessão"
```

Também aceita o runner padrão: `node --test testes/frontend/app.test.cjs testes/frontend/correcoes.test.cjs testes/frontend/markup.test.cjs`.

O runner salva `summary.json` com totais, casos individualizados, falhas e asserções, além de `resultados.ndjson` com os eventos completos do Node. O resumo segue `{suite, summary: {run, passed, failed, errors, skipped}, cases}`. Cada caso contém identificador, descrição, estado, duração e, quando falha, detalhes. `regression_case` identifica cenários de defeitos já encontrados, sem mascarar o resultado. Retorna código 1 se há falha real, 2 se há erro de execução ou argumentos inválidos. Não transforma defeitos conhecidos em sucessos, skips ou falhas esperadas.

A suíte lê os HTMLs e executa **o `assets/js/app.js` real da aplicação**, integralmente em contexto isolado por teste. Eventos de formulário, cliques, temporizadores e respostas de fetch são controlados para reproduzir condições determinísticas. As asserções verificam conteúdo mostrado, mensagens, redirecionamento, dados enviados, validação e estado dos botões.

Cobertura: histórico, paginação, filtros, debounce, respostas fora de ordem, ranking, painel, detalhes e retrospecto, escape de texto da API, login/cadastro, registro de partidas, perfil, sessão expirada e logout. Erros HTTP e de rede são testados tanto em leituras quanto em envios.

**Limites:** `ambiente.cjs` fornece um DOM parcial; não é um navegador real. Os testes de foco conferem o elemento ativo no DOM simulado, sem comprovar navegação por teclado ou anúncio por leitor de tela no navegador. Não testa CSS, dimensões, APIs completas do DOM, execução de event handlers injetados ou cookies reais. Os casos de escape verificam texto e ausência de elementos HTML injetados no DOM simulado; não substituem validação de XSS em navegador. Os formulários e a integração de rede real precisam de testes adicionais em navegador/API. As respostas simuladas seguem os tipos de dados da API, incluindo campos numéricos como números.

Os testes de regressão expressam o comportamento desejado e passaram após as correções. A execução dos testes não altera arquivos de produção.
