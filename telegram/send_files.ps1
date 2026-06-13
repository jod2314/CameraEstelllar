# Parámetros del script (deben ser la primera instrucción)
param(
    [string]$FilePath = "",
    [string]$Caption = "Reporte de actividades generado automáticamente"
)

# Obtener la ruta del directorio del script para localizar el archivo de estado
$StateFile = Join-Path $PSScriptRoot "telegram_state.json"

if (-not (Test-Path $StateFile)) {
    Write-Error "No se encontró el archivo de estado en: $StateFile"
    Exit 1
}

# Leer configuración del archivo de estado
$Config = Get-Content -Raw -Path $StateFile | ConvertFrom-Json
$Token = $Config.Token
$ChatId = $Config.ChatId


if (-not $FilePath) {
    Write-Error "Debes proveer una ruta de archivo válida con -FilePath"
    Exit 1
}

if (Test-Path $FilePath) {
    Write-Host "Enviando archivo: $FilePath a Telegram..."
    # Enviar archivo usando curl nativo
    curl.exe -s -X POST "https://api.telegram.org/bot$Token/sendDocument" `
        -F "chat_id=$ChatId" `
        -F "document=@$FilePath" `
        -F "caption=$Caption" | Out-Null
    Write-Host "Archivo enviado exitosamente."
} else {
    Write-Error "El archivo especificado no existe en la ruta: $FilePath"
    Exit 1
}
