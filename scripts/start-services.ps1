# 启动三个后端服务（本地开发用）。
#
#   .\scripts\start-services.ps1
#   .\scripts\start-services.ps1 -MysqlPort 3306 -NacosAddr 127.0.0.1:8848
#
# 依赖：已执行 docker compose up -d，或本机自备 MySQL / Nacos。

param(
    [string]$MysqlPort = "3307",
    [string]$NacosAddr = "127.0.0.1:18848",
    [string]$MysqlHost = "127.0.0.1",
    [string]$MysqlUser = "platform",
    [string]$MysqlPassword = "platform123",
    [string]$RedisHost = "127.0.0.1",
    [string]$RedisPort = "6379",
    [string]$KafkaBootstrap = "127.0.0.1:9092",
    [string]$ExportQueue = "kafka",
    [int]$HeapMb = 384,
    [switch]$Local
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$logDir = Join-Path $root 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$env:MYSQL_HOST = $MysqlHost
$env:MYSQL_PORT = $MysqlPort
$env:MYSQL_USER = $MysqlUser
$env:MYSQL_PASSWORD = $MysqlPassword
$env:NACOS_ADDR = $NacosAddr
$env:REDIS_HOST = $RedisHost
$env:REDIS_PORT = $RedisPort
$env:KAFKA_BOOTSTRAP = $KafkaBootstrap
# redis = Redis Streams；kafka 不可用时导出会自动降级为同步执行
$env:EXPORT_QUEUE_TYPE = $ExportQueue
if ($Local) {
    $env:SPRING_PROFILES_ACTIVE = 'local'
    Write-Host '模式: local（不依赖 Nacos，服务间直连）' -ForegroundColor Yellow
} else {
    $env:SPRING_PROFILES_ACTIVE = ''
    Write-Host "模式: nacos（注册中心 $NacosAddr）" -ForegroundColor Yellow
}

$services = @(
    @{ Name = 'auth-service';    Port = 8081; Jar = 'auth-service\target\auth-service-0.0.1-SNAPSHOT.jar' },
    @{ Name = 'conn-service';    Port = 8082; Jar = 'conn-service\target\conn-service-0.0.1-SNAPSHOT.jar' },
    @{ Name = 'gateway-service'; Port = 8080; Jar = 'gateway-service\target\gateway-service-0.0.1-SNAPSHOT.jar' }
)

foreach ($service in $services) {
    $jar = Join-Path $root $service.Jar
    if (-not (Test-Path $jar)) {
        throw "找不到 $($service.Jar)，请先执行 .\mvnw.cmd -DskipTests package"
    }

    $inUse = Get-NetTCPConnection -State Listen -LocalPort $service.Port -ErrorAction SilentlyContinue
    if ($inUse) {
        Write-Host "$($service.Name) 已在 $($service.Port) 端口运行，跳过" -ForegroundColor DarkGray
        continue
    }

    $startParams = @{
        FilePath               = 'java'
        ArgumentList           = @("-Xmx${HeapMb}m", '-jar', $jar)
        RedirectStandardOutput = (Join-Path $logDir "$($service.Name).log")
        RedirectStandardError  = (Join-Path $logDir "$($service.Name).err.log")
        WindowStyle            = 'Hidden'
    }
    Start-Process @startParams
    Write-Host "已启动 $($service.Name) (:$($service.Port))" -ForegroundColor Green
    Start-Sleep -Seconds 3
}

Write-Host "`n等待服务就绪..." -ForegroundColor Yellow
foreach ($service in $services) {
    $healthy = $false
    for ($i = 1; $i -le 30; $i++) {
        try {
            $response = Invoke-WebRequest -Uri "http://127.0.0.1:$($service.Port)/actuator/health" -TimeoutSec 3 -UseBasicParsing
            if ($response.StatusCode -eq 200) {
                Write-Host "  $($service.Name) :$($service.Port) 就绪" -ForegroundColor Green
                $healthy = $true
                break
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    }
    if (-not $healthy) {
        Write-Host "  $($service.Name) 启动失败，请看 logs\$($service.Name).log" -ForegroundColor Red
    }
}

Write-Host "`n网关入口: http://localhost:8080    日志目录: $logDir" -ForegroundColor Cyan
