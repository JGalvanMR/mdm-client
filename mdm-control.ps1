# ================================================================
# mdm-control.ps1 — Panel de control MDM desde terminal
# Uso: .\mdm-control.ps1
# ================================================================

# ── Configuración ─────────────────────────────────────────────────
$BASE_URL  = "http://localhost:5000"
$ADMIN_KEY = "DEV-ADMIN-KEY-SOLO-PARA-DESARROLLO-NO-USAR-EN-PROD"

# ── Colores ────────────────────────────────────────────────────────
function Write-Title  ($t) { Write-Host "`n$t" -ForegroundColor Cyan }
function Write-Ok     ($t) { Write-Host "  ✓ $t" -ForegroundColor Green }
function Write-Fail   ($t) { Write-Host "  ✗ $t" -ForegroundColor Red }
function Write-Info   ($t) { Write-Host "  · $t" -ForegroundColor Gray }
function Write-Warn   ($t) { Write-Host "  ⚠ $t" -ForegroundColor Yellow }

# ── Helper HTTP ────────────────────────────────────────────────────
function Call {
    param(
        [string]$Method = "GET",
        [string]$Path,
        [hashtable]$Headers = @{},
        [object]$Body = $null
    )
    try {
        $params = @{
            Method  = $Method
            Uri     = "$BASE_URL$Path"
            Headers = $Headers + @{ "Content-Type" = "application/json" }
        }
        if ($Body) { $params.Body = ($Body | ConvertTo-Json -Depth 5) }
        return Invoke-RestMethod @params
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        Write-Fail "HTTP $code — $($_.ErrorDetails.Message)"
        return $null
    }
}

function AdminCall {
    param([string]$Method = "GET", [string]$Path, [object]$Body = $null)
    return Call -Method $Method -Path $Path `
        -Headers @{ "X-Admin-Key" = $ADMIN_KEY } -Body $Body
}

# ── Obtener lista de dispositivos ─────────────────────────────────
function Get-Devices {
    $r = AdminCall -Path "/api/admin/devices"
    if ($r -and $r.success) { return $r.data.devices }
    return @()
}

# ── Seleccionar dispositivo ────────────────────────────────────────
function Select-Device {
    $devices = Get-Devices
    if ($devices.Count -eq 0) {
        Write-Warn "No hay dispositivos registrados aún."
        Write-Info "Abre la app en el dispositivo Android y espera ~30 segundos."
        return $null
    }

    Write-Title "DISPOSITIVOS REGISTRADOS"
    Write-Host ""
    for ($i = 0; $i -lt $devices.Count; $i++) {
        $d      = $devices[$i]
        $status = if ($d.isOnline) { "[ONLINE]  " } else { "[OFFLINE] " }
        $color  = if ($d.isOnline) { "Green" } else { "DarkGray" }
        $bat    = if ($d.batteryLevel) { " 🔋$($d.batteryLevel)%" } else { "" }
        $kiosk  = if ($d.kioskModeEnabled) { " [KIOSK]" } else { "" }
        Write-Host "  [$($i+1)] " -NoNewline
        Write-Host "$status" -ForegroundColor $color -NoNewline
        Write-Host "$($d.deviceId)$bat$kiosk — $($d.deviceName)" -ForegroundColor White
    }

    if ($devices.Count -eq 1) {
        Write-Host ""
        Write-Info "Usando único dispositivo: $($devices[0].deviceId)"
        return $devices[0].deviceId
    }

    Write-Host ""
    $sel = Read-Host "  Selecciona dispositivo [1-$($devices.Count)]"
    $idx = [int]$sel - 1
    if ($idx -ge 0 -and $idx -lt $devices.Count) {
        return $devices[$idx].deviceId
    }
    Write-Fail "Selección inválida."
    return $null
}

# ── Enviar comando ─────────────────────────────────────────────────
function Send-Command {
    param([string]$DeviceId, [string]$CmdType, [string]$Params = $null, [int]$Priority = 5)

    $body = @{ deviceId = $DeviceId; commandType = $CmdType; priority = $Priority }
    if ($Params) { $body.parameters = $Params }

    $r = AdminCall -Method POST -Path "/api/admin/commands" -Body $body
    if ($r -and $r.success) {
        Write-Ok "Comando encolado → $CmdType (Id=$($r.data.commandId))"
        return $r.data.commandId
    }
    return -1
}

# ── Esperar y mostrar resultado ────────────────────────────────────
function Wait-Result {
    param([int]$CommandId, [int]$TimeoutSec = 90)

    Write-Info "Esperando resultado (máx ${TimeoutSec}s)..."
    $elapsed = 0
    while ($elapsed -lt $TimeoutSec) {
        Start-Sleep -Seconds 5
        $elapsed += 5

        $r = AdminCall -Path "/api/admin/commands/$CommandId"
        if (-not $r) { continue }

        $status = $r.data.status
        Write-Host "  · [$elapsed s] Estado: $status" -ForegroundColor DarkGray

        if ($status -eq "Executed") {
            Write-Ok "Ejecutado en $elapsed segundos."
            if ($r.data.result) {
                Write-Host "`n  Resultado:" -ForegroundColor Cyan
                try {
                    $parsed = $r.data.result | ConvertFrom-Json
                    $parsed | ConvertTo-Json -Depth 5 |
                        ForEach-Object { Write-Host "  $_" -ForegroundColor White }
                } catch {
                    Write-Host "  $($r.data.result)" -ForegroundColor White
                }
            }
            return
        }

        if ($status -in @("Failed","Cancelled","Expired")) {
            Write-Fail "Estado final: $status"
            if ($r.data.errorMessage) {
                Write-Fail "Error: $($r.data.errorMessage)"
            }
            return
        }
    }
    Write-Warn "Timeout. El dispositivo no respondió en ${TimeoutSec}s."
    Write-Info "Verifica que la app esté corriendo y tenga conexión."
}

# ── Mostrar detalle del dispositivo ───────────────────────────────
function Show-DeviceDetail {
    param([string]$DeviceId)
    $r = AdminCall -Path "/api/admin/devices/$DeviceId"
    if (-not $r) { return }
    $d = $r.data

    $statusTxt   = if ($d.isOnline)          { "ONLINE"  } else { "OFFLINE" }
    $statusColor = if ($d.isOnline)          { "Green"   } else { "Red"     }
    $kioskTxt    = if ($d.kioskModeEnabled)  { "Activo"  } else { "Inactivo"}
    $cameraTxt   = if ($d.cameraDisabled)    { "Bloqueada" } else { "Habilitada" }
    $lastSeen    = if ($d.lastSeen) { $d.lastSeen } else { "Nunca" }

    Write-Host ""
    Write-Host "  ┌─────────────────────────────────────────┐" -ForegroundColor DarkGray
    Write-Host "  │  DISPOSITIVO: $($d.deviceId.PadRight(27))│" -ForegroundColor White
    Write-Host "  ├─────────────────────────────────────────┤" -ForegroundColor DarkGray
    Write-Host "  │  Estado:    " -NoNewline -ForegroundColor DarkGray
    Write-Host "$($statusTxt.PadRight(29))│" -ForegroundColor $statusColor
    Write-Host "  │  Nombre:    $($d.deviceName.PadRight(29))│" -ForegroundColor Gray
    Write-Host "  │  Modelo:    $($d.model.PadRight(29))│" -ForegroundColor Gray
    Write-Host "  │  Android:   $($d.androidVersion.PadRight(29))│" -ForegroundColor Gray
    Write-Host "  │  Batería:   $("$($d.batteryLevel)%".PadRight(29))│" -ForegroundColor Gray
    Write-Host "  │  IP:        $($d.ipAddress.PadRight(29))│" -ForegroundColor Gray
    Write-Host "  │  Polls:     $($d.pollCount.ToString().PadRight(29))│" -ForegroundColor Gray
    Write-Host "  │  Kiosk:     $($kioskTxt.PadRight(29))│" -ForegroundColor Gray
    Write-Host "  │  Cámara:    $($cameraTxt.PadRight(29))│" -ForegroundColor Gray
    Write-Host "  │  Pendientes:$($d.pendingCommandsCount.ToString().PadRight(29))│" -ForegroundColor Gray
    Write-Host "  │  Último:    $($lastSeen.ToString().Substring(0, [Math]::Min(29,$lastSeen.ToString().Length)).PadRight(29))│" -ForegroundColor Gray
    Write-Host "  └─────────────────────────────────────────┘" -ForegroundColor DarkGray
    Write-Host ""
}

# ── Menú de comandos ───────────────────────────────────────────────
function Show-CommandMenu {
    param([string]$DeviceId)

    while ($true) {
        Show-DeviceDetail -DeviceId $DeviceId

        Write-Host "  COMANDOS DISPONIBLES" -ForegroundColor Cyan
        Write-Host "  ─────────────────────────────────────────" -ForegroundColor DarkGray
        Write-Host "  [1] Bloquear pantalla       (LOCK_DEVICE)"        -ForegroundColor White
        Write-Host "  [2] Deshabilitar cámara     (DISABLE_CAMERA)"     -ForegroundColor White
        Write-Host "  [3] Habilitar cámara        (ENABLE_CAMERA)"      -ForegroundColor White
        Write-Host "  [4] Activar Kiosk Mode      (ENABLE_KIOSK_MODE)"  -ForegroundColor White
        Write-Host "  [5] Desactivar Kiosk Mode   (DISABLE_KIOSK_MODE)" -ForegroundColor White
        Write-Host "  [6] Info del dispositivo    (GET_DEVICE_INFO)"    -ForegroundColor White
        Write-Host "  [7] Timeout de pantalla     (SET_SCREEN_TIMEOUT)" -ForegroundColor White
        Write-Host "  [8] Ver historial de comandos"                     -ForegroundColor White
        Write-Host "  [9] Cancelar comandos pendientes"                  -ForegroundColor Yellow
        Write-Host "  [0] Volver al menú principal"                      -ForegroundColor DarkGray
        Write-Host ""

        $op = Read-Host "  Opción"

        switch ($op) {
            "1" {
                Write-Title "BLOQUEAR PANTALLA"
                $id = Send-Command -DeviceId $DeviceId -CmdType "LOCK_DEVICE" -Priority 1
                if ($id -gt 0) { Wait-Result -CommandId $id }
            }
            "2" {
                Write-Title "DESHABILITAR CÁMARA"
                $id = Send-Command -DeviceId $DeviceId -CmdType "DISABLE_CAMERA" -Priority 3
                if ($id -gt 0) { Wait-Result -CommandId $id }
            }
            "3" {
                Write-Title "HABILITAR CÁMARA"
                $id = Send-Command -DeviceId $DeviceId -CmdType "ENABLE_CAMERA" -Priority 3
                if ($id -gt 0) { Wait-Result -CommandId $id }
            }
            "4" {
                Write-Title "ACTIVAR KIOSK MODE"
                Write-Warn "El dispositivo quedará bloqueado en la app MDM."
                $confirm = Read-Host "  ¿Confirmar? (s/n)"
                if ($confirm -eq "s") {
                    $id = Send-Command -DeviceId $DeviceId -CmdType "ENABLE_KIOSK_MODE" -Priority 2
                    if ($id -gt 0) { Wait-Result -CommandId $id }
                }
            }
            "5" {
                Write-Title "DESACTIVAR KIOSK MODE"
                $id = Send-Command -DeviceId $DeviceId -CmdType "DISABLE_KIOSK_MODE" -Priority 2
                if ($id -gt 0) { Wait-Result -CommandId $id }
            }
            "6" {
                Write-Title "INFO DEL DISPOSITIVO"
                $id = Send-Command -DeviceId $DeviceId -CmdType "GET_DEVICE_INFO" -Priority 5
                if ($id -gt 0) { Wait-Result -CommandId $id -TimeoutSec 60 }
            }
            "7" {
                Write-Title "TIMEOUT DE PANTALLA"
                $sec = Read-Host "  Segundos (5-3600)"
                if ([int]$sec -ge 5 -and [int]$sec -le 3600) {
                    $params = "{`"seconds`":$sec}"
                    $id = Send-Command -DeviceId $DeviceId `
                        -CmdType "SET_SCREEN_TIMEOUT" -Params $params -Priority 5
                    if ($id -gt 0) { Wait-Result -CommandId $id }
                } else {
                    Write-Fail "Valor fuera de rango (5-3600)."
                }
            }
            "8" {
                Write-Title "HISTORIAL DE COMANDOS"
                $r = AdminCall -Path "/api/admin/devices/$DeviceId/commands?page=1&pageSize=15"
                if ($r -and $r.success) {
                    $cmds = $r.data.items
                    Write-Host ""
                    Write-Host ("  {0,-5} {1,-22} {2,-12} {3,-10}" -f "ID","TIPO","ESTADO","PRIORIDAD") `
                        -ForegroundColor Cyan
                    Write-Host "  ────────────────────────────────────────────────────" `
                        -ForegroundColor DarkGray
                    foreach ($c in $cmds) {
                        $color = switch ($c.status) {
                            "Executed"  { "Green"    }
                            "Failed"    { "Red"      }
                            "Pending"   { "Yellow"   }
                            "Sent"      { "Cyan"     }
                            "Cancelled" { "DarkGray" }
                            default     { "White"    }
                        }
                        Write-Host ("  {0,-5} {1,-22} " -f $c.id, $c.commandType) -NoNewline
                        Write-Host ("{0,-12}" -f $c.status) -ForegroundColor $color -NoNewline
                        Write-Host ("{0,-10}" -f $c.priority)
                    }
                    Write-Host ""
                    Write-Info "Total: $($r.data.total) comandos."
                }
            }
            "9" {
                Write-Title "CANCELAR COMANDOS PENDIENTES"
                Write-Warn "Se cancelarán todos los comandos en estado Pending/Sent."
                $confirm = Read-Host "  ¿Confirmar? (s/n)"
                if ($confirm -eq "s") {
                    $r = AdminCall -Method DELETE `
                        -Path "/api/admin/devices/$DeviceId/commands/pending"
                    if ($r -and $r.success) { Write-Ok $r.message }
                }
            }
            "0" { return }
            default { Write-Fail "Opción inválida." }
        }

        Write-Host ""
        Read-Host "  Presiona Enter para continuar"
    }
}

# ── Menú principal ─────────────────────────────────────────────────
function Show-MainMenu {
    while ($true) {
        Clear-Host
        Write-Host @"

  ╔══════════════════════════════════════════╗
  ║         MDM CONTROL PANEL v1.0           ║
  ║         Servidor: $BASE_URL
  ╚══════════════════════════════════════════╝
"@ -ForegroundColor Cyan

        # Verificar servidor
        try {
            $health = Invoke-RestMethod -Uri "$BASE_URL/health" -TimeoutSec 3
            Write-Host "  Estado del servidor: " -NoNewline
            Write-Host "ONLINE ✓" -ForegroundColor Green
        } catch {
            Write-Host "  Estado del servidor: " -NoNewline
            Write-Host "OFFLINE ✗ — Inicia el servidor con: dotnet run" -ForegroundColor Red
        }

        # Resumen de dispositivos
        $devices = Get-Devices
        $online  = ($devices | Where-Object { $_.isOnline }).Count
        Write-Host "  Dispositivos: $($devices.Count) registrados, $online online" -ForegroundColor Gray
        Write-Host ""

        Write-Host "  [1] Controlar dispositivo" -ForegroundColor White
        Write-Host "  [2] Ver todos los dispositivos" -ForegroundColor White
        Write-Host "  [3] Stats del sistema" -ForegroundColor White
        Write-Host "  [4] Ver logs de Android en tiempo real" -ForegroundColor White
        Write-Host "  [Q] Salir" -ForegroundColor DarkGray
        Write-Host ""

        $op = Read-Host "  Opción"

        switch ($op.ToUpper()) {
            "1" {
                $deviceId = Select-Device
                if ($deviceId) {
                    Show-CommandMenu -DeviceId $deviceId
                }
            }
            "2" {
                Write-Title "TODOS LOS DISPOSITIVOS"
                $devices = Get-Devices
                if ($devices.Count -eq 0) {
                    Write-Warn "Sin dispositivos registrados."
                } else {
                    Write-Host ""
                    foreach ($d in $devices) {
                        $s = if ($d.isOnline) { "ONLINE " } else { "OFFLINE" }
                        $c = if ($d.isOnline) { "Green"  } else { "DarkGray" }
                        Write-Host "  [$s] " -ForegroundColor $c -NoNewline
                        Write-Host "$($d.deviceId) — $($d.deviceName)" -ForegroundColor White
                        Write-Host "         Batería: $($d.batteryLevel)%  " -NoNewline -ForegroundColor Gray
                        Write-Host "Kiosk: $($d.kioskModeEnabled)  " -NoNewline -ForegroundColor Gray
                        Write-Host "Cámara bloqueada: $($d.cameraDisabled)" -ForegroundColor Gray
                    }
                }
                Write-Host ""
                Read-Host "  Presiona Enter para continuar"
            }
            "3" {
                Write-Title "STATS DEL SISTEMA"
                $r = AdminCall -Path "/api/admin/stats"
                if ($r -and $r.success) {
                    $s = $r.data
                    Write-Ok "Total dispositivos:  $($s.totalDevices)"
                    Write-Ok "Online ahora:        $($s.onlineDevices)"
                    Write-Ok "Comandos pendientes: $($s.pendingCommands)"
                    Write-Ok "Ejecutados (24h):    $($s.executedLast24h)"
                    Write-Ok "Fallidos (24h):      $($s.failedLast24h)"
                    Write-Info "Hora servidor: $($s.serverTime)"
                }
                Write-Host ""
                Read-Host "  Presiona Enter para continuar"
            }
            "4" {
                Write-Title "LOGS DE ANDROID EN TIEMPO REAL"
                Write-Info "Presiona Ctrl+C para detener."
                Write-Host ""
                adb logcat -s `
                    "MdmPollingService" `
                    "RegistrationManager" `
                    "ApiClient" `
                    "CommandExecutor" `
                    "DeviceOwnerReceiver" `
                    "MainActivity"
            }
            "Q" {
                Write-Host "`n  Hasta luego.`n" -ForegroundColor Cyan
                exit
            }
            default { Write-Fail "Opción inválida." }
        }
    }
}

# ── Punto de entrada ───────────────────────────────────────────────
Show-MainMenu