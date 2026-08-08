# AI_DEVELOPMENT_RULES.md

## 1. Papel da IA

A IA deve atuar como engenheira de software do projeto, seguindo os documentos de contexto, produto, arquitetura, UX e testes.

Ela não deve redefinir o produto por conta própria.

## 2. Prioridade de decisão

Em caso de conflito, utilizar a seguinte ordem:

1. requisitos explícitos do usuário na tarefa atual;
2. PROJECT_CONTEXT.md;
3. PRODUCT_REQUIREMENTS.md;
4. PDF_READER_GUIDELINES.md / COMIC_READER_GUIDELINES.md;
5. ARCHITECTURE.md;
6. UX_GUIDELINES.md;
7. CODING_STANDARDS.md;
8. TESTING_GUIDELINES.md.

Se houver contradição relevante, apontar antes de implementar.

## 3. Não expandir escopo silenciosamente

Não implementar funcionalidades apenas porque parecem úteis.

Especialmente não adicionar sem solicitação:

- backend;
- login;
- cloud;
- sync;
- IA;
- recomendação;
- OCR avançado;
- MOBI/AZW;
- CBR no MVP;
- analytics;
- DRM;
- social;
- loja.

## 4. MVP-first

Antes de implementar qualquer recurso, verificar:

> Isto melhora diretamente a experiência de importar, encontrar, abrir, ler ou continuar um livro?

Se não, questionar se pertence ao MVP.

## 5. Não implementar engines do zero

Não criar do zero:

- renderizador PDF;
- engine EPUB.

Utilizar engines existentes depois de validação técnica.

## 6. PDF é prioridade de produto

Não tratar PDF apenas como requisito de compatibilidade.

Ao trabalhar no PDF Reader, considerar sempre:

- conforto em celular;
- paginação;
- Content Fit;
- preservação de conteúdo técnico;
- performance.

## 7. Preservar conteúdo

Não aplicar transformações destrutivas ou irreversíveis nos arquivos originais.

Original Mode deve permanecer disponível quando houver modos experimentais.

## 8. Não generalizar leitores em excesso

Compartilhar apenas conceitos realmente comuns.

Não forçar recursos EPUB, PDF e Comic dentro de uma única API se os conceitos forem diferentes.

## 9. Código incremental

Cada alteração deve:

- ter objetivo claro;
- evitar refactors fora do escopo;
- manter build funcionando;
- adicionar/atualizar testes quando comportamento relevante mudar.

## 10. Mudanças arquiteturais

Antes de alterar arquitetura, dependências centrais ou modelo de dados:

- explicar motivo;
- vantagens;
- desvantagens;
- impacto;
- alternativa mais simples.

Não fazer grandes mudanças arquiteturais silenciosamente.

## 11. Dependências

Não escolher biblioteca apenas por popularidade.

Avaliar:

- compatibilidade;
- manutenção;
- licença;
- performance;
- API;
- integração com Compose;
- arquivos grandes.

## 12. Honestidade técnica

Quando uma funcionalidade for experimental ou tecnicamente arriscada, declarar claramente.

Exemplo:

PDF reflow não deve ser descrito como simples ou garantido.

## 13. UX

Não considerar uma tarefa concluída apenas porque a lógica funciona.

Para recursos de leitura, verificar também:

- número de interações;
- espaço útil da tela;
- previsibilidade dos gestos;
- conforto prolongado.

## 14. Performance

Nunca carregar um livro ou quadrinho completo em memória quando streaming/lazy loading for possível.

## 15. Arquivos

Usar APIs seguras do Android para arquivos e permissões.

Não assumir paths absolutos tradicionais quando o Storage Access Framework exigir URIs persistentes.

## 16. Definição de pronto

Uma tarefa está pronta quando:

- requisito está atendido;
- estados de erro relevantes foram tratados;
- não introduz regressão conhecida;
- testes adequados passam;
- UX é coerente com as guidelines;
- documentação é atualizada se a decisão mudou.
