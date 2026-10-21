# Identidade visual

O frontend não inventa estilo: ele reaproveita a linguagem do portfólio
[`leonardobarrosx/leonardobarros-portfolio`](https://github.com/leonardobarrosx/leonardobarros-portfolio).
Os valores abaixo foram extraídos de `src/styles/global.css` e `src/data/themes.ts` daquele repositório
(tema `red`, o padrão da casa) e vivem aqui em [`frontend/src/styles/tokens.css`](../frontend/src/styles/tokens.css).
Nenhum componente usa cor escrita à mão: trocar o tema é trocar o bloco de tokens.

> Estética de pôster editorial sobre um grid de 12 colunas: papel creme, tinta vermelha, escuros de café,
> tipografia display condensada e acentos em kanji.

## Paleta

| Token | Claro | Escuro | Uso |
|---|---|---|---|
| `--paper` | `#efe9df` | `#1e1512` | fundo da página |
| `--paper-2` | `#e6dfd3` | `#2a1d18` | pôster de papel, superfícies secundárias |
| `--ink` | `#141110` | `#efe9df` | texto principal |
| `--ink-2` | `#2b2521` | `#c8b6a3` | texto de apoio |
| `--accent` | `#e3301c` | `#e3301c` | tinta vermelha: botão, disco, faixa corrida, pôster vermelho |
| `--accent-deep` | `#b8230f` | `#b8230f` | vermelho escuro |
| `--accent-light` | `#ff8a78` | `#ff8a78` | vermelho para texto sobre fundo escuro |
| `--split` | `#1fb6a8` | `#1fb6a8` | segunda cor (no portfólio, o rastro cromático); aqui marca créditos |
| `--surface-dark` | `#1e1512` | `#3b2a21` | painel de resumo, folha de confirmação |
| `--surface-ink` | `#141110` | `#2a1d18` | pôster de tinta (saídas, recusa) |
| `--lilac` | `#c8b6a3` | `#c8b6a3` | texto suave sobre escuro |
| `--mute` | `#6f655c` | `#c8b6a3` | metadados, dicas |
| `--line` | `rgba(20,17,16,.14)` | `rgba(239,233,223,.16)` | réguas de 1px |

O esquema escuro segue `prefers-color-scheme` até a pessoa escolher no seletor **Claro / Escuro**, como no portfólio.

## Tipografia

| Papel | Família | Onde |
|---|---|---|
| Display | **Anton**, caixa alta, `line-height: .9` | saldo, valores, títulos, botões |
| Serifada itálica | **Instrument Serif** *italic* | a palavra de ênfase em frases ("*duas vezes*") |
| Mono | **IBM Plex Mono** 11px, caixa alta, `letter-spacing: .08em` | rótulos, metadados, chaves, end-to-end id |
| Corpo | **Inter** 400/500/600 | texto corrido, campos |
| Kanji | **Noto Sans JP** 700/900, recortada com `&text=` | acentos dos rótulos: 残高 saldo · 今月 este mês · 明細 extrato · 振込 transferência · 確認 confirmação · 完了 concluído · 拒否 recusa · 再送 reenvio |

Escala: `--t-hero clamp(64px, 11.5vw, 176px)`, `--t-display clamp(40px, 5.2vw, 80px)`, `--t-lead clamp(17px, 1.35vw, 20px)`,
`--t-body 16px`, `--t-small 14px`, `--t-mono 11px`.

## Espaço e grid

- Base de 8px: `--s1 8` · `--s2 16` · `--s3 24` · `--s4 32` · `--s5 48` · `--s6 64` · `--s7 96` · `--s8 128`.
- Grid de 12 colunas, `--gap clamp(16px, 1.6vw, 24px)`, margem lateral `--gutter clamp(20px, 3.4vw, 56px)`, largura máxima 1440px.
- Quebras em 900px (12 → 6 colunas, navegação em duas linhas) e 560px (coluna única).

## Componentes herdados

| Do portfólio | Aqui |
|---|---|
| Rótulo de seção `01 / About ——— 私について` | `01 / Saldo disponível ——— 残高` |
| Pílula translúcida da navegação (blur, borda `--line`, item ativo em tinta) | navegação, seletor de conta, Claro/Escuro |
| `.btn`: pílula com borda, Anton, preenchimento vermelho que sobe no hover | Transferir, Revisar, Enviar Pix |
| Disco vermelho do hero (`mix-blend-mode: multiply`) | ao lado do saldo |
| Faixa corrida vermelha com pontos e kanji | vocabulário do motor: Pix · ACID · 送金 · Idempotência… |
| Pôsteres `--red`, `--coffee`, `--paper` com formas decorativas | entradas, saídas, lançamentos e comprovante |
| Linhas da lista de experiência (régua, deslocamento no hover) | extrato |
| Folha lateral escura (`.sheet`) | confirmação do envio |
| Grão de impressão animado | mesmo overlay, desligado com `prefers-reduced-motion` |

## Acessibilidade

Link para pular ao conteúdo, foco visível em vermelho, rótulos ligados aos campos, `aria-live` nos estados do envio,
foco levado ao título da folha de confirmação e do comprovante, `Esc` fecha a folha, e o sinal de crédito/débito
nunca depende só da cor (vem escrito, `+` / `−`, além do ponto colorido).
