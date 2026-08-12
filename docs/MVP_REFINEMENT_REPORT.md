# Relatório de refinamento do MVP

Data da auditoria: 12/08/2026.

## Veredito

**O MVP ainda não está concluído.** O aplicativo compila, gera APK e os leitores principais já oferecem uma base funcional consistente, mas ainda há requisitos explícitos de `PRODUCT_REQUIREMENTS.md` e `ROADMAP.md` sem implementação ou sem validação de ponta a ponta.

## Refinamentos aplicados nesta rodada

- marcadores persistentes em EPUB, PDF e CBZ, com ação de adicionar/remover, lista e navegação para a posição marcada;
- restauração correta da versão de locators de PDF Reading Mode;
- reconhecimento de formato por extensão ou MIME;
- prevenção de importação duplicada da mesma URI e persistência do tamanho informado pelo provedor;
- remoção explícita de progresso e marcadores ao excluir um livro da biblioteca;
- Content Fit sem alocar um array com todos os pixels da página;
- decodificação amostrada de imagens CBZ grandes para reduzir risco de falta de memória;
- início correto de quadrinhos RTL na última página quando ainda não existe progresso salvo;
- testes de regressão para MIME, locators persistidos e limite de memória de quadrinhos.

## Estado por área

| Área | Estado | Evidência ou pendência |
|---|---|---|
| Biblioteca, busca, filtros, ordenação, favoritos e Continue lendo | Implementado | Fluxos presentes na tela e persistência local |
| Importação EPUB/PDF/CBZ | Implementado | Extensão e MIME reconhecidos; URI persistente |
| Retomada EPUB | Implementado no código | Locator Readium persistido e restaurado; falta teste instrumentado do caminho completo |
| Retomada PDF/CBZ | Implementado no código | Página/bloco persistidos; falta teste instrumentado do caminho completo |
| Marcadores | Implementado | Adicionar, remover na posição atual, listar e navegar nos três leitores |
| EPUB confortável | Parcial | Leitura, tema, fonte, espaçamento, margem e layout existem; faltam capítulos/sumário |
| PDF Original | Parcial | Página única, swipe, zoom, pan e fit existem; falta “ir para página” |
| PDF Content Fit | Parcial | Crop automático conservador existe; faltam crop manual e cache persistente de bounding boxes |
| Comic Reader CBZ | Implementado no código | Paginado/vertical, LTR/RTL, fit, zoom e progresso; falta validação no corpus |
| PDF Comic Mode | Não implementado | PDF sempre abre no leitor PDF atual |
| Metadados | Parcial | Capa e título existem; autor não é extraído dos arquivos |
| Offline | Implementado por arquitetura | Não há login ou backend no fluxo principal |
| Acessibilidade | Parcial | Controles principais têm descrições; falta validação manual com TalkBack e contraste |
| Arquivos grandes/performance | Parcial | Há limites de bitmap/render e amostragem; faltam medições no corpus e teste prolongado |

## Validação executada

- `gradle testDebugUnitTest assembleDebug`: **sucesso**;
- 35 testes unitários, 0 falhas, 0 erros e 0 ignorados;
- APK de debug gerado em `app/build/outputs/apk/debug/app-debug.apk`.

Não foram executados nesta rodada os testes instrumentados, testes de UI, TalkBack nem o corpus manual de EPUB/PDF/CBZ, pois exigem emulador/dispositivo e inspeção humana.

## Critérios restantes para declarar o MVP concluído

1. Implementar capítulos/sumário no EPUB.
2. Implementar “ir para página” no PDF.
3. Implementar crop manual e cache persistente do Content Fit.
4. Permitir abrir PDF em Comic Mode.
5. Extrair autor quando disponível.
6. Automatizar e aprovar o caminho importar → ler → fechar → reabrir → retomar para EPUB, PDF e CBZ.
7. Executar regressão em arquivos grandes, corpus de Content Fit, acessibilidade e sessão prolongada de leitura.
