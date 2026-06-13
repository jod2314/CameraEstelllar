# Script para obtener mensajes pendientes del Bot de Telegram en formato JSON
$OutputEncoding = [System.Text.Encoding]::UTF8

# Ruta relativa del archivo de estado
$StateFile = Join-Path $PSScriptRoot "telegram_state.json"

if (-not (Test-Path $StateFile)) {
    Write-Error "No se encontro el archivo de estado en: $StateFile"
    Exit 1
}

# Leer la configuracion actual de estado
$Config = Get-Content -Raw -Path $StateFile | ConvertFrom-Json
$Token = $Config.Token
$ChatId = $Config.ChatId
$LastUpdateId = $Config.LastUpdateId

# Calcular el offset para getUpdates (sin timeout, consulta rapida instantanea)
$Offset = $LastUpdateId + 1
$Url = "https://api.telegram.org/bot$Token/getUpdates?offset=$Offset&timeout=0"

$PendingTasks = @()

try {
    $Response = Invoke-RestMethod -Uri $Url -Method Get -TimeoutSec 10

    if ($Response.ok -and $Response.result.Count -gt 0) {
        foreach ($Update in $Response.result) {
            $UpdateId = $Update.update_id
            $Message = $Update.message
            
            # Ignorar si no tiene mensaje o texto
            if ($null -eq $Message -or $null -eq $Message.text) {
                # Actualizar offset de todos modos para limpiar la cola
                $Config.LastUpdateId = $UpdateId
                $Config | ConvertTo-Json -Compress | Set-Content -Path $StateFile -Encoding UTF8
                continue
            }

            $SenderId = $Message.chat.id.ToString()
            $Text = $Message.text.Trim()

            # Solo procesar si proviene del ChatId autorizado
            if ($SenderId -eq $ChatId) {
                # Acumular la tarea
                $Task = @{
                    update_id = $UpdateId
                    text      = $Text
                }
                $PendingTasks += $Task

                # CRITICO: Actualizar offset de inmediato en el JSON para no reprocesar en la proxima iteracion
                $Config.LastUpdateId = $UpdateId
                $Config | ConvertTo-Json -Compress | Set-Content -Path $StateFile -Encoding UTF8
            } else {
                # Actualizar offset para limpiar mensajes no autorizados
                $Config.LastUpdateId = $UpdateId
                $Config | ConvertTo-Json -Compress | Set-Content -Path $StateFile -Encoding UTF8
            }
        }
    }
}
catch {
    Write-Error "Error al consultar getUpdates: $_"
}

# Retornar las tareas pendientes en formato JSON
$PendingTasks | ConvertTo-Json -Compress
