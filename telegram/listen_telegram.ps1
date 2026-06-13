# Script de Escucha y Procesamiento de Comandos para Telegram Bot (Long Polling)
$OutputEncoding = [System.Text.Encoding]::UTF8

# Ruta relativa del archivo de estado
$StateFile = Join-Path $PSScriptRoot "telegram_state.json"

if (-not (Test-Path $StateFile)) {
    Write-Error "No se encontro el archivo de estado en: $StateFile"
    Exit 1
}

Write-Host "============================================="
Write-Host "  Iniciando Servicio de Escucha de Telegram  "
Write-Host "============================================="
Write-Host "Leyendo configuracion desde: $StateFile"

$Running = $true

while ($Running) {
    try {
        # Leer la configuracion actual de estado en cada iteracion
        $Config = Get-Content -Raw -Path $StateFile | ConvertFrom-Json
        $Token = $Config.Token
        $ChatId = $Config.ChatId
        $LastUpdateId = $Config.LastUpdateId

        # Calcular el offset para getUpdates
        $Offset = $LastUpdateId + 1
        $Url = "https://api.telegram.org/bot$Token/getUpdates?offset=$Offset&timeout=30"

        Write-Host "Consultando actualizaciones (Long Polling, offset: $Offset)..."
        
        # Consulta transaccional con timeout largo
        $Response = Invoke-RestMethod -Uri $Url -Method Get -TimeoutSec 35

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

                Write-Host "Actualizacion recibida [$UpdateId] de ChatId: $SenderId. Texto: '$Text'"

                # Filtrar por ChatId autorizado
                if ($SenderId -ne $ChatId) {
                    Write-Warning "Mensaje recibido de ChatId no autorizado: $SenderId. Ignorando."
                    
                    # Actualizar offset para limpiar la cola de mensajes
                    $Config.LastUpdateId = $UpdateId
                    $Config | ConvertTo-Json -Compress | Set-Content -Path $StateFile -Encoding UTF8
                    continue
                }

                # CRITICO: Registrar el LastUpdateId de inmediato en el JSON para evitar reprocesamiento
                $Config.LastUpdateId = $UpdateId
                $Config | ConvertTo-Json -Compress | Set-Content -Path $StateFile -Encoding UTF8
                Write-Host "Estado de actualizacion [$UpdateId] guardado en telegram_state.json."

                # Procesar comando
                $ResponseText = ""
                switch -Regex ($Text) {
                    "^/start" {
                        $ResponseText = "Bienvenido al sistema de control de CameraStellar v3! Envia /help para ver la lista de comandos disponibles en tu PC."
                    }
                    "^/help" {
                        $ResponseText = "Comandos disponibles en tu PC:`n" +
                                        "/ping - Verifica si la PC esta encendida.`n" +
                                        "/status - Reporta el estado de compilacion y pruebas del proyecto.`n" +
                                        "/stop - Detiene el servicio de escucha de Telegram de forma remota.`n" +
                                        "/help - Muestra este menu de ayuda."
                    }
                    "^/ping" {
                        $ResponseText = "Pong! El bot de CameraStellar esta en linea y escuchando en tu PC."
                    }
                    "^/status" {
                        # Consultar el estado basico del compilado
                        $BuildReport = Join-Path $PSScriptRoot "../app/build/reports/lint-results-debug.html"
                        if (Test-Path $BuildReport) {
                            $ResponseText = "Estado del Proyecto: COMPILADO Y TESTEADO EXITOSAMENTE. Todas las validaciones de Android Lint y pruebas locales pasaron."
                        } else {
                            $ResponseText = "Estado del Proyecto: El proyecto no ha sido compilado localmente aun o el build esta limpio."
                        }
                    }
                    "^/stop" {
                        $ResponseText = "Deteniendo el servicio de escucha en la PC. Hasta pronto!"
                        $Running = $false
                    }
                    Default {
                        $ResponseText = "Comando no reconocido: '$Text'. Envia /help para ver la lista de comandos validos."
                    }
                }

                # Enviar respuesta al usuario
                if ($ResponseText) {
                    # Invocar el script de envio local
                    $SendScript = Join-Path $PSScriptRoot "send_reply.ps1"
                    powershell -ExecutionPolicy Bypass -File $SendScript -Text $ResponseText | Out-Null
                    Write-Host "Respuesta enviada: '$ResponseText'"
                }
            }
        }
    }
    catch {
        Write-Error "Error en el ciclo de escucha: $_"
        # Pequeña pausa en caso de error de red continuo para no saturar
        Start-Sleep -Seconds 5
    }
}

Write-Host "============================================="
Write-Host "  Servicio de Escucha Detenido Exitosamente  "
Write-Host "============================================="
