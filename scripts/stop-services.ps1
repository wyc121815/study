# 停止由 start-services.ps1 启动的后端服务（只结束命令行里带本项目 jar 的 java 进程）。

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$jars = @('auth-service', 'conn-service', 'gateway-service') | ForEach-Object {
    Join-Path $root ('{0}\target\{0}-0.0.1-SNAPSHOT.jar' -f $_)
}

$stopped = 0
Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | ForEach-Object {
    $commandLine = $_.CommandLine
    foreach ($jar in $jars) {
        if ($commandLine -and $commandLine.Contains($jar)) {
            Stop-Process -Id $_.ProcessId -Force
            Write-Host "已停止 $jar (PID $($_.ProcessId))" -ForegroundColor Yellow
            $stopped++
            break
        }
    }
}

if ($stopped -eq 0) {
    Write-Host '没有正在运行的项目服务' -ForegroundColor DarkGray
}
