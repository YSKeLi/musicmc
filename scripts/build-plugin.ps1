[CmdletBinding()]
param(
    [string]$Repository = "",
    [string]$PortalUrl = "",
    [string]$Branch = "main",
    [string]$OutputPath = "output/MusicMC-server.jar",
    [switch]$SkipTests,
    [switch]$NonInteractive
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

function Read-Required([string]$Prompt, [string]$Value) {
    if ($Value) {
        return $Value.Trim()
    }
    if ($NonInteractive) {
        throw "$Prompt is required in non-interactive mode."
    }
    $answer = (Read-Host $Prompt).Trim()
    if (-not $answer) {
        throw "$Prompt is required."
    }
    return $answer
}

$Repository = Read-Required "GitHub music repository (OWNER/REPOSITORY)" $Repository
$PortalUrl = Read-Required "Public upload portal URL" $PortalUrl
$Branch = Read-Required "Catalog branch" $Branch

if ($Repository -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') {
    throw "Repository must use OWNER/REPOSITORY format."
}
if ($Branch -notmatch '^[A-Za-z0-9._/-]{1,200}$' -or $Branch.Contains('..')) {
    throw "Branch contains unsupported characters."
}
$portalUri = $null
if (-not [Uri]::TryCreate($PortalUrl, [UriKind]::Absolute, [ref]$portalUri) `
        -or $portalUri.Scheme -ne 'https') {
    throw "PortalUrl must be an absolute HTTPS URL."
}
$PortalUrl = $PortalUrl.TrimEnd('/') + '/'

$maven = if ($IsWindows -or $env:OS -eq 'Windows_NT') {
    Join-Path $Root 'mvnw.cmd'
}
else {
    Join-Path $Root 'mvnw'
}
if (-not (Test-Path -LiteralPath $maven)) {
    throw "Maven wrapper is missing: $maven"
}

$goal = if ($SkipTests) { 'package' } else { 'verify' }
$mavenArguments = @(
    '--batch-mode',
    'clean',
    $goal,
    "-Dmusicmc.github.repository=$Repository",
    "-Dmusicmc.github.branch=$Branch",
    "-Dmusicmc.portal.url=$PortalUrl"
)
if ($SkipTests) {
    $mavenArguments += '-DskipTests'
}

& $maven @mavenArguments
if ($LASTEXITCODE -ne 0) {
    throw "MusicMC plugin build failed."
}

$artifact = Get-ChildItem -LiteralPath (Join-Path $Root 'target') -Filter 'MusicMC-*.jar' -File |
    Where-Object { $_.Name -notlike 'original-*' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $artifact) {
    throw "The build completed without a MusicMC JAR."
}

$resolvedOutput = if ([IO.Path]::IsPathRooted($OutputPath)) {
    [IO.Path]::GetFullPath($OutputPath)
}
else {
    [IO.Path]::GetFullPath((Join-Path $Root $OutputPath))
}
$outputDirectory = Split-Path -Parent $resolvedOutput
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
Copy-Item -LiteralPath $artifact.FullName -Destination $resolvedOutput -Force

$hash = (Get-FileHash -LiteralPath $resolvedOutput -Algorithm SHA256).Hash
Write-Host ""
Write-Host "MusicMC server plugin built."
Write-Host "Repository: $Repository"
Write-Host "Portal:     $PortalUrl"
Write-Host "Output:     $resolvedOutput"
Write-Host "SHA-256:    $hash"

[pscustomobject]@{
    Repository = $Repository
    PortalUrl = $PortalUrl
    Branch = $Branch
    OutputPath = $resolvedOutput
    Sha256 = $hash
}
