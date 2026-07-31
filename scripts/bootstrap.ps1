[CmdletBinding()]
param(
    [string]$Repository = "",
    [string]$WorkerName = "musicmc-upload",
    [string]$PagesOrigin = "",
    [string]$PortalUrl = "",
    [string]$UploadApiUrl = "",
    [string]$PrivateKeyPath = ".local/upload-ticket-private.pem",
    [string]$PublicKeyPath = "github-upload-service/config/upload-ticket-public.pem",
    [string]$PublicKeyRegistryPath = "github-upload-service/config/upload-ticket-public-keys.json",
    [string]$UploadKeyId = "default",
    [switch]$RotateUploadKey
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

function Require-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command is missing: $Name"
    }
}

function Invoke-Native([scriptblock]$Command, [string]$FailureMessage) {
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw $FailureMessage
    }
}

function ConvertFrom-Secure([Security.SecureString]$Value) {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Value)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

function Set-RepositorySecret([string]$Name, [string]$Value) {
    $Value | & gh secret set $Name --repo $Repository
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to set GitHub Actions secret $Name."
    }
}

function Set-RepositoryVariable([string]$Name, [string]$Value) {
    & gh variable set $Name --repo $Repository --body $Value
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to set GitHub Actions variable $Name."
    }
}

Require-Command git
Require-Command node
Require-Command npm
Require-Command npx
Require-Command gh

& gh auth status
if ($LASTEXITCODE -ne 0) {
    throw "GitHub CLI is not authenticated. Run: gh auth login"
}

if (-not $Repository) {
    $Repository = (& gh repo view --json nameWithOwner --jq .nameWithOwner).Trim()
}
if ($Repository -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') {
    throw "Repository must use owner/name format."
}

$Owner, $RepositoryName = $Repository.Split('/', 2)
if (-not $PagesOrigin) {
    $PagesOrigin = "https://$($Owner.ToLowerInvariant()).github.io"
}
$PagesOrigin = $PagesOrigin.TrimEnd('/')
if (-not $PortalUrl) {
    if ($RepositoryName.Equals("$Owner.github.io", [StringComparison]::OrdinalIgnoreCase)) {
        $PortalUrl = "$PagesOrigin/"
    }
    else {
        $PortalUrl = "$PagesOrigin/$RepositoryName/"
    }
}
if ($PagesOrigin -notmatch '^https://[^/]+$' -or $PortalUrl -notmatch '^https://') {
    throw "PagesOrigin must be an HTTPS origin and PortalUrl must be an HTTPS URL."
}

$privateFullPath = [IO.Path]::GetFullPath((Join-Path $Root $PrivateKeyPath))
$publicFullPath = [IO.Path]::GetFullPath((Join-Path $Root $PublicKeyPath))
$registryFullPath = [IO.Path]::GetFullPath((Join-Path $Root $PublicKeyRegistryPath))
if ($RotateUploadKey -and $UploadKeyId -eq "default") {
    $UploadKeyId = "key-$(Get-Date -AsUTC -Format 'yyyyMMddHHmmss')"
}
if ($UploadKeyId -notmatch '^[A-Za-z0-9_-]{1,32}$') {
    throw "UploadKeyId must contain only letters, numbers, underscores, or hyphens."
}
$keyArguments = @($privateFullPath, $publicFullPath)
if ($RotateUploadKey -or ((Test-Path -LiteralPath $publicFullPath) -and -not (Test-Path -LiteralPath $privateFullPath))) {
    $keyArguments += "--force"
}
if ($RotateUploadKey -or -not (Test-Path -LiteralPath $privateFullPath) -or -not (Test-Path -LiteralPath $publicFullPath)) {
    & node (Join-Path $Root "scripts/generate-keypair.mjs") @keyArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to generate the upload signing key pair."
    }
}
& node (Join-Path $Root "scripts/update-public-key-registry.mjs") `
    $registryFullPath $UploadKeyId $publicFullPath
if ($LASTEXITCODE -ne 0) {
    throw "Failed to update the upload public-key registry."
}

$githubToken = ""
$cloudflareToken = ""
$neteaseCookie = ""
try {
    $githubToken = ConvertFrom-Secure (Read-Host "GitHub fine-grained token (Contents and Issues: read/write)" -AsSecureString)
    if (-not $githubToken) {
        throw "A GitHub fine-grained token is required."
    }
    $cloudflareAccountId = (Read-Host "Cloudflare Account ID").Trim()
    $cloudflareToken = ConvertFrom-Secure (Read-Host "Cloudflare API token (Workers Scripts: Edit)" -AsSecureString)
    if (-not $cloudflareAccountId -or -not $cloudflareToken) {
        throw "Cloudflare Account ID and API token are required for automatic Worker deployment."
    }
    $neteaseCookie = ConvertFrom-Secure (Read-Host "NetEase Music Cookie (optional; Enter to skip)" -AsSecureString)

    & npx wrangler whoami
    if ($LASTEXITCODE -ne 0) {
        & npx wrangler login
        if ($LASTEXITCODE -ne 0) {
            throw "Wrangler login failed."
        }
    }

    Push-Location (Join-Path $Root "github-upload-service/worker")
    try {
        & npm ci
        if ($LASTEXITCODE -ne 0) {
            throw "Worker dependencies could not be installed."
        }
        $deployLines = & npx wrangler deploy --name $WorkerName `
            --var "ALLOWED_ORIGIN:$PagesOrigin" `
          --var "GITHUB_REPOSITORY:$Repository" `
          --var "GITHUB_BRANCH:main" `
          --var "MAX_PENDING_UPLOADS:50" 2>&1
        $deployLines | ForEach-Object { Write-Host $_ }
        if ($LASTEXITCODE -ne 0) {
            throw "Worker deployment failed."
        }
        $githubToken | & npx wrangler secret put GITHUB_TOKEN --name $WorkerName
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to set Worker secret GITHUB_TOKEN."
        }
        (Get-Content -LiteralPath $publicFullPath -Raw -Encoding utf8) |
            & npx wrangler secret put UPLOAD_PUBLIC_KEY --name $WorkerName
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to set Worker secret UPLOAD_PUBLIC_KEY."
        }
        (Get-Content -LiteralPath $registryFullPath -Raw -Encoding utf8) |
            & npx wrangler secret put UPLOAD_PUBLIC_KEYS --name $WorkerName
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to set Worker secret UPLOAD_PUBLIC_KEYS."
        }
    }
    finally {
        Pop-Location
    }

    if (-not $UploadApiUrl) {
        $deployText = $deployLines -join "`n"
        $match = [regex]::Match($deployText, 'https://[A-Za-z0-9.-]+\.workers\.dev')
        if (-not $match.Success) {
            throw "Wrangler deployed the Worker but its public URL could not be detected. Pass -UploadApiUrl explicitly."
        }
        $UploadApiUrl = $match.Value
    }
    $UploadApiUrl = $UploadApiUrl.TrimEnd('/')

    Set-RepositorySecret "MUSICMC_AUTOMATION_TOKEN" $githubToken
    Set-RepositorySecret "CLOUDFLARE_API_TOKEN" $cloudflareToken
    Set-RepositorySecret "CLOUDFLARE_ACCOUNT_ID" $cloudflareAccountId
    if ($neteaseCookie) {
        Set-RepositorySecret "NETEASE_COOKIE" $neteaseCookie
    }
    Set-RepositoryVariable "MUSICMC_WORKER_NAME" $WorkerName
    Set-RepositoryVariable "MUSICMC_UPLOAD_API_URL" $UploadApiUrl
    Set-RepositoryVariable "MUSICMC_PAGES_ORIGIN" $PagesOrigin
    Set-RepositoryVariable "MUSICMC_PORTAL_URL" $PortalUrl

    $labels = @(
        @{ Name = "music-pending"; Color = "d4c5f9"; Description = "Music upload waiting for processing" },
        @{ Name = "music-processing"; Color = "fbca04"; Description = "Music upload is being processed" },
        @{ Name = "music-ready"; Color = "0e8a16"; Description = "Music upload was published" },
        @{ Name = "music-failed"; Color = "d93f0b"; Description = "Automatic music processing failed" }
    )
    foreach ($label in $labels) {
        & gh label create $label.Name --repo $Repository --color $label.Color --description $label.Description --force
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to create repository label $($label.Name)."
        }
    }

    & gh api --method POST "repos/$Repository/pages" -f build_type=workflow 2>$null
    if ($LASTEXITCODE -ne 0) {
        & gh api --method PUT "repos/$Repository/pages" -f build_type=workflow | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "GitHub Pages could not be enabled automatically. Select GitHub Actions in Settings > Pages."
        }
    }

    & git add -- $PublicKeyPath $PublicKeyRegistryPath
    & git diff --cached --quiet
    if ($LASTEXITCODE -ne 0) {
        & git commit -m "Configure MusicMC upload public key"
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to commit the public upload key."
        }
    }
    & git push origin HEAD:main
    if ($LASTEXITCODE -ne 0) {
        throw "Deployment is configured, but the public key commit could not be pushed."
    }

    & gh workflow run pages.yml --repo $Repository --ref main
    if ($LASTEXITCODE -ne 0) {
        throw "Deployment is configured, but the manual Pages workflow could not be started."
    }

    Write-Host ""
    Write-Host "MusicMC deployment configured."
    Write-Host "Portal:     $PortalUrl"
    Write-Host "Upload API: $UploadApiUrl"
    Write-Host "Private key (copy only to plugins/MusicMC/upload-ticket-private.pem): $privateFullPath"
    Write-Host "Set upload.key-id in plugins/MusicMC/config.yml to: $UploadKeyId"
}
finally {
    $githubToken = $null
    $cloudflareToken = $null
    $neteaseCookie = $null
}
