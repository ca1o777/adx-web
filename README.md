# ADX Aplicativo de Xadrez

A aplicação web ADX registra resultados de partidas de xadrez e permite consultar o histórico, as estatísticas e o ranking dos jogadores. A implementação em Java está em [`adx-web-app`](adx-web-app/), com servidor Tomcat embutido, banco H2 e interface em HTML, CSS e JavaScript.

Na versão da entrega A2, o jogador pode gerenciar a foto de perfil, confirmar a alteração de e-mail e recuperar a senha. O [guia de e-mail](adx-web-app/README.md#e-mails-de-confirmação) explica a configuração SMTP e a demonstração local.

## Executar a aplicação web no Windows

1. Instale um JDK 17 ou superior.
2. Baixe e extraia o repositório. Abra a pasta `adx-web-app`.
3. Execute `executar.bat`. Na primeira execução, conecte-se à internet para o Maven Wrapper baixar as dependências.
4. Quando aparecer **ADX no ar**, acesse [localhost:8080](http://localhost:8080) e mantenha o terminal aberto.

Para iniciar com jogadores fictícios em um banco vazio, use `executar-demo.bat`. Após a primeira execução bem-sucedida, `./executar.bat -o` inicia em modo offline no PowerShell.

O [`README da aplicação`](adx-web-app/README.md) explica as rotas, as regras de negócio e o escopo. Para rodar a suíte automatizada, consulte [`testes/README.md`](adx-web-app/testes/README.md).

## Protótipo do primeiro bimestre

Os arquivos HTML, CSS e JavaScript na raiz registram a entrega do protótipo inicial, com dados demonstrativos no navegador.

### Como abrir o protótipo

Não precisa instalar nada. Basta abrir o arquivo `index.html` em um navegador moderno (Chrome, Edge ou Firefox).

### Telas do protótipo

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

### Estrutura do protótipo

```
adx-web-frontend/
├── *.html                 páginas da aplicação
└── assets/
    ├── css/style.css      estilos de todas as telas
    ├── js/app.js          lógica, navegação e validações dos formulários
    ├── js/dados.js        dados de demonstração (serão substituídos pelo backend)
    └── fonts/             fontes Barlow (uso offline)
```

## Observações

- Os dados exibidos são de demonstração e ficam em `assets/js/dados.js`.
- Partidas registradas na tela *Registrar partida* ficam salvas apenas no navegador (localStorage) até a integração com o backend.
- Autenticação real, bloqueio após três tentativas (RN003) e verificação de e-mail duplicado (RN001) serão feitos no backend.
