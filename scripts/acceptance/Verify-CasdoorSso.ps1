<#
.SYNOPSIS
Verifies the Casdoor OIDC SSO acceptance baseline for two independent systems.

.DESCRIPTION
This script checks the parts that can be verified without storing secrets:
- Casdoor OIDC discovery endpoint
- JWKS endpoint
- Authorization Code + PKCE login entry for two clients
- Optional protected-resource behavior for two systems
- Optional JWT payload sanity checks when an access token is provided

It does not automate username/password browser login. Full SSO still requires
opening both generated authorization URLs in the same browser session.
#>

[CmdletBinding()]
param(
    [string]$CasdoorBaseUrl = "http://localhost:8000",
    [string]$ApplicationName = "",

    [string]$SystemAName = "xyyx",
    [string]$SystemAClientId = "xyyx-web",
    [string]$SystemARedirectUri = "http://localhost:5173/auth/callback",
    [string]$SystemAProtectedUrl = "",

    [string]$SystemBName = "future-system",
    [string]$SystemBClientId = "future-system-web",
    [string]$SystemBRedirectUri = "http://localhost:5174/auth/callback",
    [string]$SystemBProtectedUrl = "",

    [string]$AccessToken = "",
    [string]$ExpectedIssuer = "",
    [switch]$SelfTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$script:PassCount = 0
$script:FailCount = 0
$script:GeneratedAuthorizeUrls = @()

function Write-Section {
    param([string]$Text)
    Write-Host ""
    Write-Host "== $Text =="
}

function Pass {
    param([string]$Message)
    $script:PassCount++
    Write-Host "[PASS] $Message" -ForegroundColor Green
}

function Fail {
    param([string]$Message)
    $script:FailCount++
    Write-Host "[FAIL] $Message" -ForegroundColor Red
}

function Assert-True {
    param(
        [bool]$Condition,
        [string]$PassMessage,
        [string]$FailMessage
    )
    if ($Condition) {
        Pass $PassMessage
    } else {
        Fail $FailMessage
    }
}

function Normalize-BaseUrl {
    param([string]$Url)
    return $Url.TrimEnd("/")
}

function ConvertTo-Base64Url {
    param([byte[]]$Bytes)
    return [Convert]::ToBase64String($Bytes).TrimEnd("=").Replace("+", "-").Replace("/", "_")
}

function ConvertFrom-Base64UrlJson {
    param([string]$Value)
    $padded = $Value.Replace("-", "+").Replace("_", "/")
    switch ($padded.Length % 4) {
        2 { $padded += "==" }
        3 { $padded += "=" }
        1 { throw "Invalid base64url length." }
    }
    $bytes = [Convert]::FromBase64String($padded)
    $json = [Text.Encoding]::UTF8.GetString($bytes)
    return $json | ConvertFrom-Json
}

function New-PkcePair {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    $verifier = ConvertTo-Base64Url $bytes
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $challengeBytes = $sha.ComputeHash([Text.Encoding]::ASCII.GetBytes($verifier))
    } finally {
        $sha.Dispose()
    }
    return [pscustomobject]@{
        Verifier = $verifier
        Challenge = ConvertTo-Base64Url $challengeBytes
    }
}

function Invoke-JsonGet {
    param([string]$Url)
    $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -Headers @{ Accept = "application/json" }
    return $response.Content | ConvertFrom-Json
}

function Test-EndpointStatus {
    param(
        [string]$Url,
        [int[]]$AcceptedStatusCodes
    )
    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -MaximumRedirection 0
        return [int]$response.StatusCode
    } catch {
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            return [int]$_.Exception.Response.StatusCode
        }
        throw
    }
}

function New-AuthorizeUrl {
    param(
        [object]$Metadata,
        [string]$ClientId,
        [string]$RedirectUri,
        [string]$State
    )
    $pkce = New-PkcePair
    $query = @{
        client_id = $ClientId
        response_type = "code"
        redirect_uri = $RedirectUri
        scope = "openid profile email"
        state = $State
        code_challenge = $pkce.Challenge
        code_challenge_method = "S256"
    }
    $parts = foreach ($key in $query.Keys) {
        "{0}={1}" -f [Uri]::EscapeDataString($key), [Uri]::EscapeDataString([string]$query[$key])
    }
    return "{0}?{1}" -f $Metadata.authorization_endpoint, ($parts -join "&")
}

function Test-Discovery {
    param([string]$BaseUrl, [string]$AppName)
    Write-Section "Casdoor discovery"
    if ([string]::IsNullOrWhiteSpace($AppName)) {
        $discoveryUrl = "$BaseUrl/.well-known/openid-configuration"
    } else {
        $discoveryUrl = "$BaseUrl/.well-known/$AppName/openid-configuration"
    }
    Write-Host "Discovery URL: $discoveryUrl"
    $metadata = Invoke-JsonGet $discoveryUrl

    $requiredFields = @(
        "issuer",
        "authorization_endpoint",
        "token_endpoint",
        "userinfo_endpoint",
        "jwks_uri"
    )
    foreach ($field in $requiredFields) {
        Assert-True ([bool]$metadata.PSObject.Properties[$field]) "metadata has $field" "metadata missing $field"
    }

    $grantTypes = @($metadata.grant_types_supported)
    Assert-True ($grantTypes -contains "authorization_code") "supports authorization_code grant" "authorization_code grant not advertised"
    Assert-True ($grantTypes -contains "refresh_token") "supports refresh_token grant" "refresh_token grant not advertised"

    $pkceMethods = @($metadata.code_challenge_methods_supported)
    Assert-True ($pkceMethods -contains "S256") "supports PKCE S256" "PKCE S256 not advertised"

    $scopes = @($metadata.scopes_supported)
    Assert-True ($scopes -contains "openid") "supports openid scope" "openid scope not advertised"

    return $metadata
}

function Test-Jwks {
    param([object]$Metadata)
    Write-Section "JWKS"
    Write-Host "JWKS URL: $($Metadata.jwks_uri)"
    $jwks = Invoke-JsonGet $Metadata.jwks_uri
    $keys = @($jwks.keys)
    Assert-True ($keys.Count -gt 0) "JWKS has signing keys" "JWKS has no signing keys"
    if ($keys.Count -gt 0) {
        $rsaKeys = @($keys | Where-Object { $_.kty -eq "RSA" })
        Assert-True ($rsaKeys.Count -gt 0) "JWKS contains RSA key(s)" "JWKS does not contain RSA keys"
    }
    return $jwks
}

function Test-AuthorizeEntry {
    param(
        [object]$Metadata,
        [string]$SystemName,
        [string]$ClientId,
        [string]$RedirectUri
    )
    Write-Section "Authorization Code + PKCE entry: $SystemName"
    $state = "acceptance-$SystemName-$([Guid]::NewGuid().ToString("N"))"
    $url = New-AuthorizeUrl -Metadata $Metadata -ClientId $ClientId -RedirectUri $RedirectUri -State $state
    $script:GeneratedAuthorizeUrls += [pscustomobject]@{
        System = $SystemName
        Url = $url
    }
    Write-Host "Authorize URL: $url"

    Assert-True ($url -like "*code_challenge_method=S256*") "$SystemName authorize URL uses PKCE S256" "$SystemName authorize URL missing PKCE S256"

    $status = Test-EndpointStatus -Url $url -AcceptedStatusCodes @(200, 302, 303)
    Assert-True (@(200, 302, 303) -contains $status) "$SystemName authorization endpoint reachable, HTTP $status" "$SystemName authorization endpoint unexpected HTTP $status"
}

function Test-JwtPayload {
    param(
        [string]$Token,
        [object]$Metadata,
        [object]$Jwks,
        [string]$IssuerOverride
    )
    if ([string]::IsNullOrWhiteSpace($Token)) {
        Write-Section "JWT checks"
        Write-Host "Skipped: no AccessToken was provided."
        return
    }

    Write-Section "JWT checks"
    $parts = $Token.Split(".")
    Assert-True ($parts.Count -eq 3) "access token is JWT-shaped" "access token is not JWT-shaped"
    if ($parts.Count -ne 3) { return }

    $header = ConvertFrom-Base64UrlJson $parts[0]
    $payload = ConvertFrom-Base64UrlJson $parts[1]
    $issuer = if ([string]::IsNullOrWhiteSpace($IssuerOverride)) { $Metadata.issuer } else { $IssuerOverride }

    Assert-True ($payload.iss -eq $issuer) "token issuer matches expected issuer" "token issuer mismatch: $($payload.iss)"
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$payload.sub)) "token contains subject sub" "token missing subject sub"
    Assert-True (-not [string]::IsNullOrWhiteSpace([string]$payload.aud)) "token contains audience aud" "token missing audience aud"

    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    Assert-True ([int64]$payload.exp -gt $now) "token is not expired" "token is expired"

    if ($header.kid) {
        $matchingKey = @($Jwks.keys | Where-Object { $_.kid -eq $header.kid })
        Assert-True ($matchingKey.Count -gt 0) "JWT kid exists in JWKS" "JWT kid not found in JWKS"
    } else {
        Fail "JWT header missing kid"
    }
}

function Test-ProtectedResource {
    param(
        [string]$SystemName,
        [string]$Url,
        [string]$Token
    )
    if ([string]::IsNullOrWhiteSpace($Url)) {
        Write-Section "Protected resource: $SystemName"
        Write-Host "Skipped: no protected URL was provided."
        return
    }

    Write-Section "Protected resource: $SystemName"
    Write-Host "Protected URL: $Url"
    $anonymousStatus = Test-EndpointStatus -Url $Url -AcceptedStatusCodes @(401, 403)
    Assert-True (@(401, 403) -contains $anonymousStatus) "$SystemName rejects anonymous request, HTTP $anonymousStatus" "$SystemName did not reject anonymous request, HTTP $anonymousStatus"

    if ([string]::IsNullOrWhiteSpace($Token)) {
        Write-Host "Skipped authenticated check: no AccessToken was provided."
        return
    }

    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -Headers @{ Authorization = "Bearer $Token" }
        $code = [int]$response.StatusCode
        Assert-True ($code -ge 200 -and $code -lt 300) "$SystemName accepts Casdoor token, HTTP $code" "$SystemName token request unexpected HTTP $code"
    } catch {
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $code = [int]$_.Exception.Response.StatusCode
            Fail "$SystemName rejected Casdoor token, HTTP $code"
        } else {
            throw
        }
    }
}

function Invoke-SelfTest {
    Write-Section "Self test"
    $pair = New-PkcePair
    Assert-True ($pair.Verifier.Length -ge 43) "PKCE verifier length is valid" "PKCE verifier too short"
    Assert-True ($pair.Challenge.Length -ge 43) "PKCE challenge length is valid" "PKCE challenge too short"

    $samplePayload = '{"iss":"http://localhost:8000","sub":"user-1","aud":"xyyx-web","exp":4102444800}'
    $encodedPayload = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($samplePayload))
    $decodedPayload = ConvertFrom-Base64UrlJson $encodedPayload
    Assert-True ($decodedPayload.sub -eq "user-1") "base64url JSON decoder works" "base64url JSON decoder failed"
}

if ($SelfTest) {
    Invoke-SelfTest
} else {
    $baseUrl = Normalize-BaseUrl $CasdoorBaseUrl
    $metadata = Test-Discovery -BaseUrl $baseUrl -AppName $ApplicationName
    $jwks = Test-Jwks -Metadata $metadata

    Test-AuthorizeEntry -Metadata $metadata -SystemName $SystemAName -ClientId $SystemAClientId -RedirectUri $SystemARedirectUri
    Test-AuthorizeEntry -Metadata $metadata -SystemName $SystemBName -ClientId $SystemBClientId -RedirectUri $SystemBRedirectUri

    Test-JwtPayload -Token $AccessToken -Metadata $metadata -Jwks $jwks -IssuerOverride $ExpectedIssuer
    Test-ProtectedResource -SystemName $SystemAName -Url $SystemAProtectedUrl -Token $AccessToken
    Test-ProtectedResource -SystemName $SystemBName -Url $SystemBProtectedUrl -Token $AccessToken
}

Write-Section "Manual browser acceptance"
foreach ($item in $script:GeneratedAuthorizeUrls) {
    Write-Host "$($item.System): $($item.Url)"
}
if ($script:GeneratedAuthorizeUrls.Count -gt 0) {
    Write-Host "Open both URLs in the same browser session. Acceptance passes when the second system enters without asking for the password again."
}

Write-Section "Summary"
Write-Host "Passed: $script:PassCount"
Write-Host "Failed: $script:FailCount"

if ($script:FailCount -gt 0) {
    exit 1
}

exit 0
