# DATA_MODEL.md

## 1. Objetivo

Persistir somente informações necessárias ao funcionamento do leitor local.

Evitar modelagem excessivamente complexa no MVP.

## 2. Book

Campos conceituais:

```text
Book
- id
- title
- author
- fileUri / fileReference
- fileHash
- fileType
- contentType
- coverReference
- fileSize
- dateAdded
- lastReadAt
- favorite
- status
```

### fileType

Representa o formato físico:

- EPUB;
- PDF;
- CBZ;
- CBR futuramente.

### contentType

Representa como o usuário deseja ler.

Exemplos:

- BOOK;
- DOCUMENT;
- COMIC;
- MANGA.

Isso permite que um PDF seja lido como Comic.

## 3. ReadingProgress

```text
ReadingProgress
- id
- bookId
- locator
- percentage
- updatedAt
```

O locator deve ser serializável e versionável.

## 4. Bookmark

```text
Bookmark
- id
- bookId
- locator
- title
- createdAt
```

Bookmark não significa livro favorito.

## 5. Favorite

Preferencialmente um boolean em `Book` no MVP.

Não criar tabela separada sem necessidade.

## 6. ReaderSettings

Pode existir configuração global e futuramente configuração por livro.

Exemplos:

### EPUB

- fontSize;
- lineSpacing;
- margin;
- readingMode.

### PDF

- displayMode;
- cropMode;
- fitMode.

### Comic

- readingDirection;
- displayMode;
- fitMode.

## 7. ReadingSession

Não é necessária no MVP.

Adicionar somente quando forem implementadas estatísticas detalhadas.

## 8. Hash de arquivos

SHA-256 pode ser utilizado para identificar conteúdo.

Não assumir que hash é necessário para cada interação do MVP.

Calcular fora da main thread.

Pode ser útil futuramente para:

- detectar duplicados;
- sincronização;
- identificar o mesmo livro em dispositivos diferentes.
