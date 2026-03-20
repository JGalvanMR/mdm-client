# Ver dispositivos registrados (aparece tras el primer poll de la app)
Invoke-RestMethod `
  -Uri "http://localhost:5000/api/admin/devices" `
  -Headers @{ "X-Admin-Key" = "DEV-ADMIN-KEY-SOLO-PARA-DESARROLLO-NO-USAR-EN-PROD" } |
  ConvertTo-Json -Depth 5

# ── Prueba 1: Bloquear pantalla ───────────────────────────────────
.\send-command.ps1 -CommandType "LOCK_DEVICE"
# El dispositivo debe bloquearse en los próximos 30 segundos

# ── Prueba 2: Deshabilitar cámara ─────────────────────────────────
.\send-command.ps1 -CommandType "DISABLE_CAMERA"
# Abre la cámara en el dispositivo — debe mostrar error o no abrir

# ── Prueba 3: Obtener info del dispositivo ────────────────────────
.\send-command.ps1 -CommandType "GET_DEVICE_INFO"
# En ~30s ver el resultado:
Start-Sleep -Seconds 35
Invoke-RestMethod `
  -Uri "http://192.168.123.155:5000/api/admin/commands/3" `
  -Headers @{ "X-Admin-Key" = "DEV-ADMIN-KEY-SOLO-PARA-DESARROLLO-NO-USAR-EN-PROD" } |
  ConvertTo-Json -Depth 5

# ── Prueba 4: Activar Kiosk Mode ──────────────────────────────────
.\send-command.ps1 -CommandType "ENABLE_KIOSK_MODE"
# El dispositivo queda bloqueado en la app MDM — no puede salir

# ── Prueba 5: Desactivar Kiosk Mode ──────────────────────────────
.\send-command.ps1 -CommandType "DISABLE_KIOSK_MODE"
# El dispositivo vuelve al modo normal

# ── Prueba 6: Rehabilitar cámara ──────────────────────────────────
.\send-command.ps1 -CommandType "ENABLE_CAMERA"

# ── Prueba 7: Configurar timeout de pantalla ──────────────────────
.\send-command.ps1 -CommandType "SET_SCREEN_TIMEOUT" -Parameters '{"seconds":30}'