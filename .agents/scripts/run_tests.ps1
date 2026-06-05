# .agents/scripts/run_tests.ps1
# Script de validacion local basado en Gradle para CameraStellar v3

$ErrorActionPreference = "Continue"

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "   Iniciando Validacion Local de Gradle" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

# 1. Verificar existencia de gradlew.bat
if (-not (Test-Path ".\gradlew.bat")) {
    Write-Host "[ERROR] No se encontro gradlew.bat en el directorio actual." -ForegroundColor Red
    Write-Host "Por favor, ejecute este script desde la raiz del proyecto." -ForegroundColor Yellow
    exit 1
}

# 2. Verificar Java
try {
    $javaVersion = java -version 2>&1
    Write-Host "[OK] Java detectado:" -ForegroundColor Green
    Write-Host ($javaVersion | Out-String) -ForegroundColor Gray
} catch {
    Write-Host "[ADVERTENCIA] 'java' no esta en el PATH de Windows." -ForegroundColor Yellow
    Write-Host "Si Gradle falla, asegurese de tener JDK 17 configurado correctamente." -ForegroundColor Yellow
}

# 3. Ejecutar Pruebas Unitarias
Write-Host "" -ForegroundColor Blue
Write-Host "[1/2] Ejecutando pruebas unitarias locales..." -ForegroundColor Blue
& .\gradlew.bat testDebugUnitTest --no-daemon

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Las pruebas unitarias fallaron (Gradle Exit Code: $LASTEXITCODE)." -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Pruebas unitarias aprobadas exitosamente." -ForegroundColor Green

# 4. Ejecutar Analisis de Linter
Write-Host "" -ForegroundColor Blue
Write-Host "[2/2] Ejecutando analisis estatico (Android Lint)..." -ForegroundColor Blue
& .\gradlew.bat lintDebug --no-daemon

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] El analisis estatico de Lint fallo o reportó errores criticos (Gradle Exit Code: $LASTEXITCODE)." -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Analisis estatico aprobado exitosamente." -ForegroundColor Green

Write-Host "" -ForegroundColor Green
Write-Host "=============================================" -ForegroundColor Green
Write-Host "   *** TODAS LAS PRUEBAS PASARON CORRECTAMENTE ***" -ForegroundColor Green
Write-Host "=============================================" -ForegroundColor Green
exit 0
