# Decisões técnicas — Fases 0–3

## Plataforma

- Android nativo, Kotlin e Jetpack Compose; `minSdk 29`.
- Arquivos locais são acessados pelo Storage Access Framework com URI persistente.
- PDF: a primeira POC usa `android.graphics.pdf.PdfRenderer`, API nativa que preserva o documento e permite renderização página a página. A decisão para produção depende de validar zoom, cache e crop com o corpus técnico.
- EPUB: Readium Kotlin Toolkit é o candidato aprovado para a POC por ser uma engine EPUB dedicada. Sua versão e adaptador serão fixados somente depois de validar publicação, locator, índice e temas contra o SDK Android instalado.
- CBZ: ZIP nativo, enumeração e decodificação preguiçosa; o leitor fica fora das fases 0–3.

## Fases 4–6

- Content Fit usa uma heurística visual conservadora baseada em pixels quase brancos e acrescenta uma margem de segurança de 2%. Ele nunca altera o arquivo; o usuário pode voltar a **Original** a qualquer momento.
- O leitor CBZ enumera somente imagens aceitas, ordena páginas naturalmente, limita o arquivo a 10.000 entradas e cada imagem descompactada a 80 MB. Ele carrega apenas a página atual.
- PDF em Comic Mode, cache persistente de crops e preferências por livro permanecem pendentes: exigem a validação no aparelho e uma migração de schema antes de persistir novas preferências.

## Limitações conhecidas

O ambiente de desenvolvimento atual não possui Android SDK, Gradle ou JDK 17. Portanto, este projeto precisa ser sincronizado no Android Studio para resolver dependências e executar as POCs. Não se deve considerar uma engine selecionada para produção antes desses testes.
