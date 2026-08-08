# PRODUCT_REQUIREMENTS.md

## 1. Escopo do MVP

O MVP é um leitor local. Ele não é uma plataforma social, loja de livros ou serviço de sincronização.

## 2. Biblioteca local

### Obrigatório

- importar arquivos do dispositivo;
- reconhecer EPUB, PDF e CBZ;
- exibir capa quando disponível;
- exibir título;
- exibir autor quando disponível;
- exibir formato;
- exibir progresso;
- exibir último acesso;
- remover item da biblioteca;
- ordenar livros;
- marcar livro como favorito;
- filtrar biblioteca.

### Filtros iniciais

- Todos;
- Livros;
- Mangás;
- PDFs;
- Favoritos.

### Ordenações iniciais

- título;
- autor;
- data adicionada;
- última leitura;
- progresso.

### Não implementar inicialmente

- tags complexas;
- coleções inteligentes;
- editor completo de metadados;
- gerenciamento avançado de séries;
- filtros compostos.

## 3. Continue lendo

A tela principal deve possuir uma área de acesso rápido aos livros recentes ou em andamento.

Utilizar o progresso já persistido e `lastReadAt`.

## 4. Favorito vs Bookmark

Estes conceitos são diferentes e nunca devem ser misturados.

### Favorite

Marca um livro como favorito na biblioteca.

### Bookmark

Marca uma posição específica dentro do conteúdo.

Exemplos:

- PDF: página 125;
- CBZ: página 37;
- EPUB: locator/CFI/progression.

## 5. Histórico

### MVP

Histórico simples baseado em último acesso.

Exemplo:

- livro A — agora;
- livro B — ontem;
- livro C — 03/08.

### Pós-MVP

Sessões detalhadas, tempo de leitura e páginas lidas.

## 6. EPUB Reader

### MVP

- abrir EPUB;
- capítulos;
- índice;
- paginação horizontal;
- scroll vertical opcional;
- tamanho da fonte;
- espaçamento;
- margens;
- tema claro;
- tema escuro;
- salvar posição automaticamente;
- progresso;
- bookmark;
- busca básica, caso a engine escolhida ofereça suporte confiável.

## 7. PDF Reader

O PDF é um diferencial central do aplicativo.

O MVP não deve ser apenas um `PDF Viewer` convencional.

### 7.1 Original Mode

- preservar página original;
- página por página;
- sem scroll infinito como comportamento padrão;
- swipe para avançar ou voltar;
- zoom;
- pan;
- fit width;
- fit height;
- ir para página;
- progresso;
- bookmark.

### 7.2 Content Fit Mode

Faz parte da proposta do MVP.

Objetivo:

Reduzir espaço desperdiçado e melhorar a leitura em telas pequenas sem reconstruir semanticamente o documento.

Recursos desejados:

- detectar margens úteis;
- crop automático simples;
- crop manual, se necessário;
- remover visualmente margens;
- ajustar área útil à largura da tela;
- manter imagens, tabelas, diagramas, fórmulas e código intactos.

### 7.3 Reading Mode

Pós-MVP / experimental.

Pode tentar:

- extração de texto;
- reflow;
- reconstrução de parágrafos;
- reorganização de conteúdo.

Regras:

- nunca substituir permanentemente o original;
- permitir voltar imediatamente ao Original Mode;
- tratar resultado como potencialmente imperfeito;
- não assumir que texto extraído preserva semântica.

### 7.4 Column Mode

Pós-MVP.

Objetivo:

Melhorar PDFs de duas ou mais colunas em telas pequenas.

Possível fluxo:

- coluna esquerda;
- coluna direita;
- próxima página.

## 8. Comic Reader

Deve suportar inicialmente CBZ e futuramente CBR.

### MVP

- página única;
- navegação horizontal;
- scroll vertical;
- zoom;
- fit width;
- fit height;
- direção esquerda para direita;
- direção direita para esquerda;
- salvar página atual;
- progresso;
- bookmark.

PDFs de mangás/HQs devem poder ser abertos em Comic Mode.

### Pós-MVP

- página dupla;
- modo webtoon;
- detecção automática de spreads.

### Futuro avançado

- detecção de painéis;
- navegação painel a painel.

## 9. Configurações

### Globais

- tema do aplicativo;
- preferências padrão de leitor.

### Por livro

Pós-MVP ou apenas onde a implementação for barata.

Exemplos:

- modo PDF escolhido;
- direção do mangá;
- zoom/crop;
- estilo EPUB.

## 10. Fora do MVP

Não implementar no MVP:

- login;
- backend;
- sincronização;
- conta;
- loja;
- descoberta de livros;
- recomendações;
- IA;
- OCR avançado;
- anotações manuscritas;
- DRM;
- cliente web;
- cliente desktop;
- integração Kindle/Kobo;
- painel inteligente de quadrinhos;
- reflow PDF avançado.
