# E-reader

Leitor Android local-first para EPUB, PDF, CBZ e CBR. Arquivos CBR são convertidos automaticamente para CBZ no armazenamento privado do app durante a importação. O projeto requer **JDK 17** e Android SDK API 36 (target API 35).

O seletor de importação aceita múltiplos arquivos na mesma operação, incluindo vários PDFs de uma vez.

## Executar

1. Abra a pasta no Android Studio.
2. Configure o Android SDK se solicitado e sincronize o Gradle.
3. Execute `./gradlew testDebugUnitTest` e `./gradlew assembleDebug`.

O leitor PDF usa `PdfRenderer` nativo e o leitor EPUB usa Readium Kotlin Toolkit. Os três leitores persistem progresso e marcadores localmente.

O estado detalhado do escopo e das validações está em [`docs/MVP_REFINEMENT_REPORT.md`](docs/MVP_REFINEMENT_REPORT.md). O build atual é testável, mas ainda não cumpre todos os critérios documentados para declarar o MVP concluído.

## Arquivos de teste no emulador

Com o Pixel 9 aberto ou fechado, execute no PowerShell:

```powershell
.\tools\prepare_emulator_test_files.cmd
```

O script gera um EPUB, um PDF e um CBZ válidos, inicia o AVD `Pixel_9` quando necessário e envia os arquivos para `Download/E-reader-tests`. Para usar outro dispositivo conectado, informe o serial exibido por `adb devices`:

```powershell
.\tools\prepare_emulator_test_files.cmd -Serial emulator-5554
```
