# UX_GUIDELINES.md

## 1. Princípio principal

A interface deve desaparecer quando não for necessária.

O conteúdo é mais importante que os controles.

## 2. Biblioteca

Estrutura sugerida:

```text
Minha Biblioteca

[ Buscar ]

Continue lendo
--------------------------------
Livro atual                  38%

Filtros
[Todos] [Livros] [Mangás] [PDFs] [Favoritos]

Biblioteca
[capa] [capa] [capa]
```

## 3. Favoritos

Favoritos devem ser acessíveis através do filtro da biblioteca.

Marcar/desmarcar favorito deve exigir no máximo uma interação simples através da capa, detalhes ou menu do livro.

## 4. Reader UI

Durante leitura normal:

- esconder app bar;
- esconder controles desnecessários;
- maximizar área do conteúdo.

Um toque central pode mostrar/esconder os controles.

## 5. Gestos

Evitar inventar gestos incomuns.

Padrões recomendados:

- swipe horizontal: próxima/anterior página em modo paginado;
- pinch: zoom onde aplicável;
- drag: pan quando conteúdo estiver ampliado;
- toque central: mostrar/esconder controles.

Gestos não podem entrar em conflito facilmente.

## 6. PDF

### Comportamento padrão desejado

- uma página por vez;
- não usar scroll infinito por padrão;
- transição previsível;
- lembrar modo de visualização quando apropriado.

### Controles importantes

- página atual / total;
- progresso;
- Original Mode;
- Content Fit;
- fit width;
- fit height;
- zoom;
- bookmark.

Não poluir a tela mostrando todos simultaneamente durante leitura.

## 7. EPUB

O usuário deve conseguir ajustar texto sem abrir uma tela complexa.

Principais configurações:

- tamanho;
- espaçamento;
- margem;
- tema.

## 8. Comic

Configurações principais:

- direção LTR/RTL;
- página/vertical;
- fit width/height.

## 9. Progresso

Mostrar progresso de forma discreta.

Exemplo:

```text
Capítulo 4                   42%
----------●---------------------
```

Para PDF e Comic, página atual pode acompanhar porcentagem.

## 10. Erros

Mensagens devem explicar o problema em linguagem simples.

Ruim:

> IOException EACCES.

Bom:

> Não foi possível acessar este arquivo. Ele pode ter sido movido ou a permissão de acesso foi removida.

## 11. Acessibilidade

Não depender somente de cor para comunicar estado.

Manter áreas de toque adequadas e contraste legível.

## 12. Anti-patterns

Evitar:

- animações longas;
- barras permanentes ocupando espaço de leitura;
- botões flutuantes sobre o conteúdo sem necessidade;
- excesso de menus;
- configurações técnicas expostas ao usuário;
- copiar interface de navegador para PDFs.
