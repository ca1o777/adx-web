# Referências visuais do ADX

As telas do ADX usam HTML, CSS e JavaScript locais. As referências abaixo identificam as ideias e os trechos adaptados no projeto.

## Spell UI

O botão Entrar adapta a superfície e os estados do **Rich Button**. Ao enviar o formulário, o ADX exibe o SVG do **Spinner**, desativa envios repetidos e anuncia o progresso. A folha local `assets/css/login-form.css` inclui a licença MIT. [Rich Button](https://spell.sh/docs/rich-button) · [Spinner](https://spell.sh/docs/spinner) · [Licença](https://github.com/xxtomm/spell-ui/blob/a6441b7742b47d99932a8d687e895100bdf736be/LICENSE)

## OriginKit

A perspectiva do tabuleiro e a luz que acompanha o ponteiro se inspiram no **Chess Horizon**. A capa do ADX combina uma imagem original gerada para o projeto e um gradiente em CSS. A imagem é servida pelo próprio ADX e permanece visível com JavaScript desativado. [Chess Horizon](https://www.originkit.dev/components/chess-horizon)

## Cult UI

A moldura de 24 pixels, as superfícies arredondadas e as sombras sobrepostas do cartão de evolução adaptam o **Minimal Card** da Cult UI para CSS. O gráfico em SVG foi implementado no ADX. O **Analytics Chart** serviu de referência para destacar o ponto selecionado e consultar os dados de cada partida. [Minimal Card](https://www.cult-ui.com/docs/components/minimal-card) · [Fonte do componente](https://www.cult-ui.com/r/minimal-card.json) · [Analytics Chart](https://www.cult-ui.com/docs/components/analytics-chart)

## Skiper UI

A transição vertical dos totais segue o exemplo gratuito **Animated Number** da Skiper UI, de gxuri. A animação do ADX é uma implementação própria em CSS. O total final está disponível imediatamente aos leitores de tela e aparece sem animação quando a pessoa prefere movimento reduzido. [Animated Number](https://skiper-ui.com/v1/skiper37)

## Ranking, registro e perfil

No ranking, o resumo da conta leva o jogador à sua linha. A moldura da classificação retoma o Minimal Card, e a posição surge com uma transição curta inspirada no Animated Number. Cada percentual tem uma barra e o valor por escrito. [Cult UI](https://www.cult-ui.com/docs/components/minimal-card) · [Skiper UI](https://skiper-ui.com/v1/skiper37)

No registro, o jogador escolhe modo e resultado em controles de seleção únicos e confirma no botão dourado inspirado no Rich Button. No perfil, o avatar e o nome ficam sobre um tabuleiro em perspectiva desenhado em CSS a partir da ideia do Chess Horizon. [Spell UI](https://spell.sh/docs/rich-button) · [OriginKit](https://www.originkit.dev/components/chess-horizon)

## Licença da adaptação do Minimal Card

[Licença MIT da Cult UI](https://github.com/nolly-studio/cult-ui/blob/main/LICENSE.md)

```text
MIT License

Copyright (c) 2023 Jordan-Gilliam

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```
