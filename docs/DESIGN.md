# Identidade visual

O front junta duas coisas: a identidade do meu portfólio
([`leonardobarrosx/leonardobarros-portfolio`](https://github.com/leonardobarrosx/leonardobarros-portfolio)) e as
convenções de um app de banco. Do portfólio vêm a cor, a marca e alguns gestos; do banco vêm a estrutura, a
densidade de informação e a sobriedade de quem mostra dinheiro.

Todos os valores vivem em [`frontend/src/styles/tokens.css`](../frontend/src/styles/tokens.css); nenhum componente
usa cor escrita à mão.

## O que veio do portfólio

| Elemento | No portfólio | Aqui |
|---|---|---|
| Vermelho `#e3301c` | tinta dos pôsteres, botões, disco do hero | ação principal (Fazer um Pix, Entrar, Confirmar), item ativo do menu, saídas no resumo |
| Escuros de café `#1e1512` · `#2a1d18` · `#3b2a21` | faixas e painéis escuros | menu lateral, cartão de saldo, cabeçalho do comprovante, painel do login |
| Creme `#efe9df` | fundo da página | clareado para `#f5f2ed`: fundo de app, mais neutro para ler números |
| Anton, caixa alta, ponto vermelho | marca "LB" e títulos display | só a marca **● MOTOR** e a manchete do login: identidade, sem gritar dentro do app |
| Instrument Serif itálico | palavra de ênfase | "*uma vez só.*" no login |
| IBM Plex Mono | rótulos e metadados | agência, conta, chave Pix, ID da transação, Idempotency-Key, tempo de sessão |
| Disco vermelho | sol do hero | canto do cartão de saldo, do comprovante e do painel de login |
| Turquesa `#1fb6a8` | rastro cromático | escurecido para `#0f7a6c` (contraste AA): cor de **entrada de dinheiro** |

## O que veio do banco

- **Estrutura de app:** menu lateral fixo no desktop, barra inferior com três abas no celular, barra superior com
  saudação, data, ocultar valores, tema e sair.
- **Cartão de saldo** escuro com o valor grande em Inter (números tabulares, não display condensado: dinheiro precisa
  ser lido sem esforço), agência, conta e chave Pix copiável.
- **Ocultar valores** em toda a interface (`R$ •••••`), lembrado entre visitas.
- **Pix em etapas** com indicador de progresso, consulta do recebedor (nome + CPF mascarado + instituição) antes de
  pedir o valor, e uma revisão final antes de confirmar.
- **Comprovante** com cabeçalho de status, valor, data completa, partes, e os identificadores que um suporte pediria
  (E2E, protocolo), com recorte serrilhado.
- **Extrato** agrupado por dia ("Hoje", "Ontem"), ícone de entrada/saída, valor de entrada em verde e com `+`.
- **Sessão visível**: o tempo restante do token aparece no menu, e a sessão termina sozinha ao expirar.

## Paleta

| Token | Claro | Escuro | Uso |
|---|---|---|---|
| `--bg` | `#f5f2ed` | `#17110e` | fundo |
| `--surface` | `#ffffff` | `#1e1512` | cartões |
| `--ink` / `--ink-2` / `--mute` | `#141110` / `#4a423c` / `#7a7068` | `#efe9df` / `#c8b6a3` / `#9c8f82` | texto |
| `--line` | `#e8e2da` | `#33251e` | bordas |
| `--brand` | `#e3301c` | `#e3301c` | ação principal |
| `--coffee-900` | `#1e1512` | `#120c0a` | menu, cartão de saldo |
| `--positive` | `#0f7a6c` | `#4fd1bf` | entradas |
| `--warning` | `#9a5b00` | `#f0b45a` | conta bloqueada, valor acima do saldo |

## Tipografia e forma

- **Inter** para toda a interface, com `font-variant-numeric: tabular-nums` em valores (os dígitos alinham em coluna).
- **Anton** só na marca e na manchete do login. **IBM Plex Mono** para identificadores.
- Raios de 12–24px, botões em pílula, sombras curtas. Espaçamento em múltiplos de 4px.

## Vidro (glassmorphism)

Usado onde há algo atrás para desfocar e onde o conteúdo não é um número a ser lido com precisão:

- **barra superior** e **barra inferior do celular**, que flutuam sobre o conteúdo rolando;
- **gaveta do comprovante** e o fundo desfocado atrás dela;
- **atalhos** da tela inicial, o seletor de filtros do extrato, os **toasts** e o cartão de **login**;
- a pílula de agência/conta dentro do cartão de saldo café.

O fundo da página tem duas manchas suaves (vermelho do portfólio e café, bem diluídos) para o vidro ter o que
refratar. Cartões com saldo, extrato e comprovante continuam opacos: contraste de valor monetário não se negocia.

Tokens em `tokens.css` (`--glass-bg`, `--glass-bg-strong`, `--glass-border`, `--glass-blur`), com versões para o
tema escuro. Navegadores sem `backdrop-filter` (`@supports`) e quem ativa `prefers-reduced-transparency` recebem as
superfícies sólidas de antes.

## Acessibilidade

Link para pular ao conteúdo, foco visível em vermelho, rótulos ligados aos campos, `aria-current` na etapa do Pix e no
item do menu, gaveta do comprovante com `Esc` e foco devolvido ao fechar, entrada e saída nunca indicadas só pela cor
(ícone e sinal `+`/`−`), e animações desligadas com `prefers-reduced-motion`.
