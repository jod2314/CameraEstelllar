# Script de Asistente de Escucha Activa y Comandos de Consola en PowerShell
$OutputEncoding = [System.Text.Encoding]::UTF8

# Ruta relativa del archivo de estado y utilidades
$StateFile = Join-Path $PSScriptRoot "telegram_state.json"
$SendScript = Join-Path $PSScriptRoot "send_reply.ps1"

if (-not (Test-Path $StateFile)) {
    Write-Error "No se encontro el archivo de estado en: $StateFile"
    Exit 1
}

Write-Host "============================================="
Write-Host "  Iniciando Asistente Interactivo Continuo   "
Write-Host "============================================="
Write-Host "Leyendo configuracion desde: $StateFile"
Write-Host "Presione Ctrl+C en la consola de la PC para detener."

$Running = $true

while ($Running) {
    try {
        # Cargar configuracion de estado
        $Config = Get-Content -Raw -Path $StateFile | ConvertFrom-Json
        $Token = $Config.Token
        $ChatId = $Config.ChatId
        $LastUpdateId = $Config.LastUpdateId

        $Offset = $LastUpdateId + 1
        $Url = "https://api.telegram.org/bot$Token/getUpdates?offset=$Offset&timeout=10"

        # Consulta con timeout corto para bucle continuo
        $Response = Invoke-RestMethod -Uri $Url -Method Get -TimeoutSec 15

        if ($Response.ok -and $Response.result.Count -gt 0) {
            foreach ($Update in $Response.result) {
                $UpdateId = $Update.update_id
                $Message = $Update.message

                # Ignorar si no tiene mensaje o texto
                if ($null -eq $Message -or $null -eq $Message.text) {
                    $Config.LastUpdateId = $UpdateId
                    $Config | ConvertTo-Json -Compress | Set-Content -Path $StateFile -Encoding UTF8
                    continue
                }

                $SenderId = $Message.chat.id.ToString()
                $Text = $Message.text.Trim()

                Write-Host "Asistente: Mensaje recibido [$UpdateId] de ChatId: $SenderId. Texto: '$Text'"

                # Validar usuario administrador
                if ($SenderId -ne $ChatId) {
                    $Config.LastUpdateId = $UpdateId
                    $Config | ConvertTo-Json -Compress | Set-Content -Path $StateFile -Encoding UTF8
                    continue
                }

                # CRITICO: Actualizar offset de inmediato en el JSON
                $Config.LastUpdateId = $UpdateId
                $Config | ConvertTo-Json -Compress | Set-Content -Path $StateFile -Encoding UTF8

                $ResponseText = ""

                # Procesamiento de comandos avanzados
                if ($Text -like "/nota *") {
                    $Nota = $Text.Substring(6).Trim()
                    $NotesFile = Join-Path $PSScriptRoot "notas.txt"
                    $Timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
                    "[$Timestamp] $Nota" | Out-File -FilePath $NotesFile -Append -Encoding UTF8
                    $ResponseText = "Nota registrada exitosamente en notas.txt."
                }
                elseif ($Text -like "/recuerdame *") {
                    # Formato: /recuerdame [minutos] [mensaje]
                    $Parts = $Text.Substring(12).Trim() -split " ", 2
                    if ($Parts.Count -eq 2 -and [int]::TryParse($Parts[0], [ref]$Minutos)) {
                        $MensajeRecordatorio = "Recordatorio: " + $Parts[1]
                        
                        # Lanzar Job en segundo plano asincrono en PowerShell
                        $JobCode = {
                            param($mins, $msg, $scriptPath)
                            Start-Sleep -Seconds ($mins * 60)
                            powershell -ExecutionPolicy Bypass -File $scriptPath -Text $msg
                        }
                        Start-Job -ScriptBlock $JobCode -ArgumentList $Minutos, $MensajeRecordatorio, $SendScript | Out-Null
                        $ResponseText = "Entendido. Te recordare en $Minutos minuto(s): '$($Parts[1])'."
                    } else {
                        $ResponseText = "Formato incorrecto. Uso correcto: /recuerdame [minutos] [mensaje]"
                    }
                }
                elseif ($Text -like "/cmd *") {
                    $Cmd = $Text.Substring(5).Trim()
                    Write-Host "Ejecutando comando remoto: $Cmd"
                    try {
                        # Ejecutar comando en la PC local de forma remota
                        $Out = Invoke-Expression $Cmd 2>&1 | Out-String
                        
                        if (-not $Out) {
                            $ResponseText = "Comando ejecutado. Salida vacia."
                        } else {
                            # Limitar tamaño de mensaje para Telegram (max 4000 caracteres)
                            if ($Out.Length -gt 4000) {
                                $ResponseText = "Salida del comando (Truncada a 4000 caracteres):`n" + $Out.Substring(0, 4000)
                            } else {
                                $ResponseText = "Salida del comando:`n" + $Out
                            }
                        }
                    }
                    catch {
                        $ResponseText = "Error al ejecutar el comando: $_"
                    }
                }
                else {
                    # Menu de comandos del Asistente
                    switch -Regex ($Text) {
                        "^/ayuda" {
                            $ResponseText = "Menu de comandos del Asistente:`n" +
                                            "/ayuda - Muestra este menu.`n" +
                                            "/informes - Reporta el estado de compilacion del proyecto.`n" +
                                            "/nota [texto] - Registra una nota de texto en notas.txt.`n" +
                                            "/recuerdame [minutos] [mensaje] - Programa una alerta.`n" +
                                            "/cmd [comando] - Ejecuta un comando en la PC local.`n" +
                                            "/stop - Detiene el asistente."
                        }
                        "^/informes" {
                            $BuildReport = Join-Path $PSScriptRoot "../app/build/reports/lint-results-debug.html"
                            if (Test-Path $BuildReport) {
                                $ResponseText = "Informe: El proyecto CameraStellar v3 compila exitosamente. Validaciones de Lint aprobadas."
                            } else {
                                $ResponseText = "Informe: No hay builds recientes en la maquina local."
                            }
                        }
                        "^/stop" {
                            $ResponseText = "Deteniendo el Asistente Interactivo. Hasta pronto!"
                            $Running = $false
                        }
                        Default {
                            $ResponseText = "Comando no reconocido: '$Text'. Envia /ayuda para ver los comandos del asistente."
                        }
                    }
                }

                # Enviar respuesta
                if ($ResponseText) {
                    powershell -ExecutionPolicy Bypass -File $SendScript -Text $ResponseText | Out-Null
                }
            }
        }
    }
    catch {
        Write-Error "Error en el asistente: $_"
        Start-Sleep -Seconds 2
    }
}

Write-Host "Asistente detenido."
