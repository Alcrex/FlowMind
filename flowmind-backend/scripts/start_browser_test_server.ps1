$ErrorActionPreference = 'Stop'
$taskBackendRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $taskBackendRoot
try {
    & mvn test-compile dependency:build-classpath '-Dmdep.outputFile=target/browser-classpath.txt' -B
    if ($LASTEXITCODE -ne 0) { throw 'Unable to compile browser test server' }
    $taskClassPath = ('target/classes;target/test-classes;' + (Get-Content target/browser-classpath.txt -Raw)).Replace('\', '/').Trim()
    $taskArguments = "-cp`n`"$taskClassPath`"`ncom.flowmind.platform.workflow.WorkflowBrowserServer`n"
    [System.IO.File]::WriteAllText((Join-Path $taskBackendRoot 'target/browser-java.args'), $taskArguments, [System.Text.UTF8Encoding]::new($false))
    $taskJava = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java -ErrorAction Stop).Source }
    & $taskJava '@target/browser-java.args'
    if ($LASTEXITCODE -ne 0) { throw 'Browser test server exited unsuccessfully' }
} finally {
    Pop-Location
}
