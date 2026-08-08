# TESTING_GUIDELINES.md

## 1. Filosofia

Testes devem proteger comportamento importante, não perseguir porcentagem de cobertura artificial.

## 2. Caminho feliz principal

Este fluxo é obrigatório:

```text
abrir app
 -> importar livro
 -> abrir livro
 -> navegar
 -> fechar
 -> reabrir app
 -> reabrir livro
 -> continuar na posição correta
```

Testar para:

- EPUB;
- PDF;
- CBZ.

## 3. Unit tests

Priorizar:

- cálculo de progresso;
- ordenação;
- filtros;
- identificação de formato;
- serialização de locators;
- bookmarks;
- favorito;
- natural sort de páginas CBZ;
- crop logic que puder ser isolada.

## 4. Integration tests

Priorizar:

- Room;
- migrations;
- importação;
- persistência de progresso;
- reabertura;
- arquivo removido;
- permissões.

## 5. UI tests

Fluxos principais:

- biblioteca vazia;
- importar;
- filtrar favoritos;
- abrir leitor;
- voltar;
- continuar leitura;
- trocar modo PDF;
- RTL em comic.

## 6. Arquivos de teste

Manter corpus de arquivos representativos.

### EPUB

- simples;
- muitas imagens;
- livro técnico;
- índice complexo.

### PDF

- texto comum;
- código;
- tabelas;
- fórmulas;
- diagramas;
- duas colunas;
- margens grandes;
- PDF escaneado;
- arquivo grande;
- arquivo protegido ou inválido.

### CBZ

- nomes 1.jpg, 2.jpg, 10.jpg;
- imagens grandes;
- arquivo corrompido;
- ZIP malicioso controlado para validar limites.

## 7. Performance tests

Avaliar:

- tempo de abertura;
- mudança de página;
- pico de memória;
- scroll;
- zoom;
- importação de arquivos grandes.

## 8. Regressão de PDF Content Fit

Uma alteração no algoritmo de crop deve ser testada contra múltiplos PDFs.

Não aprovar apenas porque funciona em um documento específico.
