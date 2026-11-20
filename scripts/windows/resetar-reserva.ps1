$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$marker = Join-Path $repoRoot "SD-v4\Chat_Middleware_Service\data\middleware-reserva-ativo.flag"
$port = 6666

Write-Host "Este procedimento remove somente o marcador que informa que o Middleware B foi promovido." -ForegroundColor Yellow
Write-Host "O Middleware B precisa estar totalmente encerrado antes de continuar."
Write-Host "ATENCAO: depois que B assumiu, novas mensagens podem existir somente no Banco B." -ForegroundColor Red
Write-Host "Antes de voltar A a principal, sincronize o Banco A com o Banco B e valide os IDs." -ForegroundColor Red

$teste = Test-NetConnection -ComputerName 127.0.0.1 -Port $port -WarningAction SilentlyContinue
if ($teste.TcpTestSucceeded) {
    throw "A porta $port ainda esta aberta. Encerre o Middleware B (Ctrl+C) antes de resetar o papel."
}

if (-not (Test-Path -LiteralPath $marker -PathType Leaf)) {
    Write-Host "Nenhum marcador de promocao foi encontrado. O Banco B ja iniciara como reserva." -ForegroundColor Green
    exit 0
}

$confirmacaoBanco = Read-Host "Confirme que o Banco A ja recebeu todas as mensagens do Banco B. Digite BANCO_A_SINCRONIZADO"
if ($confirmacaoBanco -cne "BANCO_A_SINCRONIZADO") {
    Write-Host "Operacao cancelada. Nao remova o marcador sem sincronizar os bancos."
    exit 0
}

$confirmacao = Read-Host "Para remover o marcador e permitir novo failover, digite RESETAR"
if ($confirmacao -cne "RESETAR") {
    Write-Host "Operacao cancelada."
    exit 0
}

Remove-Item -LiteralPath $marker -Force
Write-Host "Marcador removido. Inicie primeiro o Middleware B em --replica-only e depois o Middleware A." -ForegroundColor Green
