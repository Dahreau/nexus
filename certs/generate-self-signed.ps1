Param(
  [string]$OutDir = "./certs"
)

if (-not (Get-Command openssl -ErrorAction SilentlyContinue)) {
  Write-Error "OpenSSL not found in PATH. Install OpenSSL or use WSL."
  exit 1
}

if (-not (Test-Path $OutDir)) {
  New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
}

$crt = Join-Path $OutDir "server.crt"
$key = Join-Path $OutDir "server.key"

Write-Host "Generating self-signed certificate to:`n  $crt`n  $key"
openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout $key -out $crt -subj "/CN=localhost"
Write-Host "Done. Place the generated files under ./certs and run docker-compose up -d"
