# ADX – Aplicativo de Xadrez (Aplicação Web) · Frontend

Frontend da aplicação web ADX, entregue no 1º bimestre. O backend em Java e o banco de dados serão integrados no 2º bimestre.

## Como executar

Não precisa instalar nada. Basta abrir o arquivo `index.html` em um navegador moderno (Chrome, Edge ou Firefox).

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
