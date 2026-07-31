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

$deployScript = Join-Path $PSScriptRoot 'deploy.ps1'
Write-Warning 'bootstrap.ps1 is a compatibility alias. New automation lives in scripts/deploy.ps1.'

$arguments = @{
    Mode = $Mode
    GitHubOwner = $GitHubOwner
    Repository = $Repository
    PortalRepository = $PortalRepository
    PortalUrl = $PortalUrl
    Branch = $Branch
    WorkerName = $WorkerName
    CloudflareAccountId = $CloudflareAccountId
    CloudflareSubdomain = $CloudflareSubdomain
    PrivateKeyPath = $PrivateKeyPath
    UploadKeyId = $UploadKeyId
    PluginOutputPath = $PluginOutputPath
    MigrateCatalogNow = $MigrateCatalogNow
    ReplaceExistingUserSite = $ReplaceExistingUserSite
    AllowRepositoryDeletion = $AllowRepositoryDeletion
    ReplaceExistingWorker = $ReplaceExistingWorker
    SkipPortalDeployment = $SkipPortalDeployment
    SkipWorkerDeployment = $SkipWorkerDeployment
    SkipPluginBuild = $SkipPluginBuild
    PlanOnly = $PlanOnly
    NonInteractive = $NonInteractive
}
& $deployScript @arguments
