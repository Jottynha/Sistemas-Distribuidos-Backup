param(
    [string]$MavenPath = "",
    [string]$DatabaseHost = "127.0.0.1",
    [int]$DatabasePort = 3306,
    [int]$PromotionTimeoutMs = 10000,
    [int]$StartupGraceMs = 20000,
    [string]$MulticastGroup = "239.255.42.99",
    [int]$MulticastPort = 5005,
    [int]$AdvertisementIntervalSeconds = 10,
    [string]$MulticastInterface = ""
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$rootPom = Join-Path $repoRoot "pom.xml"
$pom = Join-Path $repoRoot "SD-v4\Chat_Middleware_Service\pom.xml"

function Get-MavenCommand {
    param([string]$ProvidedPath)
    if ($ProvidedPath) {
        if (-not (Test-Path -LiteralPath $ProvidedPath -PathType Leaf)) {
            throw "Maven nao encontrado em: $ProvidedPath"
        }
        return $ProvidedPath
    }

    $fromPath = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($fromPath) { return $fromPath.Source }

    $candidates = @(
        (Join-Path $env:ProgramFiles "Apache NetBeans\java\maven\bin\mvn.cmd"),
        (Join-Path $env:ProgramFiles "NetBeans\java\maven\bin\mvn.cmd")
    )

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
    }

    throw "Maven nao encontrado. Passe -MavenPath com o caminho completo para mvn.cmd."
}

function Convert-SecureStringToPlainText {
    param([Security.SecureString]$SecureValue)
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureValue)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

if (-not (Test-Path -LiteralPath $rootPom -PathType Leaf)) {
    throw "POM agregador nao encontrado: $rootPom"
}
if (-not (Test-Path -LiteralPath $pom -PathType Leaf)) {
    throw "POM do middleware nao encontrado: $pom"
}

$maven = Get-MavenCommand -ProvidedPath $MavenPath
$dbPasswordSecure = Read-Host "Senha do usuario MySQL chat_app_b" -AsSecureString
$replicationTokenSecure = Read-Host "Token compartilhado de replicacao (use o mesmo no Middleware A)" -AsSecureString

try {
    $env:CHAT_DB_HOST = $DatabaseHost
    $env:CHAT_DB_PORT = [string]$DatabasePort
    $env:CHAT_DB_NAME = "chat_distribuido_b"
    $env:CHAT_DB_USER = "chat_app_b"
    $env:CHAT_DB_PASSWORD = Convert-SecureStringToPlainText $dbPasswordSecure
    $env:CHAT_DB_SSL_MODE = "PREFERRED"
    $env:CHAT_REPLICATION_PORT = "6666"
    $env:CHAT_REPLICATION_TOKEN = Convert-SecureStringToPlainText $replicationTokenSecure
    $env:CHAT_REPLICATION_ENABLED = "false"
    $env:CHAT_FAILOVER_TIMEOUT_MS = [string]$PromotionTimeoutMs
    $env:CHAT_FAILOVER_STARTUP_GRACE_MS = [string]$StartupGraceMs
    $env:CHAT_AD_MULTICAST_GROUP = $MulticastGroup
    $env:CHAT_AD_MULTICAST_PORT = [string]$MulticastPort
    $env:CHAT_AD_INTERVAL_SECONDS = [string]$AdvertisementIntervalSeconds
    if ($MulticastInterface) { $env:CHAT_AD_MULTICAST_INTERFACE = $MulticastInterface }

    Write-Host "Compilando protocolo e middleware pelo POM agregador..." -ForegroundColor Cyan
    $buildArguments = @(
        "-f", $rootPom,
        "-pl", "SD-v4/Chat_Middleware_Service",
        "-am",
        "--no-transfer-progress",
        "install"
    )
    & $maven @buildArguments
    if ($LASTEXITCODE -ne 0) {
        throw "A compilacao do middleware falhou (codigo $LASTEXITCODE)."
    }

    Write-Host ""
    Write-Host "Iniciando Middleware B em modo reserva (somente replicacao)..." -ForegroundColor Cyan
    Write-Host "Banco: chat_distribuido_b | Receptor TLS: 6666"
    Write-Host "Mantenha esta janela aberta enquanto o Middleware B estiver ativo."
    Write-Host ""

    # Em Windows PowerShell 5.1, as propriedades -D do Maven devem ser
    # passadas como elementos de string individuais. Sem as aspas, o parser
    # pode dividir -Dexec.mainClass e o Maven interpreta .mainClass=... como fase.
    $runArguments = @(
        "-f", $pom,
        "--no-transfer-progress",
        "-Dexec.mainClass=pacote.Run",
        "-Dexec.args=--replica-only",
        "-Dexec.classpathScope=runtime",
        "process-classes",
        "org.codehaus.mojo:exec-maven-plugin:3.6.3:java"
    )

    & $maven @runArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Middleware B encerrou com codigo $LASTEXITCODE."
    }
}
finally {
    Remove-Item Env:CHAT_DB_HOST -ErrorAction SilentlyContinue
    Remove-Item Env:CHAT_DB_PORT -ErrorAction SilentlyContinue
    Remove-Item Env:CHAT_DB_NAME -ErrorAction SilentlyContinue
    Remove-Item Env:CHAT_DB_USER -ErrorAction SilentlyContinue
    Remove-Item Env:CHAT_DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:CHAT_DB_SSL_MODE -ErrorAction SilentlyContinue
    Remove-Item Env:CHAT_REPLICATION_PORT -ErrorAction SilentlyContinue
    Remove-Item Env:CHAT_REPLICATION_TOKEN -ErrorAction SilentlyContinue
    Remove-Item Env:CHAT_REPLICATION_ENABLED -ErrorAction SilentlyContinue
    Remove-Item Env:CHAT_FAILOVER_TIMEOUT_MS -ErrorAction SilentlyContinue
    Remove-Item Env:CHAT_FAILOVER_STARTUP_GRACE_MS -ErrorAction SilentlyContinue
    Remove-Item Env:CHAT_AD_MULTICAST_GROUP -ErrorAction SilentlyContinue
    Remove-Item Env:CHAT_AD_MULTICAST_PORT -ErrorAction SilentlyContinue
    Remove-Item Env:CHAT_AD_INTERVAL_SECONDS -ErrorAction SilentlyContinue
    Remove-Item Env:CHAT_AD_MULTICAST_INTERFACE -ErrorAction SilentlyContinue
    $dbPasswordSecure = $null
    $replicationTokenSecure = $null
}
