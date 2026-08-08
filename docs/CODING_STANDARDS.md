# CODING_STANDARDS.md

## 1. Linguagem

Kotlin seguindo estilo idiomático.

Evitar transportar padrões Java para Kotlin sem necessidade.

## 2. Clareza

Preferir código simples e explícito a abstrações sofisticadas prematuras.

Não criar padrões apenas para demonstrar conhecimento arquitetural.

## 3. Compose

- manter composables pequenos;
- state hoisting quando apropriado;
- UI deve receber estado e emitir eventos;
- não acessar banco ou filesystem diretamente em composables;
- evitar recomposições desnecessárias;
- usar previews quando úteis.

## 4. Coroutines

- Structured Concurrency;
- nunca usar `GlobalScope`;
- operações I/O fora da main thread;
- tratamento explícito de cancelamento quando necessário.

## 5. Estado

Preferir estado imutável exposto pela camada de apresentação.

Exemplo:

```kotlin
data class LibraryUiState(
    val books: List<BookUiModel> = emptyList(),
    val loading: Boolean = false,
    val error: UiError? = null
)
```

## 6. Erros

Não capturar `Exception` genericamente e ignorar.

Converter falhas técnicas em erros de domínio/UI quando necessário.

Log técnico não substitui mensagem ao usuário.

## 7. Modelos

Separar modelos externos/engine de modelos centrais quando isso reduz acoplamento.

Não deixar entidades Room vazarem indiscriminadamente para UI.

## 8. Null safety

Não usar `!!` salvo quando uma invariável comprovável justificar e estiver documentada.

## 9. Nomes

Usar nomes descritivos.

Evitar abreviações obscuras.

## 10. Comentários

Comentários devem explicar "por quê", não repetir o que o código faz.

## 11. Dependências

Antes de adicionar biblioteca:

- verificar manutenção;
- licença;
- tamanho/impacto;
- compatibilidade Android;
- necessidade real.

Não adicionar biblioteca para função trivial facilmente implementável.

## 12. Segurança de arquivos

Nunca confiar somente na extensão.

Validar tipo/conteúdo quando viável.

Tratar arquivos importados como não confiáveis.
