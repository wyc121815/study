# 在本地组装一份"发布包"，目录结构必须和 CI 产出的完全一致。
#
#   .\mvnw.cmd -B package
#   cd frontend; npm run build; cd ..
#   .\scripts\build-release-bundle.ps1
#   cd build\release; docker compose up -d --build
#
# 用途：在上服务器之前，先在本机把生产编排整套跑一遍。

param(
    [string]$OutputDir = 'build\release'
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$target = Join-Path $root $OutputDir
$modules = @('auth-service', 'conn-service', 'gateway-service')

Write-Host "组装发布包 -> $target" -ForegroundColor Yellow

if (Test-Path $target) { Remove-Item -LiteralPath $target -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $target 'backend') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $target 'web') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $target 'nginx') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $target 'init') | Out-Null

foreach ($module in $modules) {
    $jar = Join-Path $root "$module\target\$module-0.0.1-SNAPSHOT.jar"
    if (-not (Test-Path $jar)) {
        throw "找不到 $module 的 jar，请先执行 .\mvnw.cmd -B package"
    }
    Copy-Item -LiteralPath $jar -Destination (Join-Path $target "backend\$module.jar") -Force
    Write-Host "  backend\$module.jar" -ForegroundColor DarkGray
}

$dist = Join-Path $root 'frontend\dist'
if (-not (Test-Path $dist)) {
    throw '找不到 frontend\dist，请先在 frontend 目录执行 npm run build'
}
Copy-Item -Path (Join-Path $dist '*') -Destination (Join-Path $target 'web') -Recurse -Force

Copy-Item -LiteralPath (Join-Path $root 'deploy\docker-compose.yml') -Destination $target -Force
Copy-Item -LiteralPath (Join-Path $root 'deploy\Dockerfile') -Destination $target -Force
Copy-Item -LiteralPath (Join-Path $root 'deploy\nginx\nginx.conf') -Destination (Join-Path $target 'nginx') -Force
Copy-Item -LiteralPath (Join-Path $root 'deploy\mysql\init\01-schema.sql') -Destination (Join-Path $target 'init') -Force

$envExample = @'
# 本机验证用的示例密钥，切勿用于生产
MYSQL_ROOT_PASSWORD=root123456
MYSQL_PASSWORD=platform123
APP_JWT_SECRET=ZGV2LW9ubHktc2VjcmV0LWRvLW5vdC11c2UtaW4tcHJvZHVjdGlvbi0xMjM0NTY=
APP_CRYPTO_KEY=Y29ubi1wbGF0Zm9ybS1kZXYtYWVzLWtleS0zMmJ5dGU=
APP_INTERNAL_SECRET=ZGV2LWludGVybmFsLXNlY3JldC1jaGFuZ2UtbWUtMzJieXRlcw==
APP_ADMIN_USERNAME=admin
APP_ADMIN_PASSWORD=admin123
HTTP_PORT=80
'@
Set-Content -LiteralPath (Join-Path $target '.env') -Value $envExample -Encoding UTF8

Write-Host "`n完成。运行方式：" -ForegroundColor Cyan
Write-Host "  cd $OutputDir" -ForegroundColor Cyan
Write-Host "  docker compose up -d --build" -ForegroundColor Cyan
