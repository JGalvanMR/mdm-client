# Guardar como send-command.ps1
param(
    [string]$CommandType = "LOCK_DEVICE",
    [string]$DeviceId    = "80e43c9c719a283d",
    [string]$Parameters  = ""
)

$BASE_URL  = "http://localhost:5000"
$ADMIN_KEY = "DEV-ADMIN-KEY-SOLO-PARA-DESARROLLO-NO-USAR-EN-PROD"

# Si no se pasa DeviceId, obtener el primero registrado
if (-not $DeviceId) {
    $devices = Invoke-RestMethod `
        -Uri "$BASE_URL/api/admin/devices" `
        -Headers @{ "X-Admin-Key" = $ADMIN_KEY }
    $DeviceId = $devices.data.devices[1].deviceId
    Write-Host "Usando dispositivo: $DeviceId" -ForegroundColor Cyan
}

$body = @{
    deviceId    = $DeviceId
    commandType = $CommandType
    parameters  = if ($Parameters) { $Parameters } else { $null }
    priority    = 1
} | ConvertTo-Json

$result = Invoke-RestMethod `
    -Method POST `
    -Uri "$BASE_URL/api/admin/commands" `
    -Headers @{ "X-Admin-Key" = $ADMIN_KEY; "Content-Type" = "application/json" } `
    -Body $body

Write-Host "✓ Comando encolado: $CommandType (Id=$($result.data.commandId))" -ForegroundColor Green