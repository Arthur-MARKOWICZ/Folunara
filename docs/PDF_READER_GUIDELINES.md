# PDF_READER_GUIDELINES.md

## 1. Importância

PDF é um dos principais motivos para este aplicativo existir.

A implementação deve ser avaliada pela experiência real de leitura, não apenas por conseguir renderizar o arquivo.

## 2. Requisito central

O comportamento padrão deve ser baseado em páginas.

Não utilizar scroll infinito como única experiência.

## 3. Modos

### 3.1 Original Mode — MVP

Preserva completamente a página.

Necessário:

- página única;
- swipe;
- zoom;
- pan;
- fit width;
- fit height;
- progresso;
- bookmark;
- ir para página.

### 3.2 Content Fit — MVP

Objetivo:

Aproveitar melhor a tela sem reconstruir semanticamente o PDF.

Pipeline conceitual:

```text
PDF page
 -> render/analyze
 -> detectar bounding box útil
 -> aplicar crop visual
 -> renderizar área útil
 -> fit na tela
```

### Regras

- preservar proporções;
- nunca cortar conteúdo relevante silenciosamente;
- permitir desativar crop imediatamente;
- considerar margens diferentes entre páginas pares/ímpares;
- evitar recalcular análise custosa em cada frame;
- cachear resultados de crop quando seguro.

### 3.3 Reading Mode — Futuro experimental

Pode realizar reflow ou reconstrução.

Nunca assumir que é confiável.

Problemas esperados:

- ordem incorreta de leitura;
- código quebrado;
- tabelas desestruturadas;
- fórmulas destruídas;
- imagens separadas do contexto;
- PDFs escaneados sem camada textual.

Por isso:

- manter Original sempre acessível;
- não modificar arquivo original;
- informar quando a representação é reconstruída.

### 3.4 Column Mode — Pós-MVP

Destinado principalmente a PDFs de múltiplas colunas.

Não implementar antes que Original + Content Fit estejam estáveis.

## 4. Livros técnicos

Durante testes de PDF, incluir documentos com:

- código monoespaçado;
- tabelas;
- diagramas;
- imagens;
- fórmulas;
- páginas com duas colunas;
- capítulos textuais longos.

Uma otimização não pode ser aceita se melhorar prosa simples mas degradar seriamente conteúdo técnico.

## 5. Arquivos grandes

Não manter todas as páginas renderizadas em memória.

Estratégia esperada:

```text
previous page
current page
next page(s)
```

Cache adaptável conforme memória disponível.

## 6. Crop

Implementação inicial pode ser simples.

Uma heurística simples e previsível é preferível a uma solução "inteligente" instável.

Fases possíveis:

1. crop manual;
2. bounding box simples;
3. detecção automática melhorada;
4. perfis por documento.

## 7. Persistência

Salvar ao menos:

- página atual;
- modo de leitura, se aplicável;
- progresso.

Não persistir zoom transitório sem necessidade.

## 8. Testes de experiência

Comparar com visualizador comum.

Testar sessões reais, não apenas screenshots.

Perguntas importantes:

- o texto ficou maior sem zoom constante?
- as margens deixaram de incomodar?
- trocar página é previsível?
- código continua legível?
- tabelas continuam compreensíveis?
- o usuário precisa reposicionar a página toda vez?
