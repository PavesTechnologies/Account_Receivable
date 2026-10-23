<#
.SYNOPSIS
    Starts the AR backend in the isolated "local-demo" profile - and refuses to start in any other way.

.DESCRIPTION
    Use this instead of a bare "mvn spring-boot:run". On a machine whose .env contains `spring.profiles.active=local`, a bare
    start silently runs the normal "local" profile against SHARED infrastructure. This wrapper cannot fall back to it:

      * it sets SPRING_PROFILES_ACTIVE=local-demo explicitly, AND passes the profile to Maven as a command-line argument
        (-Dspring-boot.run.profiles=local-demo), which outranks the OS environment and everything in .env;
      * it refuses to start unless the requested profile is exactly "local-demo";
      * it requires the demo database password and a demo JWT secret of at least 32 characters (and not obviously weak);
      * it refuses overrides that would change what the profile resolves (SPRING_DATASOURCE_*, SPRING_PROFILES_INCLUDE,
        SPRING_CONFIG_*, JVM options that set spring.profiles / spring.datasource / spring.config);
      * it disables the DevTools restart in the forked JVM (-Dspring.devtools.restart.enabled=false via
        -Dspring-boot.run.jvmArguments). DevTools reads that setting from JVM system properties before any config file is
        loaded, so the profile file alone cannot do it; without it the app relaunches itself as "restartedMain" and restarts
        on every classpath change;
      * it runs from the repository root, never reads or edits .env, and never prints the password, the JWT secret or any
        .env value. The application itself performs the final isolation check and refuses to start if anything shared
        could still be reached (see com.AccountReceivableManagement.demo.LocalDemoIsolationValidator).

    Required environment (set in the shell, never in .env):
        AR_DEMO_DB_PASSWORD   password of the local demo database user
        AR_DEMO_JWT_SECRET    random string, 32+ characters, also used by new-token.ps1
    Optional:
        AR_DEMO_DB_URL (must be loopback), AR_DEMO_DB_USERNAME, AR_DEMO_SERVER_PORT

.PARAMETER RequestedProfile
    Must be exactly "local-demo". Exists so a mistaken or scripted profile is refused instead of silently honoured.

.PARAMETER CheckOnly
    Run every check and print the plan, but do not start anything (used by test-start-demo.ps1).

.EXAMPLE
    $env:AR_DEMO_DB_PASSWORD = '<the local demo DB password>'
    $env:AR_DEMO_JWT_SECRET  = '<random string, 32+ characters>'
    .\scripts\local-demo\start-demo.ps1
#>
[CmdletBinding()]
param(
    [string]$RequestedProfile = 'local-demo',
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
$RefusedExitCode = 2

function Stop-Refused([string]$Reason) {
    [Console]::Error.WriteLine("REFUSED: $Reason")
    [Console]::Error.WriteLine("Nothing was started.")
    exit $RefusedExitCode
}

# --- 1. the profile: only "local-demo", compared case-sensitively --------------------------------------------------------
if ($RequestedProfile -cne 'local-demo') {
    Stop-Refused "the requested profile is '$RequestedProfile'; this wrapper only starts 'local-demo'."
}

# --- 2. repository root ---------------------------------------------------------------------------------------------------
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if (-not (Test-Path (Join-Path $root 'pom.xml'))) {
    Stop-Refused "could not find the repository root (no pom.xml at '$root')."
}
Set-Location $root

# --- 3. required variables (values are never printed) --------------------------------------------------------------------
$dbPassword = $env:AR_DEMO_DB_PASSWORD
$jwtSecret = $env:AR_DEMO_JWT_SECRET
if ([string]::IsNullOrEmpty($dbPassword)) {
    Stop-Refused "AR_DEMO_DB_PASSWORD is not set. Set it in this shell to the password of the local demo database user."
}
if ([string]::IsNullOrEmpty($jwtSecret)) {
    Stop-Refused "AR_DEMO_JWT_SECRET is not set. Set it in this shell to a random string of at least 32 characters."
}
if ($jwtSecret.Length -lt 32) {
    Stop-Refused "AR_DEMO_JWT_SECRET is too short ($($jwtSecret.Length) characters; at least 32 are required)."
}
$distinct = ($jwtSecret.ToCharArray() | Select-Object -Unique).Count
if ($distinct -lt 10) {
    Stop-Refused "AR_DEMO_JWT_SECRET is not random enough (only $distinct distinct characters; use a random string)."
}
if ($jwtSecret -match '[<>]') {
    Stop-Refused "AR_DEMO_JWT_SECRET still contains a documentation placeholder ('<' or '>'). Use a real random string."
}
if ($jwtSecret -ceq $dbPassword) {
    Stop-Refused "AR_DEMO_JWT_SECRET must not be the same value as the database password."
}

# --- 4. overrides that could change what the profile resolves ---------------------------------------------------------
$blockedVariables = @('SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD',
    'SPRING_PROFILES_INCLUDE', 'SPRING_CONFIG_LOCATION', 'SPRING_CONFIG_ADDITIONAL_LOCATION', 'SPRING_CONFIG_IMPORT',
    'SPRING_CONFIG_NAME')
foreach ($name in $blockedVariables) {
    if (-not [string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($name))) {
        Stop-Refused "the environment variable $name is set and could redirect the demo; unset it in this shell."
    }
}
foreach ($name in @('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'MAVEN_OPTS')) {
    $value = [Environment]::GetEnvironmentVariable($name)
    if ($value -and $value -match '-Dspring\.(profiles|datasource|config)') {
        Stop-Refused "$name sets a spring.profiles / spring.datasource / spring.config option; unset it in this shell."
    }
}

# --- 5. optional demo settings -----------------------------------------------------------------------------------------
$dbUrl = $env:AR_DEMO_DB_URL
if (-not [string]::IsNullOrEmpty($dbUrl)) {
    if ($dbUrl -notmatch '^jdbc:mysql://(localhost|127\.0\.0\.1|\[::1\])([:/?]|$)') {
        Stop-Refused "AR_DEMO_DB_URL must point at the local machine (localhost, 127.0.0.1 or [::1])."
    }
    $dbTarget = ($dbUrl -replace '^jdbc:mysql://', '' -replace '[?].*$', '')
} else {
    $dbTarget = '127.0.0.1:3399/ar_demo (default)'
}
if (-not [string]::IsNullOrEmpty($env:AR_DEMO_SERVER_PORT) -and $env:AR_DEMO_SERVER_PORT -notmatch '^\d{2,5}$') {
    Stop-Refused "AR_DEMO_SERVER_PORT must be a port number."
}
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Stop-Refused "Maven ('mvn') was not found on the PATH."
}

# --- 6. set the profile explicitly (never fall back to what the shell or .env say) -------------------------------------
$previous = $env:SPRING_PROFILES_ACTIVE
$env:SPRING_PROFILES_ACTIVE = 'local-demo'

Write-Host "AR local-demo start"
Write-Host "  repository root : $root"
Write-Host "  profile         : local-demo (SPRING_PROFILES_ACTIVE and -Dspring-boot.run.profiles)"
if (-not [string]::IsNullOrEmpty($previous) -and $previous -cne 'local-demo') {
    Write-Host "  note            : SPRING_PROFILES_ACTIVE was '$previous' in this shell; replaced by 'local-demo'"
}
Write-Host "  devtools restart: disabled (-Dspring.devtools.restart.enabled=false passed to the application JVM)"
Write-Host "  database target : $dbTarget"
Write-Host "  demo variables  : AR_DEMO_DB_PASSWORD set, AR_DEMO_JWT_SECRET set (values are not printed)"
Write-Host "  .env            : not read, not modified by this script"

if ($CheckOnly) {
    Write-Host "Checks passed (nothing started)."
    exit 0
}

# The application repeats the isolation check itself and refuses to start if anything shared could still be reached.
& mvn spring-boot:run "-Dspring-boot.run.profiles=local-demo" "-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false"
exit $LASTEXITCODE
