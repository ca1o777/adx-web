# Testes HTTP do ADX

Esta pasta amplia os 24 cenários originais para **39 métodos unittest**, incluindo subcasos de limites, segurança e concorrência. Usa somente a biblioteca padrão do Python 3.9 ou superior.

O servidor precisa estar iniciado antes. Para uma verificação sem interferência em dados reais, use uma cópia do projeto iniciada com banco temporário isolado, conforme o executor geral da pasta `testes`. Esta suíte **não apaga dados**: cria contas com UUID, sem depender de demonstração, nomes fixos ou quantidade global de usuários. Pode rodar mais de uma vez no mesmo banco. As contas e partidas criadas permanecem nele; por isso prefira sempre o banco temporário.

```powershell
python testes/api/main.py --base-url http://127.0.0.1:8080 --report testes/resultados_api.json
```

É possível escolher um método e configurar o timeout por requisição:

```powershell
python testes/api/main.py --base-url http://127.0.0.1:8080 --report testes/paginacao.json --test test_23_maximum_integer_page_must_not_produce_500 --timeout 8
```

O teste isolado ainda prepara as três contas e quinze partidas da fixture. Cada cliente tem seu próprio cookie jar. Os testes concorrentes usam três clientes independentes e uma barreira, sem compartilhar sessão entre threads. A execução completa faz no máximo194 requisições; não é teste de carga.

## Cobertura

| Área | Verificações |
| --- | --- |
| Acesso | Páginas públicas; seis páginas internas redirecionadas; 401 em todas as rotas privadas de leitura/escrita |
| CSRF | Ausência do cabeçalho nas cinco operações mutáveis; valor incorreto rejeitado; nenhuma gravação indevida |
| Cadastro | Erros por campo, duplicidade ignorando maiúsculas, nome100/101, e-mail120/121, senha7/8/72/73, confirmação |
| Login | Erros vazios, resposta uniforme, três falhas sequenciais, falhas concorrentes, contador zerado no sucesso |
| Sessão | Cookie HttpOnly/SameSite/Lax/30dias, identidade, rotação no novo login, revogação do cookie antigo, logout, navegadores independentes |
| Partidas | Doze partidas e numeração, adversário1/2/60/61, data escolhida no calendário, data padrão em Brasília e horário de registro, tentativa de forjar proprietário, PUT/DELETE recusados |
| Histórico | Ordem, paginação10+2, limites1..50, parâmetros inválidos, página máxima int, filtros separados/combinados, vazio |
| Busca | SQL literal; `%`, `_` e `\` sem correspondência e com correspondência literal exata |
| Detalhes | Retrospecto4jogos, IDs inválidos e acima do long, isolamento em vários IDs nos dois sentidos |
| Painel/ranking | Totais e modos, cinco últimas, ordenação, marcador do próprio usuário e posição coerente sem posição global fixa |
| Perfil | Persistência, campos imutáveis, nome2/40/41, sobre300/301, validação sem gravação, formulário parcial, substituição do texto opcional |
| Saída | HTML devolvido como texto escapado, schema sem senha/hash, cabeçalhos em sucesso/erros, JSON autenticado sem cache |

## Regressões corrigidas e mantidas na suíte

- `test_11_concurrent_login_failures_must_also_lock`: depois de três senhas incorretas simultâneas, a senha correta deve receber **423**. Repete com até três contas novas, interrompendo ao encontrar qualquer violação. Os códigos das três primeiras respostas podem ser 401 ou 423; o teste não exige três respostas423. A barreira aumenta a chance de reproduzir a condição de corrida, mas nenhum teste black box pode garantir o intercalamento interno do servidor. Passar uma vez não prova ausência de corrida.
- `test_23_maximum_integer_page_must_not_produce_500`: `pagina=2147483647` deve ser rejeitada com400 ou normalizada com200. **500 é falha**, preservando a evidência do overflow na paginação.

As duas regressões passaram na versão corrigida. As expectativas seguem a regra pretendida e não foram relaxadas para fazer os bugs passarem. Nenhum teste é marcado como `expectedFailure` ou omitido para ocultar essas regressões.

## Atualização de perfil

O contrato existente é um **POST de formulário completo**, com nome de exibição e modo obrigatórios. Enviar somente `sobre` deve retornar400 e preservar o perfil. Enviar nome e modo válidos, omitindo o texto opcional `sobre`, atualmente substitui esse texto por vazio. Há testes separados para ambos; a suíte não presume suporte a PATCH. Essa substituição está documentada para tornar uma futura mudança de contrato explícita.

## Resultado e limites

O relatório JSON contém `cases` com `id`, `description`, `status` (`pass`, `fail`, `error`, `skipped`), duração e detalhes quando necessários; também inclui totais, número de requisições e código de saída. Falhas de subcasos são agregadas no método correspondente. Não grava valores de cookies ou senhas no relatório normal.

- Código0: todos os testes passaram.
- Código1: falha de comportamento ou erro no teste.
- Código2: servidor indisponível, timeout ou falha de infraestrutura.

Essa suíte não cobre DOM/navegador, atraso de respostas no frontend, renderização visual, espera real de15minutos/30dias, persistência após reinício ou ordenação de ranking quando percentuais distintos arredondam para o mesmo número. Esses aspectos exigem verificações complementares. As datas são verificadas no horário de Brasília, tolerando mudança de dia durante a requisição.
