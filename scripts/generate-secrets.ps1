# 生成生产环境需要的 JWT 密钥与连接密码加密密钥。
#
#   .\scripts\generate-secrets.ps1
#
# 输出直接填到 GitHub Actions 的 Secrets 里。注意：这两个值一旦上线就不能再改，
# 否则已签发的 token 会失效、已保存的连接密码会解不开。

$ErrorActionPreference = 'Stop'

function New-Base64Key([int]$byteLength) {
    $bytes = New-Object byte[] $byteLength
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return [Convert]::ToBase64String($bytes)
}

$jwtSecret = New-Base64Key 32
$cryptoKey = New-Base64Key 32
$internalSecret = New-Base64Key 32

Write-Host 'APP_JWT_SECRET  = ' -NoNewline -ForegroundColor Yellow
Write-Host $jwtSecret
Write-Host 'APP_CRYPTO_KEY  = ' -NoNewline -ForegroundColor Yellow
Write-Host $cryptoKey
Write-Host 'APP_INTERNAL_SECRET = ' -NoNewline -ForegroundColor Yellow
Write-Host $internalSecret
Write-Host ''
Write-Host 'MySQL 密码建议单独生成一串强随机字符串。' -ForegroundColor DarkGray
