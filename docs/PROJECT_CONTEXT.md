# PROJECT_CONTEXT.md

## 1. Projeto

Este projeto é um aplicativo mobile de leitura digital, inicialmente para uso pessoal, com foco principal em Android e experiência de leitura confortável no celular.

O aplicativo nasce da insatisfação com leitores existentes, especialmente na leitura de PDFs. O objetivo não é criar apenas um visualizador de documentos, mas um leitor que adapte a experiência ao tipo de conteúdo.

## 2. Problema principal

Os principais problemas que o projeto pretende resolver são:

- PDFs abertos como documentos tradicionais, com experiência semelhante a navegador;
- texto pequeno em PDFs técnicos;
- margens excessivas desperdiçando espaço em telas pequenas;
- navegação ruim em livros técnicos;
- dificuldade para ler código, tabelas, diagramas, imagens e fórmulas em PDFs;
- experiência inconsistente entre livros textuais, mangás e HQs;
- dificuldade de importar e organizar arquivos locais de maneira simples.

## 3. Tipos de conteúdo prioritários

O aplicativo deve funcionar especialmente bem com:

- livros de programação e tecnologia;
- livros de investimentos e finanças;
- mangás;
- HQs e quadrinhos.

## 4. Formatos

### MVP

- EPUB
- PDF
- CBZ
- CBR por conversão automática para CBZ na importação

### Somente se houver necessidade real

- MOBI
- AZW3

### Fora do projeto

- quebra ou contorno de DRM;
- formatos protegidos que exijam violação de mecanismos de proteção.

## 5. Princípios do produto

### 5.1 Local-first

O aplicativo deve funcionar completamente offline no MVP.

Não deve exigir:

- login;
- backend;
- conexão com internet;
- conta de usuário.

### 5.2 Reader-first

A prioridade máxima é a qualidade da leitura.

Ao decidir entre adicionar uma nova funcionalidade ou melhorar a experiência de leitura, priorizar a experiência de leitura.

### 5.3 Diferentes conteúdos exigem diferentes leitores

Não tentar tratar EPUB, PDF e quadrinhos como se fossem o mesmo formato.

Usar leitores especializados:

- EPUB Reader;
- PDF Reader;
- Comic Reader.

PDFs de mangá/HQ podem utilizar Comic Mode, mesmo continuando sendo arquivos PDF.

### 5.4 Preservação semântica

Qualquer melhoria aplicada a um PDF não pode destruir ou alterar o significado de:

- código;
- tabelas;
- fórmulas;
- diagramas;
- imagens;
- layout técnico importante.

Quando houver risco de reconstrução incorreta, preservar a representação original.

## 6. Definição de sucesso

O projeto não é considerado bem-sucedido apenas porque consegue abrir arquivos.

O objetivo é que o usuário prefira ler no aplicativo em vez de um visualizador PDF comum.

Fluxo básico obrigatório:

1. Abrir aplicativo;
2. Importar EPUB, PDF, CBZ ou CBR;
3. Livro aparecer na biblioteca;
4. Abrir livro;
5. Ler;
6. Fechar aplicativo;
7. Abrir novamente;
8. Continuar exatamente da posição anterior.

Critério qualitativo adicional:

> Após uma sessão prolongada de leitura, a experiência deve ser perceptivelmente mais confortável que abrir o mesmo arquivo em um visualizador de PDF tradicional.

## 7. Referências conceituais

Podem ser usados como inspiração de UX:

- Kindle;
- ReadEra;
- leitores de mangá/HQ maduros.

Não copiar limitações ou decisões desses produtos apenas por serem populares.
