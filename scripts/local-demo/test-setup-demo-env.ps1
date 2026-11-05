<#
.SYNOPSIS
    Tests setup-demo-env.ps1: secure generation, no secret in any output, encrypted storage, refusals, and static hygiene.
    Contacts no database and starts nothing. Exit code 0 means every case behaved as expected.

.DESCRIPTION
    Every case runs the setup script in a child PowerShell with a controlled environment and a TEMPORARY %LOCALAPPDATA%,
    so the real credential store is never touched. The child reports only lengths, counts, booleans and hashes, never values.
#>
$ErrorActionPreference = 'Stop'
$setup = Join-Path $PSScriptRoot 'setup-demo-env.ps1'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envFile = Join-Path $root '.env'
$envBefore = if (Test-Path $envFile) { (Get-Item $envFile | ForEach-Object { "$($_.Length)|$($_.LastWriteTimeUtc.Ticks)" }) } else { 'absent' }
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('ar-setup-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tempRoot | Out-Null

$managed = @('AR_DEMO_DB_PASSWORD', 'AR_DEMO_JWT_SECRET', 'AR_DEMO_DB_URL', 'AR_DEMO_DB_USERNAME', 'AR_DEMO_SERVER_PORT',
    'SPRING_PROFILES_ACTIVE', 'SPRING_PROFILES_INCLUDE', 'SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME',
    'SPRING_DATASOURCE_PASSWORD', 'SPRING_CONFIG_LOCATION', 'SPRING_CONFIG_ADDITIONAL_LOCATION', 'SPRING_CONFIG_IMPORT',
    'SPRING_CONFIG_NAME', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'MAVEN_OPTS', 'MYSQL_PWD', 'LOCALAPPDATA')
$saved = @{}
foreach ($n in $managed) { $saved[$n] = [Environment]::GetEnvironmentVariable($n) }

$failures = New-Object System.Collections.Generic.List[string]
$count = 0

function Reset-Env([hashtable]$Extra = @{}) {
    foreach ($n in $managed) { [Environment]::SetEnvironmentVariable($n, $null) }
    [Environment]::SetEnvironmentVariable('LOCALAPPDATA', (Join-Path $tempRoot ([guid]::NewGuid().ToString('N'))))
    foreach ($k in $Extra.Keys) { [Environment]::SetEnvironmentVariable($k, $Extra[$k]) }
}

# Runs a snippet in a child PowerShell (inherits the env set by Reset-Env). $S is the setup script path.
function Invoke-Child([string]$Snippet) {
    $file = Join-Path $tempRoot ([guid]::NewGuid().ToString('N') + '.ps1')
    $prefix = '$S = ' + "'" + $setup + "'" + "`r`n" + '$RepoRoot = ' + "'" + $root + "'" + "`r`n"
    Set-Content -LiteralPath $file -Value ($prefix + $Snippet) -Encoding ASCII
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $out = & powershell -NoProfile -ExecutionPolicy Bypass -File $file 2>&1 | ForEach-Object { "$_" } }
    finally { $ErrorActionPreference = $previous }
    return [pscustomobject]@{ Exit = $LASTEXITCODE; Output = ($out -join "`n") }
}

function Check([string]$Name, [bool]$Ok, [string]$Detail = '') {
    $script:count++
    if ($Ok) { Write-Host "  PASS  $Name" }
    else { Write-Host "  FAIL  $Name $Detail"; $script:failures.Add($Name) }
}

function Field([string]$Text, [string]$Key) {
    $m = [regex]::Match($Text, "(?m)^RESULT .*\b$Key=(\S+)")
    if ($m.Success) { return $m.Groups[1].Value } else { return $null }
}

$report = @'
function Hash([string]$x) { $b = [Text.Encoding]::UTF8.GetBytes($x); -join ([Security.Cryptography.SHA256]::Create().ComputeHash($b) | ForEach-Object { $_.ToString('x2') }) }
'@

try {
    Write-Host "setup-demo-env.ps1 tests (no database is contacted, nothing is started)"

    # --- generation ------------------------------------------------------------------------------------------------
    Reset-Env
    $r = Invoke-Child ($report + @'
$o = (& $S -GenerateAll *>&1 | Out-String)
$d = $env:AR_DEMO_DB_PASSWORD; $j = $env:AR_DEMO_JWT_SECRET
"RESULT exit=$LASTEXITCODE lenDb=$($d.Length) lenJwt=$($j.Length) distinct=$(($j.ToCharArray() | Select-Object -Unique).Count) differ=$($d -cne $j) alnum=$((($d + $j) -match '^[A-Za-z0-9]+$')) leak=$($o.Contains($d) -or $o.Contains($j)) hash=$(Hash ($d + $j))"
'@)
    Check 'generates both credentials, exit 0' ((Field $r.Output 'exit') -eq '0') $r.Output
    Check 'database password is 24 characters' ((Field $r.Output 'lenDb') -eq '24')
    Check 'JWT secret is 48 characters (application minimum is 32)' ((Field $r.Output 'lenJwt') -eq '48')
    Check 'JWT secret has far more than 10 distinct characters' ([int](Field $r.Output 'distinct') -ge 20)
    Check 'the two values differ' ((Field $r.Output 'differ') -eq 'True')
    Check 'values are alphanumeric only (no quoting hazards)' ((Field $r.Output 'alnum') -eq 'True')
    Check 'neither secret appears anywhere in the script output' ((Field $r.Output 'leak') -eq 'False')
    $firstHash = Field $r.Output 'hash'

    Reset-Env
    $r2 = Invoke-Child ($report + @'
& $S -GenerateAll *>&1 | Out-Null
"RESULT hash=$(Hash ($env:AR_DEMO_DB_PASSWORD + $env:AR_DEMO_JWT_SECRET))"
'@)
    Check 'two runs generate different values' ($firstHash -and (Field $r2.Output 'hash') -and ($firstHash -ne (Field $r2.Output 'hash')))

    # --- the real wrapper rules are applied -------------------------------------------------------------------------
    Reset-Env
    $r = Invoke-Child ($report + @'
$o = (& $S -GenerateAll *>&1 | Out-String)
"RESULT exit=$LASTEXITCODE wrapper=$($o.Contains('Checks passed (nothing started).')) profile=$($o.Contains('profile         : local-demo')) envNote=$($o.Contains('not read, not modified'))"
'@)
    Check 'runs the wrapper in -CheckOnly mode and it passes' ((Field $r.Output 'wrapper') -eq 'True')
    Check 'the checked profile is local-demo' ((Field $r.Output 'profile') -eq 'True')

    # --- encrypted storage ------------------------------------------------------------------------------------------
    Reset-Env
    $r = Invoke-Child ($report + @'
& $S -GenerateAll -Remember *>&1 | Out-Null
$h1 = Hash ($env:AR_DEMO_DB_PASSWORD + $env:AR_DEMO_JWT_SECRET)
$file = Join-Path $env:LOCALAPPDATA 'ar-local-demo\credentials.xml'
$exists = Test-Path $file
$text = if ($exists) { Get-Content -LiteralPath $file -Raw } else { '' }
$plain = $text.Contains($env:AR_DEMO_DB_PASSWORD) -or $text.Contains($env:AR_DEMO_JWT_SECRET)
Remove-Item Env:AR_DEMO_DB_PASSWORD, Env:AR_DEMO_JWT_SECRET
& $S *>&1 | Out-Null
$h2 = Hash ($env:AR_DEMO_DB_PASSWORD + $env:AR_DEMO_JWT_SECRET)
& $S -Forget *>&1 | Out-Null
"RESULT stored=$exists plaintextInFile=$plain reloaded=$($h1 -eq $h2) forgotten=$(-not (Test-Path $file)) cleared=$(-not $env:AR_DEMO_DB_PASSWORD -and -not $env:AR_DEMO_JWT_SECRET) underRepo=$($file.StartsWith($RepoRoot))"
'@)
    Check '-Remember writes a file under LOCALAPPDATA' ((Field $r.Output 'stored') -eq 'True') $r.Output
    Check 'the stored file contains neither secret in plain text' ((Field $r.Output 'plaintextInFile') -eq 'False')
    Check 'a later run reloads exactly the same values' ((Field $r.Output 'reloaded') -eq 'True')
    Check '-Forget deletes the file and clears the session variables' (((Field $r.Output 'forgotten') -eq 'True') -and ((Field $r.Output 'cleared') -eq 'True'))
    Check 'the store is never inside the repository' ((Field $r.Output 'underRepo') -eq 'False')

    # --- refusals roll back -----------------------------------------------------------------------------------------
    Reset-Env @{ SPRING_DATASOURCE_URL = 'jdbc:mysql://anywhere:3306/x' }
    $r = Invoke-Child ($report + @'
$o = (& $S -GenerateAll -Remember *>&1 | Out-String); $code = $LASTEXITCODE
$file = Join-Path $env:LOCALAPPDATA 'ar-local-demo\credentials.xml'
"RESULT exit=$code removed=$(-not $env:AR_DEMO_DB_PASSWORD -and -not $env:AR_DEMO_JWT_SECRET) noStore=$(-not (Test-Path $file)) says=$($o.Contains('SETUP STOPPED'))"
'@)
    Check 'an overriding SPRING_DATASOURCE_URL makes the setup stop (exit 2)' ((Field $r.Output 'exit') -eq '2') $r.Output
    Check 'on refusal the variables it set are removed again' ((Field $r.Output 'removed') -eq 'True')
    Check 'on refusal nothing is stored' ((Field $r.Output 'noStore') -eq 'True')

    Reset-Env @{ AR_DEMO_DB_PASSWORD = 'short'; AR_DEMO_JWT_SECRET = 'tooShort' }
    $r = Invoke-Child ('$o = (& $S *>&1 | Out-String); "RESULT exit=$LASTEXITCODE says=$($o.Contains(''at least 12''))"')
    Check 'weak preset credentials are refused' ((Field $r.Output 'exit') -eq '2') $r.Output

    Reset-Env @{ AR_DEMO_DB_PASSWORD = 'Abcdefgh1234567890'; AR_DEMO_JWT_SECRET = 'Abcdefgh1234567890' }
    $r = Invoke-Child ('& $S *>&1 | Out-Null; "RESULT exit=$LASTEXITCODE"')
    Check 'identical or too-short secrets are refused' ((Field $r.Output 'exit') -eq '2') $r.Output

    Reset-Env @{ AR_DEMO_DB_URL = 'jdbc:mysql://db.example.com:3306/ar_demo' }
    $r = Invoke-Child ('& $S -GenerateAll -ProvisionDatabase -ConfirmTarget ar_demo *>&1 | Out-Null; "RESULT exit=$LASTEXITCODE"')
    Check 'a non-loopback database URL is refused' ((Field $r.Output 'exit') -eq '2') $r.Output

    # --- provisioning refusals happen before any prompt or connection ----------------------------------------------
    Reset-Env @{ AR_DEMO_DB_URL = 'jdbc:mysql://127.0.0.1:3399/some_other_db' }
    $r = Invoke-Child ('$o = (& $S -GenerateAll -ProvisionDatabase -ConfirmTarget ar_demo *>&1 | Out-String); "RESULT exit=$LASTEXITCODE says=$($o.Contains(''exactly ''''ar_demo''''''))"')
    Check 'provisioning refuses any schema name other than ar_demo' (((Field $r.Output 'exit') -eq '2') -and ((Field $r.Output 'says') -eq 'True')) $r.Output

    Reset-Env
    $r = Invoke-Child ('$o = (& $S -GenerateAll -ProvisionDatabase -ConfirmTarget wrong_name *>&1 | Out-String); "RESULT exit=$LASTEXITCODE says=$($o.Contains(''not confirmed''))"')
    Check 'provisioning without the exact confirmation creates nothing' (((Field $r.Output 'exit') -eq '2') -and ((Field $r.Output 'says') -eq 'True')) $r.Output

    Reset-Env @{ AR_DEMO_DB_USERNAME = "x'; DROP USER 'root" }
    $r = Invoke-Child ('$o = (& $S -GenerateAll -ProvisionDatabase -ConfirmTarget ar_demo *>&1 | Out-String); "RESULT exit=$LASTEXITCODE"')
    Check 'a hostile AR_DEMO_DB_USERNAME is refused' ((Field $r.Output 'exit') -eq '2') $r.Output

    # --- provisioning logic against a STUB mysql client (no server is contacted) ------------------------------------
    $stubDir = Join-Path $tempRoot 'stub'
    New-Item -ItemType Directory -Path $stubDir | Out-Null
    $tab = [char]9
    $stub = @(
        '@echo off',
        'echo %* | findstr /C:"--user=ar_demo_app" >nul',
        'if not errorlevel 1 ( echo ar_demo& exit /b 0 )',
        'more > "%STUB_TMP%"',
        'type "%STUB_TMP%" >> "%STUB_LOG%"',
        'findstr /C:"CREATE DATABASE" "%STUB_TMP%" >nul',
        'if not errorlevel 1 exit /b 0',
        ('echo %STUB_PORT%' + $tab + '%STUB_BIND%'),
        'exit /b 0')
    Set-Content -LiteralPath (Join-Path $stubDir 'mysql.cmd') -Value $stub -Encoding ASCII
    $provision = '$o = (& $S -GenerateAll -ProvisionDatabase -ConfirmTarget ar_demo -AdminNoPassword *>&1 | Out-String); $created = (Test-Path $env:STUB_LOG) -and ((Get-Content -LiteralPath $env:STUB_LOG -Raw) -match ''CREATE DATABASE''); "RESULT exit=$LASTEXITCODE created=$created ready=$($o.Contains(''are ready on''))"'
    function Stub-Env([string]$Bind, [string]$Port) {
        Reset-Env @{ STUB_BIND = $Bind; STUB_PORT = $Port; STUB_TMP = (Join-Path $tempRoot 'stub.tmp'); STUB_LOG = (Join-Path $tempRoot ('stub-' + [guid]::NewGuid().ToString('N') + '.log')) }
        [Environment]::SetEnvironmentVariable('PATH', $stubDir + ';' + $saved['PATH_ORIG'])
    }
    $saved['PATH_ORIG'] = [Environment]::GetEnvironmentVariable('PATH')
    foreach ($case in @(
            @{ Name = 'all-interfaces bind (0.0.0.0)'; Bind = '0.0.0.0'; Port = '3399'; Exit = '2'; Created = 'False' },
            @{ Name = 'wildcard bind (*)';             Bind = '*';       Port = '3399'; Exit = '2'; Created = 'False' },
            @{ Name = 'mixed bind (127.0.0.1,10.0.0.5)'; Bind = '127.0.0.1,10.0.0.5'; Port = '3399'; Exit = '2'; Created = 'False' },
            @{ Name = 'a different server port';       Bind = '127.0.0.1'; Port = '3306'; Exit = '2'; Created = 'False' },
            @{ Name = 'loopback-only bind (127.0.0.1)'; Bind = '127.0.0.1'; Port = '3399'; Exit = '0'; Created = 'True' },
            @{ Name = 'dual loopback bind (127.0.0.1,::1)'; Bind = '127.0.0.1,::1'; Port = '3399'; Exit = '0'; Created = 'True' })) {
        Stub-Env $case.Bind $case.Port
        $r = Invoke-Child $provision
        $ok = ((Field $r.Output 'exit') -eq $case.Exit) -and ((Field $r.Output 'created') -eq $case.Created)
        Check "provisioning with $($case.Name): exit $($case.Exit), CREATE sent = $($case.Created)" $ok $r.Output
    }
    Stub-Env '127.0.0.1' '3399'
    $r = Invoke-Child $provision
    $log = Get-Content -LiteralPath (Join-Path $tempRoot (Get-ChildItem $tempRoot -Filter 'stub-*.log' | Sort-Object LastWriteTime | Select-Object -Last 1).Name) -Raw -ErrorAction SilentlyContinue
    Check 'the statements sent are exactly 3 CREATE ... IF NOT EXISTS (database + 2 users) and 2 GRANTs' (
        (@([regex]::Matches($log, '(?m)^(CREATE DATABASE IF NOT EXISTS|CREATE USER IF NOT EXISTS)')).Count -eq 3) -and
        (@([regex]::Matches($log, '(?m)^GRANT ALL PRIVILEGES ON ar_demo\.\* TO')).Count -eq 2) -and
        ($log -notmatch '(?i)\b(DROP|DELETE|TRUNCATE|ALTER|UPDATE|INSERT|REVOKE)\b'))
    [Environment]::SetEnvironmentVariable('PATH', $saved['PATH_ORIG'])

    # --- static hygiene of the script text --------------------------------------------------------------------------
    $text = Get-Content -LiteralPath $setup -Raw
    $code = (($text -split "`n") | Where-Object { $_.TrimStart() -notmatch '^#' }) -join "`n"
    $sqlBlock = [regex]::Match($text, '(?s)\$sql = @"(.*?)"@').Groups[1].Value
    Check 'the SQL it sends is only CREATE ... IF NOT EXISTS and GRANT on ar_demo.*' (
        ($sqlBlock -notmatch '(?i)\b(DROP|DELETE|TRUNCATE|ALTER|UPDATE|INSERT|REVOKE|RENAME|SOURCE)\b') -and
        (@([regex]::Matches($sqlBlock, '(?im)^(CREATE DATABASE IF NOT EXISTS|CREATE USER IF NOT EXISTS|GRANT ALL PRIVILEGES ON ar_demo\.\*)')).Count -eq 5))
    Check 'no statement outside the SQL block drops, deletes or alters data' ($code -notmatch '(?i)\b(DROP (DATABASE|USER|TABLE)|TRUNCATE|DELETE FROM|ALTER USER)\b')
    Check 'it never loads or references the seed script or --force' (($code -notmatch 'local_demo_seed') -and ($code -notmatch '--force'))
    Check 'it never reads .env' ($code -notmatch '(?i)(Get-Content|ReadAll|Import-Csv|dotenv).*\.env')
    Check 'it never prints a secret variable' ($code -notmatch '(?i)Write-(Host|Output|Warning|Error)[^\r\n]*\$(db\b|jwt\b|pwdSql|env:AR_DEMO_(DB_PASSWORD|JWT_SECRET)|env:MYSQL_PWD)')
    Check 'it writes no repository files (only the DPAPI store via Export-Clixml)' ($code -notmatch '(?i)(Set-Content|Add-Content|Out-File|\[IO\.File\]::Write)')
    Check 'it can only validate through the wrapper in -CheckOnly mode (never starts the backend)' (($code -match '& \$wrapper -CheckOnly') -and ($code -notmatch '(?i)spring-boot:run|\bmvn\b|java '))
    Check 'passwords are never passed as command-line arguments' ($code -notmatch '(?i)--password|-p\$|-p\{')
    Check 'the script and its stored path are ASCII and outside the repo' (-not ([System.Text.Encoding]::UTF8.GetBytes($text) | Where-Object { $_ -gt 127 }))

    $envAfter = if (Test-Path $envFile) { (Get-Item $envFile | ForEach-Object { "$($_.Length)|$($_.LastWriteTimeUtc.Ticks)" }) } else { 'absent' }
    Check '.env untouched (size/timestamp unchanged; content never read)' ($envBefore -eq $envAfter)
}
finally {
    foreach ($n in $managed) { [Environment]::SetEnvironmentVariable($n, $saved[$n]) }
    if ($saved.ContainsKey('PATH_ORIG')) { [Environment]::SetEnvironmentVariable('PATH', $saved['PATH_ORIG']) }
    Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host "$count checks, $($failures.Count) failed"
if ($failures.Count -gt 0) { exit 1 } else { exit 0 }
