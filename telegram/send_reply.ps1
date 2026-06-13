# Configuración de codificación de salida a UTF-8
$OutputEncoding = [System.Text.Encoding]::UTF8

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
$Url = "https://api.telegram.org/bot$Token/sendMessage"

# Mensaje a enviar parametrizado
param(
    [string]$Text = "¡Hola! Este es un mensaje automatizado desde la PC."
)

$Body = @{
    chat_id = $ChatId
    text = $Text
}

$Json = $Body | ConvertTo-Json -Compress
$Bytes = [System.Text.Encoding]::UTF8.GetBytes($Json)

# Envío transaccional en UTF-8
Invoke-RestMethod -Uri $Url -Method Post -Body $Bytes -ContentType "application/json; charset=utf-8" | Out-Null
Write-Host "Mensaje enviado exitosamente a Telegram."
