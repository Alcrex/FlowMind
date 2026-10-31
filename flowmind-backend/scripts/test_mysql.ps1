param(
    [string]$MySqlBin = $(if ($env:MYSQL_HOME) { Join-Path $env:MYSQL_HOME 'bin' } else { Split-Path (Get-Command mysqld -ErrorAction Stop).Source })
)
$ErrorActionPreference = 'Stop'
$taskBackendRoot = Split-Path -Parent $PSScriptRoot
$taskMySqlServer = Join-Path $MySqlBin 'mysqld.exe'
$taskMySqlClient = Join-Path $MySqlBin 'mysql.exe'
if (!(Test-Path -LiteralPath $taskMySqlServer) -or !(Test-Path -LiteralPath $taskMySqlClient)) { throw 'Set -MySqlBin to the MySQL 8.0 bin directory.' }
$taskRunId = [guid]::NewGuid().ToString('N')
$taskTempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$taskDataRoot = Join-Path $taskTempRoot "flowmind-mysql-$taskRunId"
$taskSchema = "flowmind_acceptance_$taskRunId"
$taskEnvironmentNames = @('FLOWMIND_MYSQL_TEST_URL', 'FLOWMIND_MYSQL_TEST_USER', 'FLOWMIND_MYSQL_TEST_PASSWORD')
$taskPreviousEnvironment = @{}
foreach ($taskName in $taskEnvironmentNames) { $taskPreviousEnvironment[$taskName] = [Environment]::GetEnvironmentVariable($taskName, 'Process') }
$taskServerProcess = $null
New-Item -ItemType Directory -Path $taskDataRoot | Out-Null
Push-Location -LiteralPath $taskBackendRoot
try {
    & $taskMySqlServer --no-defaults --version
    $taskDataDirectory = Join-Path $taskDataRoot 'data'
    $taskInit = Start-Process -FilePath $taskMySqlServer -ArgumentList @('--no-defaults', '--initialize-insecure', '--console', "--basedir=`"$(Split-Path $MySqlBin)`"", "--datadir=`"$taskDataDirectory`"") -PassThru -Wait -WindowStyle Hidden -RedirectStandardError (Join-Path $taskDataRoot 'initialize.log')
    if ($taskInit.ExitCode -ne 0) { throw "MySQL initialization failed; inspect $taskDataRoot/initialize.log" }
    $taskPortProbe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $taskPortProbe.Start()
    $taskPort = $taskPortProbe.LocalEndpoint.Port
    $taskPortProbe.Stop()
    $taskServerProcess = Start-Process -FilePath $taskMySqlServer -ArgumentList @('--no-defaults', "--basedir=`"$(Split-Path $MySqlBin)`"", "--datadir=`"$taskDataDirectory`"", '--bind-address=127.0.0.1', "--port=$taskPort", '--mysqlx=0', "--log-error=`"$(Join-Path $taskDataRoot 'server.log')`"") -PassThru -WindowStyle Hidden
    $taskReady = $false
    for ($taskAttempt = 0; $taskAttempt -lt 60; $taskAttempt++) {
        if ($taskServerProcess.HasExited) { throw 'Isolated MySQL exited before becoming ready.' }
        & $taskMySqlClient --no-defaults --protocol=TCP --host=127.0.0.1 "--port=$taskPort" --user=root --connect-timeout=1 --execute='SELECT 1' *> $null
        if ($LASTEXITCODE -eq 0) { $taskReady = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (!$taskReady) { throw 'Isolated MySQL did not become ready within 30 seconds.' }
    & $taskMySqlClient --no-defaults --protocol=TCP --host=127.0.0.1 "--port=$taskPort" --user=root --execute="CREATE DATABASE $taskSchema CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    if ($LASTEXITCODE -ne 0) { throw 'Unable to create disposable acceptance schema.' }
    $env:FLOWMIND_MYSQL_TEST_URL = "jdbc:mysql://127.0.0.1:$taskPort/$($taskSchema)?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
    $env:FLOWMIND_MYSQL_TEST_USER = 'root'
    $env:FLOWMIND_MYSQL_TEST_PASSWORD = ''
    & mvn '-Dtest=MySqlAcceptanceTest' test -B
    if ($LASTEXITCODE -ne 0) { throw 'MySQL acceptance tests failed.' }
} catch {
    foreach ($taskLog in @('initialize.log', 'server.log')) {
        $taskLogPath = Join-Path $taskDataRoot $taskLog
        if (Test-Path -LiteralPath $taskLogPath) { Get-Content -LiteralPath $taskLogPath -Tail 12 | Write-Host }
    }
    throw
} finally {
    if ($taskServerProcess -and !$taskServerProcess.HasExited) {
        & (Join-Path $MySqlBin 'mysqladmin.exe') --no-defaults --protocol=TCP --host=127.0.0.1 "--port=$taskPort" --user=root shutdown *> $null
        if (!$taskServerProcess.WaitForExit(10000)) { Stop-Process -Id $taskServerProcess.Id; $taskServerProcess.WaitForExit() }
    }
    foreach ($taskName in $taskEnvironmentNames) { [Environment]::SetEnvironmentVariable($taskName, $taskPreviousEnvironment[$taskName], 'Process') }
    Pop-Location
    $taskResolvedDataRoot = [IO.Path]::GetFullPath($taskDataRoot)
    if ($taskResolvedDataRoot.StartsWith($taskTempRoot, [StringComparison]::OrdinalIgnoreCase) -and (Split-Path $taskResolvedDataRoot -Leaf) -eq "flowmind-mysql-$taskRunId") {
        Remove-Item -LiteralPath $taskResolvedDataRoot -Recurse -Force
    }
}
