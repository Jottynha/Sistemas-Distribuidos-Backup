param(
    [Parameter(Mandatory = $true)]
    [string]$PrimaryHost,
    [Parameter(Mandatory = $true)]
    [string]$BackupHost,
    [int]$DesktopPort = 6664,
    [int]$PythonPort = 6665,
    [int]$WebPort = 8443,
    [string]$MulticastGroup = "239.255.42.99",
    [int]$MulticastPort = 5005,
    [string]$MulticastInterface = "",
    [int]$FailoverWaitSeconds = 25
)

$ErrorActionPreference = "Stop"
foreach ($hostValue in @($PrimaryHost, $BackupHost)) {
    if ([string]::IsNullOrWhiteSpace($hostValue) -or $hostValue -match '[\s/\\@?#]') {
        throw "Host invalido: $hostValue"
    }
}
foreach ($port in @($DesktopPort, $PythonPort, $WebPort, $MulticastPort)) {
    if ($port -lt 1 -or $port -gt 65535) { throw "Porta invalida: $port" }
}
if ($FailoverWaitSeconds -lt 5 -or $FailoverWaitSeconds -gt 60) { throw "FailoverWaitSeconds deve estar entre 5 e 60." }

$values = @{
    CHAT_SERVER_HOST = $PrimaryHost.Trim()
    CHAT_SERVER_BACKUP_HOST = $BackupHost.Trim()
    CHAT_SERVER_BACKUP_HOSTS = $BackupHost.Trim()
    CHAT_SERVER_DESKTOP_PORT = [string]$DesktopPort
    CHAT_SERVER_PYTHON_PORT = [string]$PythonPort
    CHAT_SERVER_WEB_PORT = [string]$WebPort
    CHAT_AD_MULTICAST_GROUP = $MulticastGroup
    CHAT_AD_MULTICAST_PORT = [string]$MulticastPort
    CHAT_FAILOVER_CLIENT_WAIT_SECONDS = [string]$FailoverWaitSeconds
}
if ($MulticastInterface) { $values.CHAT_AD_MULTICAST_INTERFACE = $MulticastInterface }

foreach ($entry in $values.GetEnumerator()) {
    [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, "User")
}

Write-Host "Configuracao salva para o usuario atual." -ForegroundColor Green
Write-Host "Middleware principal: $PrimaryHost"
Write-Host "Middleware reserva: $BackupHost"
Write-Host "Portas: Desktop $DesktopPort | Python $PythonPort | HTTPS $WebPort"
Write-Host "Multicast: $MulticastGroup`:$MulticastPort"
Write-Host "Janela de reconexao dos clientes: $FailoverWaitSeconds s"
if ($MulticastInterface) { Write-Host "Interface multicast local: $MulticastInterface" }
Write-Host "Feche e abra novamente os clientes e o GlassFish para herdarem as variaveis."
