# COMIC_READER_GUIDELINES.md

## 1. Objetivo

Fornecer uma experiência específica para mangás e HQs.

Não reutilizar cegamente a UX de PDF textual.

## 2. Fontes de conteúdo

### MVP

- CBZ;
- PDF em Comic Mode.

### Pós-MVP

- CBR.

## 3. CBZ

CBZ deve ser tratado como ZIP contendo imagens.

Regras:

- ordenar nomes naturalmente;
- suportar JPG, JPEG, PNG e formatos de imagem aprovados;
- validar arquivos;
- não carregar todas as imagens em memória;
- prevenir ZIP bombs;
- carregar páginas sob demanda.

## 4. Modos MVP

- página única;
- horizontal;
- scroll vertical;
- LTR;
- RTL;
- fit width;
- fit height;
- zoom.

## 5. Mangá

RTL deve ser configuração explícita e fácil de encontrar.

Não assumir automaticamente que todo conteúdo japonês ou todo CBZ usa RTL.

## 6. PDF Comic Mode

Um PDF pode ser interpretado como sequência de páginas visuais.

Não aplicar reflow textual nesse modo.

## 7. Futuro

- página dupla;
- webtoon;
- detecção de spread;
- detecção de painéis.

Não implementar detecção de painéis no MVP.
