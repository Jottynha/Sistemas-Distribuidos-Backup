param(
    [string]$MavenPath = "",
    [string]$ReplicationHost = "127.0.0.1"
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$rootPom = Join-Path $repoRoot "pom.xml"
$pom = Join-Path $repoRoot "SD-v4\Chat_Middleware_Service\pom.xml"
$caFile = Join-Path $repoRoot "SD-v4\certificados\chat-root-ca.cer"

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
if (-not (Test-Path -LiteralPath $caFile -PathType Leaf)) {
    throw "CA publica nao encontrada: $caFile"
}
if ([string]::IsNullOrWhiteSpace($ReplicationHost)) {
    throw "ReplicationHost nao pode ficar vazio."
}

$maven = Get-MavenCommand -ProvidedPath $MavenPath
$dbPasswordSecure = Read-Host "Senha do usuario MySQL chat_app" -AsSecureString
$replicationTokenSecure = Read-Host "Informe exatamente o mesmo token usado no Middleware B" -AsSecureString

try {
    $env:CHAT_DB_HOST = "127.0.0.1"
    $env:CHAT_DB_PORT = "3306"
    $env:CHAT_DB_NAME = "chat_distribuido"
    $env:CHAT_DB_USER = "chat_app"
    $env:CHAT_DB_PASSWORD = Convert-SecureStringToPlainText $dbPasswordSecure
    $env:CHAT_DB_SSL_MODE = "PREFERRED"

    $env:CHAT_REPLICATION_ENABLED = "true"
    $env:CHAT_REPLICATION_HOST = $ReplicationHost
    $env:CHAT_REPLICATION_PORT = "6666"
    $env:CHAT_REPLICATION_TOKEN = Convert-SecureStringToPlainText $replicationTokenSecure
    $env:CHAT_REPLICATION_CA_FILE = $caFile

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
    Write-Host "Iniciando Middleware A (principal) com replicacao sincrona..." -ForegroundColor Cyan
    Write-Host "Banco: chat_distribuido | Destino: ${ReplicationHost}:6666"
    Write-Host "O ACK ao cliente sera enviado somente apos a confirmacao do Banco B."
    Write-Host ""

    # Passa cada propriedade -D como uma unica string para evitar que
    # Windows PowerShell 5.1 separe o argumento no ponto do nome da propriedade.
    $runArguments = @(
        "-f", $pom,
        "--no-transfer-progress",
        "-Dexec.mainClass=pacote.Run",
        "-Dexec.args=--headless",
        "-Dexec.classpathScope=runtime",
        "process-classes",
        "org.codehaus.mojo:exec-maven-plugin:3.6.3:java"
    )

    & $maven @runArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Middleware A encerrou com codigo $LASTEXITCODE."
    }
}
finally {
    Remove-Item Env:CHAT_DB_HOST -ErrorAction SilentlyContinue
    Remove-Item Env:CHAT_DB_PORT -ErrorAction SilentlyContinue
    Remove-Item Env:CHAT_DB_NAME -ErrorAction SilentlyContinue
    Remove-Item Env:CHAT_DB_USER -ErrorAction SilentlyContinue
    Remove-Item Env:CHAT_DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:CHAT_DB_SSL_MODE -ErrorAction SilentlyContinue
    Remove-Item Env:CHAT_REPLICATION_ENABLED -ErrorAction SilentlyContinue
    Remove-Item Env:CHAT_REPLICATION_HOST -ErrorAction SilentlyContinue
    Remove-Item Env:CHAT_REPLICATION_PORT -ErrorAction SilentlyContinue
    Remove-Item Env:CHAT_REPLICATION_TOKEN -ErrorAction SilentlyContinue
    Remove-Item Env:CHAT_REPLICATION_CA_FILE -ErrorAction SilentlyContinue
    $dbPasswordSecure = $null
    $replicationTokenSecure = $null
}
