# ARCHITECTURE.md

## 1. Plataforma inicial

A plataforma inicial recomendada é Android nativo.

### Stack base

- Kotlin;
- Jetpack Compose;
- Room / SQLite;
- DataStore;
- Kotlin Coroutines;
- Flow;
- Hilt;
- Coil para imagens;
- Readium Kotlin Toolkit para EPUB, sujeito a validação técnica;
- engine PDF a ser escolhida através de prova de conceito;
- ZIP parser para CBZ.

## 2. Por que Android nativo

Este projeto depende fortemente de:

- manipulação de arquivos locais;
- renderização;
- gestos;
- zoom;
- bitmaps;
- uso eficiente de memória;
- lifecycle;
- processamento offline;
- integração com APIs do Android.

Por isso, priorizar qualidade do leitor em vez de portabilidade prematura.

## 3. Não criar backend no MVP

Persistência local é suficiente inicialmente.

Backend futuro somente quando houver necessidade real de:

- sincronização;
- descoberta;
- recursos remotos.

## 4. Arquitetura modular

Estrutura conceitual:

```text
app
|
+-- core
|   +-- files
|   +-- database
|   +-- ui
|   +-- model
|   +-- utilities
|
+-- domain
|   +-- book
|   +-- library
|   +-- progress
|   +-- bookmark
|
+-- data
|   +-- repositories
|   +-- filesystem
|   +-- metadata
|
+-- reader
|   +-- common
|   +-- epub
|   +-- pdf
|   +-- comic
|
+-- feature
    +-- library
    +-- import
    +-- reader
    +-- settings
```

A estrutura física real pode ser simplificada inicialmente. Não criar módulos Gradle separados apenas para parecer arquiteturalmente sofisticado.

## 5. Regra de dependência

UI não deve conhecer detalhes internos de engines de arquivo quando isso puder ser evitado.

Exemplo:

A tela da biblioteca não deve depender de classes do Readium ou Pdfium.

## 6. Abstração de Reader

Não criar uma interface gigante que force EPUB, PDF e Comic a terem os mesmos conceitos.

Usar um núcleo comum mínimo.

Exemplo conceitual:

```kotlin
interface ReaderSession {
    val currentLocator: ReaderLocator
    val progress: Float

    suspend fun next()
    suspend fun previous()
    suspend fun goTo(locator: ReaderLocator)
}
```

Capacidades devem ser específicas.

Exemplos:

```text
EpubTextSettings
PdfViewportSettings
PdfCropCapability
ComicReadingDirection
SearchCapability
BookmarkCapability
```

## 7. Locator

Criar uma abstração de posição persistível.

Ela deve conseguir representar diferentes formatos sem perder informação.

Exemplo conceitual:

```text
ReaderLocator
- format
- payload
```

Possíveis payloads:

### PDF

```json
{"page":125}
```

### CBZ

```json
{"page":37}
```

### EPUB

Usar locator fornecido pela engine sempre que possível.

Não basear EPUB apenas em número visual de página.

## 8. Engines

### EPUB

Preferir engine existente e madura.

Não implementar parser/renderizador EPUB do zero.

### PDF

Não implementar renderizador PDF do zero.

Antes de fechar a engine, fazer POC avaliando:

- qualidade;
- zoom;
- paginação;
- renderização parcial;
- memória;
- arquivos grandes;
- acesso a coordenadas/texto;
- possibilidade de Content Fit;
- manutenção da biblioteca;
- licença.

### CBZ

Pode ser implementado como:

```text
CBZ
 -> ZIP
 -> lista de imagens
 -> ordenação natural
 -> decoder sob demanda
 -> reader
```

Não extrair todas as imagens para memória simultaneamente.

## 9. Processamento pesado

Nunca bloquear a main thread com:

- hashing;
- extração de metadata;
- geração de capa;
- abertura de arquivos grandes;
- análise de crop;
- decompression;
- renderização custosa.

Usar Coroutines com dispatcher adequado.

## 10. Migrações

Room deve possuir migrations quando schema persistido mudar.

Durante protótipos iniciais pode haver reset de banco, mas assim que dados reais do usuário forem armazenados, migrations tornam-se obrigatórias.

## 11. Portabilidade futura

Não sacrificar a qualidade Android atual por uma hipotética versão iOS/web.

Separar regras de domínio ajuda portabilidade futura, mas não criar abstrações artificiais para APIs que são naturalmente específicas da plataforma.
