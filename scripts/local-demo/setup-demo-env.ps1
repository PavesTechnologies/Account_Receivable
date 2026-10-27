<#
.SYNOPSIS
    Sets the two local-demo credentials for THIS PowerShell session, safely, so start-demo.ps1 can run.

.DESCRIPTION
    Every teammate creates their OWN credentials. Nothing here is shared, committed, written to the repository, put on a
    command line, or printed. The values live in this session's environment (AR_DEMO_DB_PASSWORD, AR_DEMO_JWT_SECRET).

    Where the values come from
      * default:        you are prompted (input hidden) for each value; press Enter to have a strong random one generated
                        with a cryptographic random number generator;
      * -GenerateAll:   no prompts, both values are generated;
      * a stored file:  if you used -Remember before, the values are loaded from it (see below);
      * already set:    if both variables are already set in this shell and valid, they are kept.

    -Remember   Stores the two values in %LOCALAPPDATA%\ar-local-demo\credentials.xml, OUTSIDE the repository. The file is
                encrypted with Windows DPAPI: only your Windows user on this machine can decrypt it. It is never read by the
                application. -Forget deletes it. Without -Remember a generated database password is lost when the shell
                closes, so remember it, or choose your own password, if you want to reuse the same MySQL user later.

    Validation   After the variables are set, the existing wrapper is run with -CheckOnly (start-demo.ps1 -CheckOnly), which
                applies the real isolation rules (secret length and variety, loopback-only database URL, no overriding
                SPRING_* variables, ...) and starts nothing. If it refuses, the variables set by this script are removed again.

    -ProvisionDatabase   OPTIONAL. Creates the local schema ar_demo and the application user (default ar_demo_app, for the
                hosts localhost and 127.0.0.1) on YOUR local MySQL, using the session's database password. It only ever
                runs CREATE DATABASE/USER IF NOT EXISTS and GRANT on ar_demo.*; it never drops, alters, deletes or reseeds
                anything and never loads the seed. It refuses unless the target is loopback, the schema is exactly ar_demo,
                the server reports it is bound to loopback only and is on the expected port, and you confirm by typing
                ar_demo (or passing -ConfirmTarget ar_demo). The MySQL admin password comes from MYSQL_PWD if set,
                otherwise a hidden prompt (use -AdminNoPassword for a fresh --initialize-insecure server).

    This script never reads or edits .env, never contacts anything but the loopback MySQL you point it at, and cannot
    start the backend with any profile other than local-demo (it only calls start-demo.ps1 -CheckOnly).

.EXAMPLE
    .\scripts\local-demo\setup-demo-env.ps1 -GenerateAll -Remember -ProvisionDatabase
    .\scripts\local-demo\start-demo.ps1
#>
#Requires -Version 5.1
[CmdletBinding()]
param(
    [switch]$GenerateAll,
    [switch]$Remember,
    [switch]$Forget,
    [switch]$ProvisionDatabase,
    [string]$ConfirmTarget,
    [string]$AdminUser = 'root',
    [switch]$AdminNoPassword
)

$ErrorActionPreference = 'Stop'
$wrapper = Join-Path $PSScriptRoot 'start-demo.ps1'
$storeDir = Join-Path $env:LOCALAPPDATA 'ar-local-demo'
$storeFile = Join-Path $storeDir 'credentials.xml'

$script:ownAdminPwd = $false

function Stop-Setup([string]$Reason) {
    # never leave the MySQL admin password behind in the caller's shell, whatever the exit path
    if ($script:ownAdminPwd) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue; $script:ownAdminPwd = $false }
    Write-Host "SETUP STOPPED: $Reason" -ForegroundColor Red
    exit 2
}

function New-RandomSecret([int]$Length) {
    $alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'.ToCharArray()
    $limit = 256 - (256 % $alphabet.Length)          # rejection sampling: no modulo bias
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $sb = New-Object System.Text.StringBuilder
        $byte = New-Object byte[] 1
        while ($sb.Length -lt $Length) {
            $rng.GetBytes($byte)
            if ($byte[0] -lt $limit) { [void]$sb.Append($alphabet[$byte[0] % $alphabet.Length]) }
        }
        return $sb.ToString()
    } finally { $rng.Dispose() }
}

function ConvertTo-PlainText([System.Security.SecureString]$Secure) {
    $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secure)
    try { return [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
    finally { [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

# Returns a reason (never the value) when a credential is unacceptable, otherwise $null.
function Get-CredentialProblem([string]$DbPassword, [string]$JwtSecret) {
    foreach ($pair in @(@('database password', $DbPassword), @('JWT secret', $JwtSecret))) {
        $name = $pair[0]; $value = $pair[1]
        if ([string]::IsNullOrEmpty($value)) { return "the $name is empty." }
        if ($value -notmatch '^[\x21-\x7E]+$') { return "the $name must be printable ASCII with no spaces." }
        if ($value -match '[<>]') { return "the $name contains '<' or '>' (looks like an unedited placeholder)." }
    }
    if ($DbPassword.Length -lt 12) { return "the database password must be at least 12 characters." }
    if ($JwtSecret.Length -lt 32) { return "the JWT secret must be at least 32 characters." }
    if (($JwtSecret.ToCharArray() | Select-Object -Unique).Count -lt 10) { return "the JWT secret is not random enough (fewer than 10 distinct characters)." }
    if ($JwtSecret -ceq $DbPassword) { return "the JWT secret and the database password must be different values." }
    return $null
}

function Read-OptionalSecret([string]$Prompt) {
    $secure = Read-Host -Prompt $Prompt -AsSecureString
    if ($secure.Length -eq 0) { return $null }
    return (ConvertTo-PlainText $secure)
}

# ---------------------------------------------------------------------------------------------------------------------
if ($Forget) {
    if (Test-Path -LiteralPath $storeFile) { Remove-Item -LiteralPath $storeFile -Force; Write-Host "Removed the stored credentials ($storeFile)." }
    else { Write-Host "No stored credentials found." }
    Remove-Item Env:AR_DEMO_DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:AR_DEMO_JWT_SECRET -ErrorAction SilentlyContinue
    Write-Host "AR_DEMO_DB_PASSWORD and AR_DEMO_JWT_SECRET were cleared from this session."
    exit 0
}

$previousDb = $env:AR_DEMO_DB_PASSWORD
$previousJwt = $env:AR_DEMO_JWT_SECRET
$source = $null
$db = $null
$jwt = $null

if (-not $GenerateAll -and (Test-Path -LiteralPath $storeFile)) {
    try {
        $stored = Import-Clixml -LiteralPath $storeFile
        $db = ConvertTo-PlainText $stored.DbPassword
        $jwt = ConvertTo-PlainText $stored.JwtSecret
        $source = 'stored encrypted file'
    } catch {
        Stop-Setup "the stored credentials could not be decrypted (they belong to another Windows user or machine). Run with -Forget, then set up again."
    }
}
if (-not $source -and -not $GenerateAll -and $previousDb -and $previousJwt) {
    $db = $previousDb; $jwt = $previousJwt; $source = 'already set in this shell'
}
if (-not $source) {
    if ($GenerateAll) {
        $db = New-RandomSecret 24; $jwt = New-RandomSecret 48; $source = 'generated'
    } else {
        Write-Host "Enter your own local-demo credentials. Input is hidden. Press Enter to generate a random value."
        $db = Read-OptionalSecret "AR demo database password (>= 12 characters)"
        $jwt = Read-OptionalSecret "AR demo JWT secret (>= 32 characters)"
        $source = 'entered or generated'
        if (-not $db) { $db = New-RandomSecret 24 }
        if (-not $jwt) { $jwt = New-RandomSecret 48 }
    }
}

$problem = Get-CredentialProblem $db $jwt
if ($problem) { Stop-Setup $problem }

$env:AR_DEMO_DB_PASSWORD = $db
$env:AR_DEMO_JWT_SECRET = $jwt
Write-Host "Credentials set for this PowerShell session (source: $source). Values are not displayed."

# Real isolation rules, from the same wrapper that starts the backend. Starts nothing.
Write-Host "Running the wrapper's checks (start-demo.ps1 -CheckOnly)..."
& $wrapper -CheckOnly
if ($LASTEXITCODE -ne 0) {
    if ($null -ne $previousDb) { $env:AR_DEMO_DB_PASSWORD = $previousDb } else { Remove-Item Env:AR_DEMO_DB_PASSWORD -ErrorAction SilentlyContinue }
    if ($null -ne $previousJwt) { $env:AR_DEMO_JWT_SECRET = $previousJwt } else { Remove-Item Env:AR_DEMO_JWT_SECRET -ErrorAction SilentlyContinue }
    Stop-Setup "the wrapper refused this configuration (see its message above). The variables set by this script were removed again."
}

if ($Remember) {
    New-Item -ItemType Directory -Force -Path $storeDir | Out-Null
    [pscustomobject]@{
        DbPassword = (ConvertTo-SecureString $db -AsPlainText -Force)
        JwtSecret  = (ConvertTo-SecureString $jwt -AsPlainText -Force)
    } | Export-Clixml -LiteralPath $storeFile
    Write-Host "Stored (DPAPI-encrypted, this Windows user only) outside the repository: $storeFile"
}

# ---------------------------------------------------------------------------------------------------------------------
function Invoke-Mysql([string[]]$MysqlArgs, [string]$Sql) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    # Windows PowerShell pipes to a native program using [Console]::InputEncoding. On a UTF-8 console (code page 65001) that
    # encoding writes a byte-order mark in front of the SQL; use the same encoding without the mark. Everything sent is ASCII.
    $previousConsoleEncoding = $null
    try { $previousConsoleEncoding = [Console]::InputEncoding; [Console]::InputEncoding = New-Object System.Text.UTF8Encoding($false) } catch { $previousConsoleEncoding = $null }
    try {
        $out = if ($null -ne $Sql) { $Sql | & $script:mysqlExe @MysqlArgs 2>&1 } else { & $script:mysqlExe @MysqlArgs 2>&1 }
        return [pscustomobject]@{ Exit = $LASTEXITCODE; Output = (($out | ForEach-Object { "$_" }) -join "`n") }
    } finally {
        $ErrorActionPreference = $previous
        if ($null -ne $previousConsoleEncoding) { try { [Console]::InputEncoding = $previousConsoleEncoding } catch { } }
    }
}

if ($ProvisionDatabase) {
    # 1. verify the target BEFORE any prompt or connection
    $port = 3399; $schema = 'ar_demo'; $hostName = '127.0.0.1'
    if ($env:AR_DEMO_DB_URL) {
        if ($env:AR_DEMO_DB_URL -notmatch '^jdbc:mysql://(localhost|127\.0\.0\.1|\[::1\])(:(\d+))?/([^/?]+)') {
            Stop-Setup "AR_DEMO_DB_URL must point at the local machine (localhost, 127.0.0.1 or [::1])."
        }
        if ($Matches[3]) { $port = [int]$Matches[3] }
        $schema = $Matches[4]
        if ($Matches[1] -eq '[::1]') { $hostName = '::1' }
    }
    if ($schema -cne 'ar_demo') { Stop-Setup "refusing to provision: the database name must be exactly 'ar_demo' (got '$schema')." }
    $appUser = if ($env:AR_DEMO_DB_USERNAME) { $env:AR_DEMO_DB_USERNAME } else { 'ar_demo_app' }
    if ($appUser -notmatch '^[A-Za-z0-9_]{1,32}$') { Stop-Setup "AR_DEMO_DB_USERNAME may only contain letters, digits and underscores." }
    if ($AdminUser -notmatch '^[A-Za-z0-9_]{1,32}$') { Stop-Setup "-AdminUser may only contain letters, digits and underscores." }

    # 2. explicit confirmation
    $answer = $ConfirmTarget
    if (-not $answer) { $answer = Read-Host -Prompt "Create schema ar_demo and user '$appUser' on ${hostName}:$port ? Type ar_demo to confirm" }
    if ($answer -cne 'ar_demo') { Stop-Setup "not confirmed; nothing was created." }

    $cmd = Get-Command mysql -ErrorAction SilentlyContinue
    if (-not $cmd) { Stop-Setup "the 'mysql' client was not found on the PATH." }
    $script:mysqlExe = $cmd.Source

    # 3. admin credentials (password never on a command line)
    if (-not $env:MYSQL_PWD -and -not $AdminNoPassword) {
        $adminSecure = Read-Host -Prompt "MySQL admin password for '$AdminUser' (hidden; Enter for none)" -AsSecureString
        if ($adminSecure.Length -gt 0) { $env:MYSQL_PWD = (ConvertTo-PlainText $adminSecure); $script:ownAdminPwd = $true }
    }
    $adminArgs = @('--no-defaults', "--host=$hostName", "--port=$port", "--user=$AdminUser", '--batch', '--skip-column-names')
    try {
        # 4. the server must be the expected one and reachable only from this machine
        $probe = Invoke-Mysql $adminArgs 'SELECT @@port, @@bind_address;'
        if ($probe.Exit -ne 0) { Stop-Setup "could not connect to the local MySQL as '$AdminUser' on ${hostName}:$port. Is it running, and is the admin password right?" }
        $fields = ($probe.Output -split "`t")
        if ([int]$fields[0] -ne $port) { Stop-Setup "the server reports port $($fields[0]), expected $port; refusing." }
        $binds = @(($fields[1].Trim() -split ',') | ForEach-Object { $_.Trim() })
        $notLoopback = @($binds | Where-Object { $_ -notin @('127.0.0.1', '::1', 'localhost') })
        if ($notLoopback.Count -gt 0 -or $binds.Count -eq 0) {
            Stop-Setup "the server is not bound to loopback only (bind_address '$($fields[1].Trim())'). Start it with --bind-address=127.0.0.1 and try again."
        }

        # 5. additive statements only
        $pwdSql = $db.Replace('\', '\\').Replace("'", "''")
        $sql = @"
CREATE DATABASE IF NOT EXISTS ar_demo CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS '$appUser'@'localhost' IDENTIFIED BY '$pwdSql';
CREATE USER IF NOT EXISTS '$appUser'@'127.0.0.1' IDENTIFIED BY '$pwdSql';
GRANT ALL PRIVILEGES ON ar_demo.* TO '$appUser'@'localhost';
GRANT ALL PRIVILEGES ON ar_demo.* TO '$appUser'@'127.0.0.1';
"@
        $create = Invoke-Mysql $adminArgs $sql
        if ($create.Exit -ne 0) { Stop-Setup "MySQL rejected the statements (details withheld because they could contain the password). Check the admin privileges." }
    } finally {
        if ($script:ownAdminPwd) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue; $script:ownAdminPwd = $false }
    }

    # 6. prove the application user works with the session's password (it may already have existed with another one)
    $previousPwd = $env:MYSQL_PWD
    $env:MYSQL_PWD = $db
    try {
        $login = Invoke-Mysql @('--no-defaults', "--host=$hostName", "--port=$port", "--user=$appUser", '--database=ar_demo', '--batch', '--skip-column-names') 'SELECT DATABASE();'
    } finally {
        if ($null -ne $previousPwd) { $env:MYSQL_PWD = $previousPwd } else { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
    }
    if ($login.Exit -eq 0 -and $login.Output.Trim() -ceq 'ar_demo') {
        Write-Host "Schema ar_demo and user '$appUser' are ready on ${hostName}:$port (verified by logging in as that user)."
    } else {
        Stop-Setup "user '$appUser' could not log in with this password. It probably already exists with a DIFFERENT password; this script never alters existing users. Use that password, or ask an admin to reset it."
    }
}

Write-Host ""
Write-Host "Next:  .\scripts\local-demo\start-demo.ps1        (then, once, the seed - see docs\local-demo.md section 4)"
exit 0
