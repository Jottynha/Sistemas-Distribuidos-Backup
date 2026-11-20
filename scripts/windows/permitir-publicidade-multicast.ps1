#Requires -RunAsAdministrator
param(
    [int]$Port = 5005,
    [string]$RuleName = "Chat Distribuido - Publicidade Multicast UDP 5005"
)

$ErrorActionPreference = "Stop"
if ($Port -lt 1 -or $Port -gt 65535) {
    throw "Porta UDP invalida: $Port"
}

# Autoriza somente trafego UDP originado na sub-rede local para a porta
# usada pelos receptores de publicidade. Nao abre portas TCP nem MySQL.
$existing = Get-NetFirewallRule -DisplayName $RuleName -ErrorAction SilentlyContinue
if ($existing) {
    $existing | Remove-NetFirewallRule
}

New-NetFirewallRule `
    -DisplayName $RuleName `
    -Description "Recepcao de avisos UDP multicast da publicidade do Chat Distribuido." `
    -Direction Inbound `
    -Action Allow `
    -Protocol UDP `
    -LocalPort $Port `
    -RemoteAddress LocalSubnet `
    -Profile Any | Out-Null

Write-Host "Regra de firewall criada para UDP $Port a partir da sub-rede local." -ForegroundColor Green
Write-Host "Esta regra nao habilita multicast no hotspot/roteador. Se o ponto de acesso bloquear multicast ou isolar clientes, sera necessario usar uma rede que encaminhe multicast." -ForegroundColor Yellow
Write-Host "Para remover: Remove-NetFirewallRule -DisplayName '$RuleName'"
