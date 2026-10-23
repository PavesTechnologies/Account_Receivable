<#
.SYNOPSIS
    Mints a demo-only HS256 JWT for the AR backend's "local-demo" profile.

.DESCRIPTION
    The local-demo profile verifies tokens against a LOCAL secret instead of the shared UMS issuer. This script signs a
    token with that same secret, read from the AR_DEMO_JWT_SECRET environment variable (at least 32 characters). The
    secret is never printed and never written to disk. The token is printed to stdout (and nothing else), so it can be
    captured:   $token = .\new-token.ps1

    The token carries the claims the backend reads: user_id, email, name, roles, permissions, iss, iat, exp. Nothing is
    bypassed: the backend still requires a valid, unexpired token and applies its existing role / permission handling.
    The signature-management endpoints still require the FINANCE_EXECUTIVE role, which is the default here; pass
    -Roles VIEWER to see how a token without it is treated.

.EXAMPLE
    $env:AR_DEMO_JWT_SECRET = '<the same value the backend runs with, 32+ characters>'
    $token = .\scripts\local-demo\new-token.ps1
    curl.exe -H "Authorization: Bearer $token" http://localhost:8080/paves/ar/api/billing-types

.EXAMPLE
    .\scripts\local-demo\new-token.ps1 -Roles FINANCE_EXECUTIVE,VIEWER -Permissions invoice.approve -TtlMinutes 30
#>
param(
    [string]$UserId = "1001",
    [string]$Email = "demo.finance@ar-demo.invalid",
    [string]$Name = "Demo Finance User",
    [string[]]$Roles = @("FINANCE_EXECUTIVE"),
    [string[]]$Permissions = @(),
    [ValidateRange(1, 1440)][int]$TtlMinutes = 120
)

$ErrorActionPreference = "Stop"

# Must equal DemoJwtConfig.ISSUER and app.security.jwt.issuer in application-local-demo.properties.
$issuer = "https://ar-demo.invalid/local"

$secret = $env:AR_DEMO_JWT_SECRET
if ([string]::IsNullOrEmpty($secret) -or $secret.Length -lt 32) {
    throw "Set AR_DEMO_JWT_SECRET to at least 32 characters - the same value the backend is started with."
}
if ($UserId -notmatch '^\d+$') {
    throw "-UserId must be numeric: the backend parses the user_id claim as a number."
}

function ConvertTo-Base64Url([byte[]]$Bytes) {
    [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

$now = [DateTimeOffset]::UtcNow
$header = '{"alg":"HS256","typ":"JWT"}'
$payload = [ordered]@{
    iss         = $issuer
    sub         = "demo-user-$UserId"
    user_id     = $UserId
    email       = $Email
    name        = $Name
    roles       = @($Roles)
    permissions = @($Permissions)
    iat         = $now.ToUnixTimeSeconds()
    exp         = $now.AddMinutes($TtlMinutes).ToUnixTimeSeconds()
} | ConvertTo-Json -Compress -Depth 4

$signingInput = (ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($header))) + "." +
                (ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($payload)))

$hmac = New-Object System.Security.Cryptography.HMACSHA256
try {
    $hmac.Key = [Text.Encoding]::UTF8.GetBytes($secret)
    $signature = ConvertTo-Base64Url ($hmac.ComputeHash([Text.Encoding]::ASCII.GetBytes($signingInput)))
} finally {
    $hmac.Dispose()
}

[Console]::Error.WriteLine("Demo token for user_id=$UserId roles=[$($Roles -join ',')] expires in $TtlMinutes minute(s) (UTC " +
    $now.AddMinutes($TtlMinutes).ToString("u") + ").")
"$signingInput.$signature"
