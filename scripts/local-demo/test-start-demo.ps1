<#
.SYNOPSIS
    Tests start-demo.ps1's refusals and its output hygiene. Starts nothing: every case runs the wrapper with -CheckOnly.

.DESCRIPTION
    Each case runs the wrapper in a child PowerShell with a controlled environment and checks the exit code
    (0 = checks passed, 2 = refused), the message, that no secret appears in the output, that the effective profile is
    local-demo, and that .env (metadata only: size and timestamp, never its content) is untouched.
    Exit code 0 means every case behaved as expected.
#>
$ErrorActionPreference = 'Stop'
$wrapper = Join-Path $PSScriptRoot 'start-demo.ps1'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envFile = Join-Path $root '.env'
$envBefore = if (Test-Path $envFile) { (Get-Item $envFile | ForEach-Object { "$($_.Length)|$($_.LastWriteTimeUtc.Ticks)" }) } else { 'absent' }

$goodPassword = 'Pw-demo-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$goodSecret = ([guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N'))   # 64 random hex characters
$managed = @('AR_DEMO_DB_PASSWORD', 'AR_DEMO_JWT_SECRET', 'AR_DEMO_DB_URL', 'AR_DEMO_SERVER_PORT', 'SPRING_PROFILES_ACTIVE',
    'SPRING_PROFILES_INCLUDE', 'SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD',
    'SPRING_CONFIG_LOCATION', 'SPRING_CONFIG_ADDITIONAL_LOCATION', 'SPRING_CONFIG_IMPORT', 'SPRING_CONFIG_NAME',
    'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'MAVEN_OPTS')
$saved = @{}
foreach ($n in $managed) { $saved[$n] = [Environment]::GetEnvironmentVariable($n) }

$failures = New-Object System.Collections.Generic.List[string]
$count = 0

# Runs the wrapper in a child PowerShell. stderr (the wrapper's REFUSED lines) must not be treated as an error here.
function Invoke-Wrapper([string[]]$WrapperArgs) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $text = & powershell -NoProfile -ExecutionPolicy Bypass -File $wrapper -CheckOnly @WrapperArgs 2>&1 | Out-String
        $script:LastExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previous }
    return $text
}

function Invoke-Case([string]$Name, [hashtable]$Env, [string[]]$ExtraArgs, [int]$ExpectedExit, [string]$MustContain) {
    $script:count++
    foreach ($n in $managed) { [Environment]::SetEnvironmentVariable($n, $null) }
    foreach ($k in $Env.Keys) { [Environment]::SetEnvironmentVariable($k, [string]$Env[$k]) }
    $output = Invoke-Wrapper $ExtraArgs
    $exit = $script:LastExit
    $problems = @()
    if ($exit -ne $ExpectedExit) { $problems += "exit code $exit, expected $ExpectedExit" }
    if ($MustContain -and ($output -notlike "*$MustContain*")) { $problems += "output lacks '$MustContain'" }
    foreach ($secret in @($goodPassword, $goodSecret)) {
        if ($output.Contains($secret)) { $problems += "a secret value was printed" }
    }
    if ($ExpectedExit -eq 0 -and $output -notlike '*Checks passed (nothing started)*') { $problems += "did not report 'nothing started'" }
    if ($ExpectedExit -eq 2 -and $output -notlike '*Nothing was started*') { $problems += "refusal did not say nothing was started" }
    if ($problems.Count -eq 0) { Write-Host ("  PASS  {0}" -f $Name) }
    else { Write-Host ("  FAIL  {0}: {1}" -f $Name, ($problems -join '; ')); $script:failures.Add($Name) }
    return $output
}

$base = @{ AR_DEMO_DB_PASSWORD = $goodPassword; AR_DEMO_JWT_SECRET = $goodSecret }
function With([hashtable]$Extra) { $h = $base.Clone(); foreach ($k in $Extra.Keys) { $h[$k] = $Extra[$k] }; return $h }

try {
    Write-Host "start-demo.ps1 refusal and hygiene tests (nothing is started)"
    Invoke-Case 'accepts a valid environment' $base @() 0 'AR local-demo start' | Out-Null
    Invoke-Case 'reports that the DevTools restart is disabled' $base @() 0 'devtools restart: disabled' | Out-Null
    # the launch line itself (not run by -CheckOnly) must pass both the profile and the DevTools switch to the application JVM
    $count++
    $launch = (Get-Content (Join-Path $PSScriptRoot 'start-demo.ps1') | Where-Object { $_ -match '^\s*& mvn spring-boot:run' }) -join ' '
    if ($launch -like '*-Dspring-boot.run.profiles=local-demo*' -and $launch -like '*-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false*') { Write-Host '  PASS  the launch line passes the profile and disables the DevTools restart' }
    else { Write-Host '  FAIL  the launch line is missing the profile or the DevTools switch'; $failures.Add('launch line') }
    $out = Invoke-Case 'replaces a preset .env-style profile (local) with local-demo' (With @{ SPRING_PROFILES_ACTIVE = 'local' }) @() 0 "was 'local'"
    Invoke-Case 'refuses requested profile local' $base @('-RequestedProfile', 'local') 2 "only starts 'local-demo'" | Out-Null
    Invoke-Case 'refuses requested profile Local-Demo (case-sensitive)' $base @('-RequestedProfile', 'Local-Demo') 2 "only starts 'local-demo'" | Out-Null
    Invoke-Case 'refuses a whitespace requested profile' $base @('-RequestedProfile', ' ') 2 "only starts 'local-demo'" | Out-Null
    # An empty string is dropped by the OS command line, so PowerShell itself rejects the call (non-zero exit). Either way
    # the wrapper must never report success and never start anything.
    $count++
    foreach ($n in $managed) { [Environment]::SetEnvironmentVariable($n, $null) }
    foreach ($k in $base.Keys) { [Environment]::SetEnvironmentVariable($k, $base[$k]) }
    $emptyOut = Invoke-Wrapper @('-RequestedProfile', '')
    if ($script:LastExit -ne 0 -and $emptyOut -notlike '*Checks passed*') { Write-Host "  PASS  an empty requested profile never succeeds (exit $($script:LastExit))" }
    else { Write-Host "  FAIL  an empty requested profile was not refused"; $failures.Add('empty profile') }
    Invoke-Case 'refuses a missing database password' @{ AR_DEMO_JWT_SECRET = $goodSecret } @() 2 'AR_DEMO_DB_PASSWORD is not set' | Out-Null
    Invoke-Case 'refuses a missing JWT secret' @{ AR_DEMO_DB_PASSWORD = $goodPassword } @() 2 'AR_DEMO_JWT_SECRET is not set' | Out-Null
    Invoke-Case 'refuses a short JWT secret' (With @{ AR_DEMO_JWT_SECRET = 'abcdefghij0123456789' }) @() 2 'too short' | Out-Null
    Invoke-Case 'refuses a 31-character JWT secret (boundary)' (With @{ AR_DEMO_JWT_SECRET = ('Ab1' * 10) + 'x' }) @() 2 'too short' | Out-Null
    Invoke-Case 'accepts a 32-character random JWT secret (boundary)' (With @{ AR_DEMO_JWT_SECRET = 'Zq8wLm3VbN6cXe1RtY4uIo7pAs2dFg5h' }) @() 0 'AR local-demo start' | Out-Null
    Invoke-Case 'refuses a low-variety JWT secret' (With @{ AR_DEMO_JWT_SECRET = ('a' * 48) }) @() 2 'not random enough' | Out-Null
    Invoke-Case 'refuses a placeholder JWT secret' (With @{ AR_DEMO_JWT_SECRET = '<any random string of 32+ characters>' }) @() 2 'placeholder' | Out-Null
    Invoke-Case 'refuses a JWT secret equal to the DB password' @{ AR_DEMO_DB_PASSWORD = $goodSecret; AR_DEMO_JWT_SECRET = $goodSecret } @() 2 'same value' | Out-Null
    foreach ($v in @('SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD', 'SPRING_PROFILES_INCLUDE',
            'SPRING_CONFIG_LOCATION', 'SPRING_CONFIG_ADDITIONAL_LOCATION', 'SPRING_CONFIG_IMPORT', 'SPRING_CONFIG_NAME')) {
        Invoke-Case "refuses $v" (With @{ $v = 'anything' }) @() 2 "$v is set" | Out-Null
    }
    foreach ($v in @('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'MAVEN_OPTS')) {
        Invoke-Case "refuses $v with -Dspring.profiles.active" (With @{ $v = '-Xmx512m -Dspring.profiles.active=local' }) @() 2 'sets a spring.profiles' | Out-Null
    }
    Invoke-Case 'accepts harmless JVM options' (With @{ JAVA_TOOL_OPTIONS = '-Xmx512m' }) @() 0 'AR local-demo start' | Out-Null
    Invoke-Case 'refuses a non-loopback AR_DEMO_DB_URL' (With @{ AR_DEMO_DB_URL = 'jdbc:mysql://db.example.com:3306/ar_demo' }) @() 2 'local machine' | Out-Null
    Invoke-Case 'refuses a look-alike loopback host' (With @{ AR_DEMO_DB_URL = 'jdbc:mysql://localhost.example.com:3306/ar_demo' }) @() 2 'local machine' | Out-Null
    Invoke-Case 'refuses a multi-host AR_DEMO_DB_URL' (With @{ AR_DEMO_DB_URL = 'jdbc:mysql://127.0.0.1,db.example.com:3306/ar_demo' }) @() 2 'local machine' | Out-Null
    Invoke-Case 'accepts a loopback AR_DEMO_DB_URL' (With @{ AR_DEMO_DB_URL = 'jdbc:mysql://localhost:3399/ar_demo' }) @() 0 'localhost:3399/ar_demo' | Out-Null
    Invoke-Case 'refuses a non-numeric AR_DEMO_SERVER_PORT' (With @{ AR_DEMO_SERVER_PORT = 'abc' }) @() 2 'port number' | Out-Null

    # the effective profile and the working directory, checked through the wrapper's own report
    $out = Invoke-Case 'reports the repository root and profile' (With @{ SPRING_PROFILES_ACTIVE = 'local' }) @() 0 'profile         : local-demo'
    $count++
    if ($out -notlike "*repository root : $root*") { Write-Host "  FAIL  repository root not reported"; $failures.Add('repository root') } else { Write-Host "  PASS  runs from / reports the repository root" }
    # runs correctly when invoked from another directory
    $count++
    Push-Location ([IO.Path]::GetTempPath())
    try {
        foreach ($n in $managed) { [Environment]::SetEnvironmentVariable($n, $null) }
        foreach ($k in $base.Keys) { [Environment]::SetEnvironmentVariable($k, $base[$k]) }
        $other = Invoke-Wrapper @()
        if ($script:LastExit -eq 0 -and $other -like "*repository root : $root*") { Write-Host "  PASS  works when invoked from another directory" }
        else { Write-Host "  FAIL  invoked from another directory"; $failures.Add('other directory') }
    } finally { Pop-Location }
}
finally {
    foreach ($n in $managed) { [Environment]::SetEnvironmentVariable($n, $saved[$n]) }
}

$count++
$envAfter = if (Test-Path $envFile) { (Get-Item $envFile | ForEach-Object { "$($_.Length)|$($_.LastWriteTimeUtc.Ticks)" }) } else { 'absent' }
if ($envBefore -eq $envAfter) { Write-Host "  PASS  .env untouched (size/timestamp unchanged; content never read)" }
else { Write-Host "  FAIL  .env metadata changed"; $failures.Add('.env') }

Write-Host ("{0} checks, {1} failed" -f $count, $failures.Count)
if ($failures.Count -gt 0) { exit 1 }
exit 0
