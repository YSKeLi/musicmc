[CmdletBinding()]
param(
    [ValidateSet('Automatic', 'Manual')]
    [string]$Mode = 'Automatic',
    [string]$GitHubOwner = '',
    [string]$Repository = '',
    [string]$PortalRepository = '',
    [string]$PortalUrl = '',
    [string]$Branch = 'main',
    [string]$WorkerName = 'musicmc-upload',
    [string]$CloudflareAccountId = '',
    [string]$CloudflareSubdomain = '',
    [string]$PrivateKeyPath = '.local/upload-ticket-private.pem',
    [string]$UploadKeyId = 'default',
    [string]$PluginOutputPath = 'output/MusicMC-server.jar',
    [switch]$MigrateCatalogNow,
    [switch]$ReplaceExistingUserSite,
    [switch]$AllowRepositoryDeletion,
    [switch]$ReplaceExistingWorker,
    [switch]$SkipPortalDeployment,
    [switch]$SkipWorkerDeployment,
    [switch]$SkipPluginBuild,
    [switch]$PlanOnly,
    [switch]$NonInteractive
)

$ErrorActionPreference = 'Stop'
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

function Read-Secret([string]$Prompt, [string]$EnvironmentName, [switch]$Optional) {
    $existing = [Environment]::GetEnvironmentVariable($EnvironmentName)
    if ($existing) {
        return $existing
    }
    if ($NonInteractive) {
        if ($Optional) { return '' }
        throw "$EnvironmentName is required in non-interactive mode."
    }
    $value = ConvertFrom-Secure (Read-Host $Prompt -AsSecureString)
    if (-not $value -and -not $Optional) {
        throw "$Prompt is required."
    }
    return $value
}

function Read-Value([string]$Prompt, [string]$Current, [string]$Default = '') {
    if ($Current) { return $Current.Trim() }
    if ($NonInteractive) {
        if ($Default) { return $Default }
        throw "$Prompt is required in non-interactive mode."
    }
    $label = if ($Default) { "$Prompt [$Default]" } else { $Prompt }
    $answer = (Read-Host $label).Trim()
    return if ($answer) { $answer } else { $Default }
}

function Test-GitHubRepository([string]$Name) {
    & gh repo view $Name --json nameWithOwner --jq '.nameWithOwner' *> $null
    return $LASTEXITCODE -eq 0
}

function New-GitHubRepository([string]$Name, [string]$Description) {
    Invoke-Native { gh repo create $Name --public --description $Description } `
        "Could not create GitHub repository $Name."
}

function Get-GitHubVariable([string]$Name, [string]$Repo) {
    $value = & gh variable get $Name --repo $Repo 2>$null
    if ($LASTEXITCODE -ne 0) { return '' }
    return ($value -join "`n").Trim()
}

function Set-GitHubVariable([string]$Name, [string]$Value, [string]$Repo) {
    Invoke-Native { gh variable set $Name --repo $Repo --body $Value } `
        "Could not set repository variable $Name."
}

function Set-GitHubSecret([string]$Name, [string]$Value, [string]$Repo) {
    $Value | & gh secret set $Name --repo $Repo
    if ($LASTEXITCODE -ne 0) {
        throw "Could not set repository secret $Name."
    }
}

function Invoke-Cloudflare([string]$Method, [string]$Path, [object]$Body = $null) {
    $headers = @{ Authorization = "Bearer $script:CloudflareToken" }
    $parameters = @{
        Method = $Method
        Uri = "https://api.cloudflare.com/client/v4$Path"
        Headers = $headers
        ContentType = 'application/json'
    }
    if ($null -ne $Body) {
        $parameters.Body = $Body | ConvertTo-Json -Compress
    }
    $response = Invoke-RestMethod @parameters
    if (-not $response.success) {
        throw "Cloudflare API request failed: $Path"
    }
    return $response.result
}

function Test-CloudflareWorker([string]$Name) {
    try {
        Invoke-Cloudflare GET "/accounts/$CloudflareAccountId/workers/scripts/$Name/settings" | Out-Null
        return $true
    }
    catch {
        $statusCode = if ($_.Exception.Response) {
            [int]$_.Exception.Response.StatusCode
        }
        else { 0 }
        if ($statusCode -eq 404) {
            return $false
        }
        throw
    }
}

function Find-FreeWorkerName([string]$BaseName) {
    for ($index = 2; $index -le 99; $index++) {
        $candidate = "$BaseName-$index"
        if (-not (Test-CloudflareWorker $candidate)) { return $candidate }
    }
    throw "Could not find a free Worker name."
}

function Find-FreeGitHubRepository([string]$BaseName) {
    if (-not (Test-GitHubRepository $BaseName)) { return $BaseName }
    for ($index = 2; $index -le 99; $index++) {
        $candidate = "$BaseName-$index"
        if (-not (Test-GitHubRepository $candidate)) { return $candidate }
    }
    throw "Could not find a free GitHub repository name."
}

function Copy-DirectoryContents([string]$Source, [string]$Destination) {
    if (Test-Path -LiteralPath $Destination) {
        Remove-Item -LiteralPath $Destination -Recurse -Force
    }
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    Copy-Item -Path (Join-Path $Source '*') -Destination $Destination -Recurse -Force
}

function Copy-DeploymentSources([string]$RuntimeDirectory) {
    $serviceRoot = Join-Path $RuntimeDirectory 'github-upload-service'
    $actionsDestination = Join-Path $serviceRoot 'actions'
    $scriptsDestination = Join-Path $serviceRoot 'scripts'
    $testsDestination = Join-Path $serviceRoot 'tests'
    $workerDestination = Join-Path $serviceRoot 'worker'
    foreach ($destination in @(
        $actionsDestination, $scriptsDestination, $testsDestination, $workerDestination
    )) {
        if (Test-Path -LiteralPath $destination) {
            Remove-Item -LiteralPath $destination -Recurse -Force
        }
        New-Item -ItemType Directory -Path $destination -Force | Out-Null
    }

    foreach ($name in @('netease-adapter.cjs', 'package.json', 'package-lock.json')) {
        Copy-Item -LiteralPath (Join-Path $Root "github-upload-service/actions/$name") `
            -Destination $actionsDestination -Force
    }
    Get-ChildItem -LiteralPath (Join-Path $Root 'github-upload-service/scripts') `
        -Filter '*.py' -File | Copy-Item -Destination $scriptsDestination -Force
    Get-ChildItem -LiteralPath (Join-Path $Root 'github-upload-service/tests') -File |
        Where-Object { $_.Extension -eq '.py' -or $_.Name -eq 'netease-adapter.test.mjs' } |
        Copy-Item -Destination $testsDestination -Force

    foreach ($name in @('package.json', 'package-lock.json', 'wrangler.jsonc')) {
        Copy-Item -LiteralPath (Join-Path $Root "github-upload-service/worker/$name") `
            -Destination $workerDestination -Force
    }
    Copy-DirectoryContents (Join-Path $Root 'github-upload-service/worker/src') `
        (Join-Path $workerDestination 'src')
    Copy-DirectoryContents (Join-Path $Root 'github-upload-service/worker/test') `
        (Join-Path $workerDestination 'test')
}

function Initialize-RepositoryBranch([string]$Directory) {
    Push-Location $Directory
    try {
        & git rev-parse --verify HEAD *> $null
        if ($LASTEXITCODE -eq 0) {
            Invoke-Native { git switch -C $Branch } "Could not switch to branch $Branch."
        }
        else {
            Invoke-Native { git switch --orphan $Branch } "Could not initialize branch $Branch."
        }
    }
    finally {
        Pop-Location
    }
}

function Resolve-PythonCommand {
    foreach ($candidate in @(
        @{ Command = 'python3'; Prefix = @() },
        @{ Command = 'python'; Prefix = @() },
        @{ Command = 'py'; Prefix = @('-3') }
    )) {
        if (-not (Get-Command $candidate.Command -ErrorAction SilentlyContinue)) { continue }
        & $candidate.Command @($candidate.Prefix) --version *> $null
        if ($LASTEXITCODE -eq 0) { return $candidate }
    }
    throw 'Python 3 is required for catalog validation and migration.'
}

function Commit-And-Push([string]$Directory, [string]$Message) {
    Push-Location $Directory
    try {
        & git add --all
        & git diff --cached --quiet
        if ($LASTEXITCODE -eq 0) {
            Write-Host "No repository changes are required in $Directory"
            return
        }
        Invoke-Native { git -c user.name='MusicMC Deployment' `
                -c user.email='musicmc-deployment@users.noreply.github.com' `
                commit -m $Message } "Could not commit deployment files."
        Invoke-Native { git push origin "HEAD:$Branch" } "Could not push deployment files."
    }
    finally {
        Pop-Location
    }
}

function Resolve-PortalTarget {
    if ($SkipPortalDeployment) {
        return [pscustomobject]@{ Repository = ''; Url = $PortalUrl; DeleteExisting = $false }
    }
    if ($Mode -eq 'Manual') {
        $manualRepository = Read-Value 'GitHub Pages repository (OWNER/REPOSITORY)' $PortalRepository
        $manualUrl = Read-Value 'Public portal URL' $PortalUrl
        return [pscustomobject]@{
            Repository = $manualRepository
            Url = $manualUrl.TrimEnd('/') + '/'
            DeleteExisting = $false
        }
    }

    $userSite = "$GitHubOwner/$GitHubOwner.github.io"
    if (-not (Test-GitHubRepository $userSite)) {
        return [pscustomobject]@{
            Repository = $userSite
            Url = "https://$($GitHubOwner.ToLowerInvariant()).github.io/"
            DeleteExisting = $false
        }
    }

    $deleteUserSite = $ReplaceExistingUserSite
    if (-not $NonInteractive -and -not $ReplaceExistingUserSite) {
        Write-Host "GitHub Pages repository already exists: $userSite" -ForegroundColor Yellow
        $choice = (Read-Host `
            'Choose DELETE to delete/recreate it, FALLBACK to use a separate project site, or ABORT' `
        ).Trim().ToUpperInvariant()
        if ($choice -eq 'DELETE') {
            $confirmation = (Read-Host "Type the complete repository name '$userSite' to confirm permanent deletion").Trim()
            $deleteUserSite = $confirmation -eq $userSite
            if (-not $deleteUserSite) { throw 'Repository deletion was not confirmed.' }
        }
        elseif ($choice -ne 'FALLBACK') {
            throw 'Deployment aborted before changing the Pages repository.'
        }
    }
    if ($deleteUserSite) {
        if ($NonInteractive -and -not $AllowRepositoryDeletion) {
            throw 'AllowRepositoryDeletion is required with ReplaceExistingUserSite in non-interactive mode.'
        }
        return [pscustomobject]@{
            Repository = $userSite
            Url = "https://$($GitHubOwner.ToLowerInvariant()).github.io/"
            DeleteExisting = $true
        }
    }

    $fallback = "$GitHubOwner/$($GitHubOwner.ToLowerInvariant())mcmusic.github.io"
    if (Test-GitHubRepository $fallback) {
        if ($NonInteractive) {
            $fallback = Find-FreeGitHubRepository $fallback
        }
        else {
            $fallbackChoice = (Read-Host `
                "Fallback repository '$fallback' exists. Choose UPDATE, NEW, or ABORT" `
            ).Trim().ToUpperInvariant()
            if ($fallbackChoice -eq 'NEW') {
                $fallback = Find-FreeGitHubRepository $fallback
            }
            elseif ($fallbackChoice -ne 'UPDATE') {
                throw 'Deployment aborted before changing the fallback Pages repository.'
            }
        }
    }
    $fallbackName = ($fallback -split '/', 2)[1]
    return [pscustomobject]@{
        Repository = $fallback
        Url = "https://$($GitHubOwner.ToLowerInvariant()).github.io/$fallbackName/"
        DeleteExisting = $false
    }
}

function Prepare-SigningKey([string]$RuntimeDirectory, [string]$TemporaryDirectory) {
    $privateFullPath = if ([IO.Path]::IsPathRooted($PrivateKeyPath)) {
        [IO.Path]::GetFullPath($PrivateKeyPath)
    }
    else {
        [IO.Path]::GetFullPath((Join-Path $Root $PrivateKeyPath))
    }
    $derivedPublic = Join-Path $TemporaryDirectory 'upload-ticket-public.pem'
    if (Test-Path -LiteralPath $privateFullPath) {
        Invoke-Native { node (Join-Path $Root 'scripts/derive-public-key.mjs') `
                $privateFullPath $derivedPublic } 'Could not derive the upload public key.'
    }
    else {
        $existingPublic = @(
            (Join-Path $RuntimeDirectory 'github-upload-service/config/upload-ticket-public.pem'),
            (Join-Path $RuntimeDirectory 'config/upload-ticket-public.pem')
        ) | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
        if ($existingPublic) {
            throw "The runtime repository already has a public key, but its private key was not found at $privateFullPath. Pass -PrivateKeyPath for the server's existing key."
        }
        $localPublic = Join-Path (Split-Path -Parent $privateFullPath) 'upload-ticket-public.pem'
        Invoke-Native { node (Join-Path $Root 'scripts/generate-keypair.mjs') `
                $privateFullPath $localPublic } 'Could not generate upload signing keys.'
        Copy-Item -LiteralPath $localPublic -Destination $derivedPublic -Force
    }
    return [pscustomobject]@{ Private = $privateFullPath; Public = $derivedPublic }
}

if ($UploadKeyId -notmatch '^[A-Za-z0-9_-]{1,32}$') {
    throw 'UploadKeyId contains unsupported characters.'
}
if ($WorkerName -notmatch '^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$') {
    throw 'WorkerName must be a valid DNS label of at most 63 characters.'
}
if ($CloudflareSubdomain `
        -and $CloudflareSubdomain -notmatch '^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$') {
    throw 'CloudflareSubdomain must be a valid DNS label of at most 63 characters.'
}
if ($Branch -notmatch '^[A-Za-z0-9._/-]{1,200}$' -or $Branch.Contains('..')) {
    throw 'Branch contains unsupported characters.'
}

if ($PlanOnly) {
    $GitHubOwner = Read-Value 'GitHub user name' $GitHubOwner
    if (-not $Repository) { $Repository = "$GitHubOwner/MusicMC-Uploads" }
    if (-not $CloudflareSubdomain) { $CloudflareSubdomain = "$($GitHubOwner.ToLowerInvariant())-musicmc" }
    if (-not $PortalRepository) { $PortalRepository = "$GitHubOwner/$GitHubOwner.github.io" }
    if (-not $PortalUrl) { $PortalUrl = "https://$($GitHubOwner.ToLowerInvariant()).github.io/" }
    [pscustomobject]@{
        Mode = $Mode
        Repository = $Repository
        PortalRepository = $PortalRepository
        PortalUrl = $PortalUrl
        WorkerName = $WorkerName
        WorkerUrl = "https://$WorkerName.$CloudflareSubdomain.workers.dev"
        Branch = $Branch
        MutatesExternalServices = $false
    }
    return
}

Require-Command git
Require-Command node
Require-Command npm
Require-Command npx
Require-Command gh
$script:Python = Resolve-PythonCommand

# Deployment credentials are collected and validated before any external mutation.
$originalGhToken = $env:GH_TOKEN
$originalCloudflareToken = $env:CLOUDFLARE_API_TOKEN
$originalCloudflareAccount = $env:CLOUDFLARE_ACCOUNT_ID
$githubToken = Read-Secret 'GitHub deployment token (repository, workflow, Issues, Releases, Pages)' 'GH_TOKEN'
$script:CloudflareToken = Read-Secret 'Cloudflare API token (Workers Scripts: Edit)' 'CLOUDFLARE_API_TOKEN'
$CloudflareAccountId = Read-Value 'Cloudflare Account ID' `
    $(if ($CloudflareAccountId) { $CloudflareAccountId } else { $env:CLOUDFLARE_ACCOUNT_ID })
$neteaseCookie = Read-Secret 'NetEase Music Cookie (optional; Enter to skip)' 'NETEASE_COOKIE' -Optional

$env:GH_TOKEN = $githubToken
$env:CLOUDFLARE_API_TOKEN = $script:CloudflareToken
$env:CLOUDFLARE_ACCOUNT_ID = $CloudflareAccountId
$temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) ("musicmc-deploy-" + [guid]::NewGuid().ToString('N'))

try {
    $GitHubOwner = (& gh api user --jq '.login').Trim()
    if ($LASTEXITCODE -ne 0 -or -not $GitHubOwner) {
        throw 'GitHub token validation failed.'
    }
    Invoke-Cloudflare GET "/accounts/$CloudflareAccountId/workers/account-settings" | Out-Null

    if ($Mode -eq 'Automatic') {
        if (-not $Repository) { $Repository = "$GitHubOwner/MusicMC-Uploads" }
    }
    else {
        $Repository = Read-Value 'Music runtime repository (OWNER/REPOSITORY)' $Repository
    }
    if ($Repository -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') {
        throw 'Repository must use OWNER/REPOSITORY format.'
    }

    $portalTarget = Resolve-PortalTarget
    $PortalRepository = $portalTarget.Repository
    $PortalUrl = $portalTarget.Url
    $deleteExistingPortal = [bool]$portalTarget.DeleteExisting
    if (-not $PortalUrl) {
        $PortalUrl = Read-Value 'Public portal URL' $PortalUrl
    }
    $PortalUrl = $PortalUrl.TrimEnd('/') + '/'
    if (-not $SkipPortalDeployment `
            -and $PortalRepository -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') {
        throw 'PortalRepository must use OWNER/REPOSITORY format.'
    }
    $parsedPortalUrl = $null
    if (-not [Uri]::TryCreate($PortalUrl, [UriKind]::Absolute, [ref]$parsedPortalUrl) `
            -or $parsedPortalUrl.Scheme -ne 'https') {
        throw 'PortalUrl must be an absolute HTTPS URL.'
    }

    $createCloudflareSubdomain = $false
    try {
        $subdomainResult = Invoke-Cloudflare GET "/accounts/$CloudflareAccountId/workers/subdomain"
        $CloudflareSubdomain = [string]$subdomainResult.subdomain
        if (-not $CloudflareSubdomain) { $createCloudflareSubdomain = $true }
    }
    catch {
        $statusCode = if ($_.Exception.Response) {
            [int]$_.Exception.Response.StatusCode
        }
        else { 0 }
        if ($statusCode -ne 404) { throw }
        $createCloudflareSubdomain = $true
    }
    if ($createCloudflareSubdomain -and -not $CloudflareSubdomain) {
        $CloudflareSubdomain = "$($GitHubOwner.ToLowerInvariant())-musicmc"
    }
    if (-not $CloudflareSubdomain) {
        throw 'Cloudflare did not return a workers.dev account subdomain.'
    }

    if (-not $SkipWorkerDeployment -and (Test-CloudflareWorker $WorkerName) -and -not $ReplaceExistingWorker) {
        if ($NonInteractive) {
            $WorkerName = Find-FreeWorkerName $WorkerName
        }
        else {
            $workerChoice = (Read-Host `
                "Worker '$WorkerName' exists. Choose REPLACE, NEW, or ABORT" `
            ).Trim().ToUpperInvariant()
            if ($workerChoice -eq 'NEW') {
                $WorkerName = Find-FreeWorkerName $WorkerName
            }
            elseif ($workerChoice -ne 'REPLACE') {
                throw 'Deployment aborted before changing the Worker.'
            }
        }
    }
    $workerUrl = "https://$WorkerName.$CloudflareSubdomain.workers.dev"

    Write-Host ''
    Write-Host 'MusicMC deployment plan' -ForegroundColor Cyan
    Write-Host "Runtime repository: $Repository"
    Write-Host "Portal repository:  $PortalRepository"
    Write-Host "Portal URL:         $PortalUrl"
    Write-Host "Worker URL:         $workerUrl"
    Write-Host "Delete user site:   $deleteExistingPortal"
    Write-Host "Create CF subdomain: $createCloudflareSubdomain"
    Write-Host "Catalog migration:  $MigrateCatalogNow"
    if (-not $NonInteractive) {
        $continue = (Read-Host 'Type DEPLOY to continue').Trim().ToUpperInvariant()
        if ($continue -ne 'DEPLOY') { throw 'Deployment cancelled.' }
    }

    Invoke-Native { gh auth setup-git } 'Could not configure Git authentication for deployment.'
    if ($deleteExistingPortal) {
        Invoke-Native { gh repo delete $PortalRepository --yes } `
            "Could not delete $PortalRepository. Check that the token can delete repositories."
        New-GitHubRepository $PortalRepository 'MusicMC upload portal and song catalog'
    }
    if ($createCloudflareSubdomain) {
        Invoke-Cloudflare PUT "/accounts/$CloudflareAccountId/workers/subdomain" `
            @{ subdomain = $CloudflareSubdomain } | Out-Null
    }

    New-Item -ItemType Directory -Path $temporaryRoot -Force | Out-Null
    $runtimeDirectory = Join-Path $temporaryRoot 'runtime'
    if (-not (Test-GitHubRepository $Repository)) {
        New-GitHubRepository $Repository 'MusicMC automated music catalog and resource packs'
    }
    Invoke-Native { gh repo clone $Repository $runtimeDirectory } "Could not clone $Repository."
    Initialize-RepositoryBranch $runtimeDirectory

    $key = Prepare-SigningKey $runtimeDirectory $temporaryRoot
    Copy-DeploymentSources $runtimeDirectory
    $runtimeConfig = Join-Path $runtimeDirectory 'github-upload-service/config'
    New-Item -ItemType Directory -Path $runtimeConfig -Force | Out-Null
    Copy-Item -LiteralPath $key.Public -Destination `
        (Join-Path $runtimeConfig 'upload-ticket-public.pem') -Force
    $registry = Join-Path $runtimeConfig 'upload-ticket-public-keys.json'
    Invoke-Native { node (Join-Path $Root 'scripts/update-public-key-registry.mjs') `
            $registry $UploadKeyId (Join-Path $runtimeConfig 'upload-ticket-public.pem') } `
        'Could not update the upload public-key registry.'

    $workflowDirectory = Join-Path $runtimeDirectory '.github/workflows'
    New-Item -ItemType Directory -Path $workflowDirectory -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $Root '.github/workflows/process-music-submission.yml') `
        -Destination $workflowDirectory -Force
    Copy-Item -LiteralPath (Join-Path $Root '.github/workflows/deploy-worker.yml') `
        -Destination $workflowDirectory -Force
    Copy-Item -LiteralPath (Join-Path $Root 'github-upload-service/runtime-README.md') `
        -Destination (Join-Path $runtimeDirectory 'README.md') -Force
    Copy-Item -LiteralPath (Join-Path $Root 'github-upload-service/runtime-README.zh-CN.md') `
        -Destination (Join-Path $runtimeDirectory 'README.zh-CN.md') -Force
    if (-not (Test-Path -LiteralPath (Join-Path $runtimeDirectory 'catalog.json'))) {
        Copy-Item -LiteralPath (Join-Path $Root 'catalog.json') -Destination $runtimeDirectory
    }
    $catalogPath = Join-Path $runtimeDirectory 'catalog.json'
    $migrationArguments = @(
        (Join-Path $Root 'github-upload-service/scripts/migrate_catalog.py'),
        '--catalog', $catalogPath
    )
    if (-not $MigrateCatalogNow) { $migrationArguments += '--check-only' }
    Invoke-Native { & $script:Python.Command @($script:Python.Prefix) @migrationArguments } `
        'Catalog validation or migration failed.'

    Commit-And-Push $runtimeDirectory 'Deploy MusicMC runtime services'

    Set-GitHubSecret 'MUSICMC_AUTOMATION_TOKEN' $githubToken $Repository
    Set-GitHubSecret 'CLOUDFLARE_API_TOKEN' $script:CloudflareToken $Repository
    Set-GitHubSecret 'CLOUDFLARE_ACCOUNT_ID' $CloudflareAccountId $Repository
    if ($neteaseCookie) { Set-GitHubSecret 'NETEASE_COOKIE' $neteaseCookie $Repository }
    Set-GitHubVariable 'MUSICMC_WORKER_NAME' $WorkerName $Repository
    Set-GitHubVariable 'MUSICMC_UPLOAD_API_URL' $workerUrl $Repository
    Set-GitHubVariable 'MUSICMC_PAGES_ORIGIN' ([Uri]$PortalUrl).GetLeftPart([UriPartial]::Authority) $Repository
    Set-GitHubVariable 'MUSICMC_PORTAL_URL' $PortalUrl $Repository
    Set-GitHubVariable 'MUSICMC_CATALOG_URL' `
        "https://raw.githubusercontent.com/$Repository/$Branch/catalog.json" $Repository

    foreach ($label in @(
        @{ Name = 'music-pending'; Color = 'd4c5f9'; Description = 'Music upload waiting for processing' },
        @{ Name = 'music-processing'; Color = 'fbca04'; Description = 'Music upload is being processed' },
        @{ Name = 'music-ready'; Color = '0e8a16'; Description = 'Music upload was published' },
        @{ Name = 'music-failed'; Color = 'd93f0b'; Description = 'Automatic music processing failed' }
    )) {
        Invoke-Native { gh label create $label.Name --repo $Repository --color $label.Color `
                --description $label.Description --force } "Could not configure label $($label.Name)."
    }

    if (-not $SkipWorkerDeployment) {
        Push-Location (Join-Path $Root 'github-upload-service/worker')
        try {
            Invoke-Native { npm ci } 'Worker dependencies could not be installed.'
            Invoke-Native { npm test } 'Worker tests failed.'
            Invoke-Native { npx wrangler deploy --name $WorkerName `
                    --var "ALLOWED_ORIGIN:$(([Uri]$PortalUrl).GetLeftPart([UriPartial]::Authority))" `
                    --var "GITHUB_REPOSITORY:$Repository" `
                    --var "GITHUB_BRANCH:$Branch" `
                    --var 'MAX_PENDING_UPLOADS:50' } 'Worker deployment failed.'
            $githubToken | & npx wrangler secret put GITHUB_TOKEN --name $WorkerName
            if ($LASTEXITCODE -ne 0) { throw 'Could not configure the Worker GitHub token.' }
            (Get-Content -Raw -Encoding utf8 $registry) |
                & npx wrangler secret put UPLOAD_PUBLIC_KEYS --name $WorkerName
            if ($LASTEXITCODE -ne 0) { throw 'Could not configure the Worker public-key registry.' }
        }
        finally { Pop-Location }
    }

    if (-not $SkipPortalDeployment) {
        if (-not (Test-GitHubRepository $PortalRepository)) {
            New-GitHubRepository $PortalRepository 'MusicMC upload portal and song catalog'
        }
        $portalDirectory = Join-Path $temporaryRoot 'portal'
        Invoke-Native { gh repo clone $PortalRepository $portalDirectory } `
            "Could not clone $PortalRepository."
        Initialize-RepositoryBranch $portalDirectory
        Copy-Item -Path (Join-Path $Root 'github-upload-service/docs/*') `
            -Destination $portalDirectory -Recurse -Force
        New-Item -ItemType File -Path (Join-Path $portalDirectory '.nojekyll') -Force | Out-Null
        $oldRepository = $env:GITHUB_REPOSITORY
        $oldUploadApi = $env:MUSICMC_UPLOAD_API_URL
        $oldCatalogUrl = $env:MUSICMC_CATALOG_URL
        $oldCatalogBranch = $env:MUSICMC_CATALOG_BRANCH
        try {
            $env:GITHUB_REPOSITORY = $Repository
            $env:MUSICMC_UPLOAD_API_URL = $workerUrl
            $env:MUSICMC_CATALOG_URL = "https://raw.githubusercontent.com/$Repository/$Branch/catalog.json"
            $env:MUSICMC_CATALOG_BRANCH = $Branch
            Invoke-Native { node (Join-Path $Root 'scripts/render-site-config.mjs') `
                    (Join-Path $portalDirectory 'config.js') } 'Could not render portal configuration.'
        }
        finally {
            $env:GITHUB_REPOSITORY = $oldRepository
            $env:MUSICMC_UPLOAD_API_URL = $oldUploadApi
            $env:MUSICMC_CATALOG_URL = $oldCatalogUrl
            $env:MUSICMC_CATALOG_BRANCH = $oldCatalogBranch
        }
        Commit-And-Push $portalDirectory 'Deploy MusicMC portal'
        & gh api --method POST "repos/$PortalRepository/pages" `
            -f "source[branch]=$Branch" -f 'source[path]=/' 2>$null
        if ($LASTEXITCODE -ne 0) {
            & gh api --method PUT "repos/$PortalRepository/pages" `
                -f "source[branch]=$Branch" -f 'source[path]=/' | Out-Null
        }
        if ($LASTEXITCODE -ne 0) {
            Write-Warning 'GitHub Pages could not be enabled automatically.'
        }
    }

    if (-not $SkipPluginBuild) {
        & (Join-Path $Root 'scripts/build-plugin.ps1') -Repository $Repository `
            -PortalUrl $PortalUrl -Branch $Branch -OutputPath $PluginOutputPath -NonInteractive
        if ($LASTEXITCODE -ne 0) { throw 'Plugin build failed.' }
    }

    Write-Host ''
    Write-Host 'MusicMC deployment complete.' -ForegroundColor Green
    Write-Host "Portal:     $PortalUrl"
    Write-Host "Worker:     $workerUrl"
    Write-Host "Repository: https://github.com/$Repository"
    Write-Host "Private key: $($key.Private)"
    Write-Host 'Copy the private key to plugins/MusicMC/upload-ticket-private.pem and set upload.key-id.'
}
finally {
    $githubToken = $null
    $script:CloudflareToken = $null
    $neteaseCookie = $null
    $env:GH_TOKEN = $originalGhToken
    $env:CLOUDFLARE_API_TOKEN = $originalCloudflareToken
    $env:CLOUDFLARE_ACCOUNT_ID = $originalCloudflareAccount
    if (Test-Path -LiteralPath $temporaryRoot) {
        $resolvedTemporary = [IO.Path]::GetFullPath($temporaryRoot)
        $resolvedSystemTemp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
        if ($resolvedTemporary.StartsWith($resolvedSystemTemp, [StringComparison]::OrdinalIgnoreCase) `
                -and (Split-Path -Leaf $resolvedTemporary).StartsWith('musicmc-deploy-')) {
            Remove-Item -LiteralPath $resolvedTemporary -Recurse -Force
        }
    }
}
