[CmdletBinding()]
param(
    [string]$AvdName = "Pixel_9",
    [string]$Serial,
    [string]$RemoteDirectory = "/sdcard/Download/E-reader-tests",
    [switch]$SkipGenerate
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$Generator = Join-Path $PSScriptRoot "generate_emulator_test_files.py"
$LocalFiles = @(
    (Join-Path $ProjectRoot "output\emulator-tests\teste-leitura.epub")
    (Join-Path $ProjectRoot "output\pdf\teste-documento.pdf")
    (Join-Path $ProjectRoot "output\emulator-tests\teste-quadrinhos.cbz")
)

function Resolve-Executable {
    param(
        [string[]]$Commands,
        [string[]]$FallbackPaths,
        [string]$Description
    )

    foreach ($commandName in $Commands) {
        $command = Get-Command $commandName -ErrorAction SilentlyContinue
        if ($command) { return $command.Source }
    }
    foreach ($path in $FallbackPaths) {
        if ($path -and (Test-Path -LiteralPath $path)) { return $path }
    }
    throw "$Description nao encontrado. Instale-o ou adicione-o ao PATH."
}

$AndroidSdk = if ($env:ANDROID_SDK_ROOT) {
    $env:ANDROID_SDK_ROOT
} elseif ($env:LOCALAPPDATA) {
    Join-Path $env:LOCALAPPDATA "Android\Sdk"
} else {
    $null
}

$AdbFallback = if ($AndroidSdk) { Join-Path $AndroidSdk "platform-tools\adb.exe" } else { $null }
$Adb = Resolve-Executable `
    -Commands @("adb") `
    -FallbackPaths @($AdbFallback) `
    -Description "ADB do Android SDK"

if (-not $SkipGenerate) {
    $CodexPython = if ($env:USERPROFILE) {
        Join-Path $env:USERPROFILE ".cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
    } else {
        $null
    }
    $Python = Resolve-Executable `
        -Commands @("python", "python3", "py") `
        -FallbackPaths @($CodexPython) `
        -Description "Python 3"

    Write-Host "Gerando EPUB, PDF e CBZ..." -ForegroundColor Cyan
    & $Python $Generator
    if ($LASTEXITCODE -ne 0) { throw "A geracao dos arquivos falhou (codigo $LASTEXITCODE)." }
}

foreach ($file in $LocalFiles) {
    if (-not (Test-Path -LiteralPath $file)) {
        throw "Arquivo de teste ausente: $file. Execute sem -SkipGenerate."
    }
}

function Get-EmulatorDevices {
    $devices = @()
    foreach ($line in (& $Adb devices | Select-Object -Skip 1)) {
        if ($line -match "^(emulator-\d+)\s+(\S+)") {
            $devices += [PSCustomObject]@{ Serial = $Matches[1]; State = $Matches[2] }
        }
    }
    return @($devices)
}

if ($Serial) {
    $state = (& $Adb -s $Serial get-state 2>$null)
    if ($state -ne "device") { throw "O dispositivo $Serial nao esta conectado e autorizado." }
} else {
    $devices = Get-EmulatorDevices
    $online = @($devices | Where-Object State -eq "device")

    if ($online.Count -eq 0 -and $devices.Count -gt 0) {
        & $Adb reconnect | Out-Host
        Start-Sleep -Seconds 2
        $devices = Get-EmulatorDevices
        $online = @($devices | Where-Object State -eq "device")
    }

    if ($online.Count -eq 0 -and $devices.Count -gt 0) {
        $states = $devices | ForEach-Object { "$($_.Serial)=$($_.State)" }
        throw "Existe um emulador conectado, mas nao autorizado: $($states -join ', '). Reinicie o ADB ou aceite a autorizacao."
    }

    if ($online.Count -eq 0) {
        if (-not $AndroidSdk) { throw "Nenhum emulador conectado e Android SDK nao localizado." }
        $Emulator = Join-Path $AndroidSdk "emulator\emulator.exe"
        if (-not (Test-Path -LiteralPath $Emulator)) { throw "Emulador do Android SDK nao encontrado: $Emulator" }

        Write-Host "Iniciando o AVD $AvdName..." -ForegroundColor Cyan
        Start-Process -FilePath $Emulator -ArgumentList @("-avd", $AvdName)
        $deadline = (Get-Date).AddMinutes(2)
        do {
            Start-Sleep -Seconds 2
            $online = @(Get-EmulatorDevices | Where-Object State -eq "device")
        } until ($online.Count -gt 0 -or (Get-Date) -ge $deadline)
        if ($online.Count -eq 0) { throw "O AVD $AvdName nao ficou disponivel em ate 2 minutos." }
    }

    if ($online.Count -gt 1) {
        $serials = $online | ForEach-Object Serial
        throw "Ha mais de um emulador conectado: $($serials -join ', '). Informe -Serial."
    }
    $Serial = $online[0].Serial
}

Write-Host "Aguardando o Android concluir o boot em $Serial..." -ForegroundColor Cyan
$bootDeadline = (Get-Date).AddMinutes(2)
do {
    $bootOutput = & $Adb -s $Serial shell getprop sys.boot_completed 2>$null
    $BootCompleted = "$bootOutput".Trim()
    if ($BootCompleted -ne "1") { Start-Sleep -Seconds 2 }
} until ($BootCompleted -eq "1" -or (Get-Date) -ge $bootDeadline)
if ($BootCompleted -ne "1") { throw "O Android nao concluiu o boot em ate 2 minutos." }

& $Adb -s $Serial shell mkdir -p $RemoteDirectory
if ($LASTEXITCODE -ne 0) { throw "Nao foi possivel criar $RemoteDirectory no emulador." }

Write-Host "Enviando arquivos para $RemoteDirectory..." -ForegroundColor Cyan
foreach ($file in $LocalFiles) {
    & $Adb -s $Serial push $file "$RemoteDirectory/"
    if ($LASTEXITCODE -ne 0) { throw "Falha ao enviar $file." }
}

Write-Host "Arquivos disponiveis no emulador:" -ForegroundColor Green
& $Adb -s $Serial shell ls -lh $RemoteDirectory
if ($LASTEXITCODE -ne 0) { throw "Nao foi possivel validar os arquivos enviados." }

Write-Host "No app, toque em Importar e abra Download > E-reader-tests." -ForegroundColor Green
