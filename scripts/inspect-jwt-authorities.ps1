<#
.SYNOPSIS
    Shows which roles and permissions a UMS access token carries, so AR_AUTH_* can be set to the exact values.

.DESCRIPTION
    Reads the token from the clipboard (copy the access token, or the whole "Bearer ..." Authorization header value,
    from the browser's DevTools > Network tab for any AR API call). Prints ONLY the claim names, user_id presence,
    expiry, and the "roles" / "permissions" values. The token, its signature and other claim values are never
    printed, written to disk or sent anywhere. The signature is NOT verified - this is a diagnostic, not an auth check.

.EXAMPLE
    .\scripts\inspect-jwt-authorities.ps1
#>
$ErrorActionPreference = "Stop"

$raw = (Get-Clipboard -Raw)
if (-not $raw) { throw "The clipboard is empty: copy the access token first." }
$token = ($raw.Trim() -replace '^(?i)bearer\s+', '')
$parts = $token.Split('.')
if ($parts.Count -ne 3) { throw "The clipboard does not hold a JWT (expected header.payload.signature)." }

$payload = $parts[1].Replace('-', '+').Replace('_', '/')
switch ($payload.Length % 4) { 2 { $payload += '==' } 3 { $payload += '=' } }
$claims = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload)) | ConvertFrom-Json

function Show-List($name) {
    $value = $claims.$name
    if ($null -eq $value) { "{0,-12}: (claim absent)" -f $name; return }
    $kind = if ($value -is [array]) { "list" } else { "single value, type $($value.GetType().Name)" }
    "{0,-12}: [{1}] ({2})" -f $name, ((@($value) | ForEach-Object { '"' + $_ + '"' }) -join ', '), $kind
}

"claim names : " + (($claims.PSObject.Properties.Name | Sort-Object) -join ', ')
"user_id     : " + $(if ($claims.user_id) { "present" } else { "MISSING - AR rejects tokens without it" })
if ($claims.exp) { "expires     : " + [DateTimeOffset]::FromUnixTimeSeconds([long]$claims.exp).LocalDateTime }
Show-List "roles"
Show-List "permissions"
""
"AR compares roles as ROLE_<role> and permissions as-is (exact, case-sensitive). Candidate authority values:"
@($claims.roles) | Where-Object { $_ } | ForEach-Object { "  ROLE_$_" }
@($claims.permissions) | Where-Object { $_ } | ForEach-Object { "  $_" }

Set-Clipboard -Value $null
"(clipboard cleared)"
