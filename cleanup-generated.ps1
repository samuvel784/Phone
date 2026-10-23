#Requires -Version 5.1
<#
.SYNOPSIS
    Remove only confirmed, reproducible Android build output from this project.

.DESCRIPTION
    This script uses an explicit allowlist. It never walks the project looking
    for directories by name, and it never removes source, documentation,
    .git, Gradle wrapper files, app/release, app/schemas, signing material,
    local project configuration, user data or release archives.

    The allowlisted entries are project-relative:
        app\build, build, .gradle, .kotlin, .cxx, .externalNativeBuild,
        app\.cxx, app\.externalNativeBuild
    .artifacts is opt-in because some projects keep downloaded JDKs or
    diagnostic logs there.

    If a release APK/AAB/mapping file is still present in app\build, the script
    refuses to delete app\build unless -ForceReleaseArtifactDeletion is used.
    Archive or otherwise verify the release copy first.

    Generated files are not source. Gradle/Android tooling will recreate them
    when the toolchain is installed again. Global caches such as ~/.gradle,
    the Android SDK, AVDs and Android Studio are deliberately outside this
    script's scope.

.PARAMETER DryRun
    Show what would be removed and the measured sizes, but delete nothing.

.PARAMETER IncludeArtifacts
    Also allow the project-local .artifacts directory. Use only after checking
    that it contains no release artifact, credential or user data.

.PARAMETER ForceReleaseArtifactDeletion
    Allow app\build to be deleted even when it still contains release APK/AAB
    or mapping output. Only use after the release files are archived/verified.

.EXAMPLE
    .\cleanup-generated.ps1 -DryRun

.EXAMPLE
    .\cleanup-generated.ps1 -DryRun -IncludeArtifacts

.EXAMPLE
    .\cleanup-generated.ps1
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [switch]$DryRun,
    [switch]$IncludeArtifacts,
    [switch]$ForceReleaseArtifactDeletion
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ProjectRoot = (Resolve-Path -LiteralPath $PSScriptRoot).Path.TrimEnd('\')
$AllowedRelative = @(
    'app\build',
    'build',
    '.gradle',
    '.kotlin',
    '.cxx',
    '.externalNativeBuild',
    'app\.cxx',
    'app\.externalNativeBuild'
)
if ($IncludeArtifacts) {
    $AllowedRelative += '.artifacts'
}

function Get-DirectoryMeasure {
    param([Parameter(Mandatory = $true)][string]$Path)

    $files = @(Get-ChildItem -LiteralPath $Path -Recurse -File -Force -ErrorAction SilentlyContinue)
    $sum = ($files | Measure-Object -Property Length -Sum).Sum
    if ($null -eq $sum) { $sum = 0 }
    [pscustomobject]@{
        Bytes = [int64]$sum
        Files = $files.Count
    }
}

function Remove-DirectoryWithFallback {
    param([Parameter(Mandatory = $true)][string]$Path)

    try {
        Remove-Item -LiteralPath $Path -Recurse -Force -ErrorAction Stop
    }
    catch {
        $empty = Join-Path $env:TEMP ('cleanup-empty-' + [Guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $empty -Force | Out-Null
        try {
            robocopy $empty $Path /MIR /R:1 /W:1 /NFL /NDL /NJH /NJS /NP | Out-Null
            if ($LASTEXITCODE -ge 8) {
                throw "robocopy failed with exit code $LASTEXITCODE for $Path"
            }
            Remove-Item -LiteralPath $Path -Recurse -Force -ErrorAction SilentlyContinue
        }
        finally {
            Remove-Item -LiteralPath $empty -Recurse -Force -ErrorAction SilentlyContinue
        }
    }

    if (Test-Path -LiteralPath $Path) {
        throw "Could not remove: $Path"
    }
}

$releaseArtifacts = @()
foreach ($relativePattern in @(
    'app\build\outputs\apk\release\*.apk',
    'app\build\outputs\bundle\release\*.aab',
    'app\build\outputs\mapping\release\mapping.txt'
)) {
    $patternPath = Join-Path $ProjectRoot $relativePattern
    $releaseArtifacts += @(Get-ChildItem -Path $patternPath -File -Force -ErrorAction SilentlyContinue)
}

$skipAppBuild = $false
if ($releaseArtifacts.Count -gt 0 -and -not $ForceReleaseArtifactDeletion) {
    $skipAppBuild = $true
    Write-Warning "Release output is still inside app\build. app\build will be skipped."
    foreach ($artifact in $releaseArtifacts) {
        Write-Warning "  $($artifact.FullName)"
    }
    Write-Warning "Archive/verify the release first, then use -ForceReleaseArtifactDeletion if deletion is intended."
}

Write-Host "Project root: $ProjectRoot"
Write-Host "Mode: $(if ($DryRun) { 'DRY RUN (nothing will be deleted)' } else { 'DELETE' })"
Write-Host ""

$targets = @()
foreach ($relative in $AllowedRelative) {
    $candidate = [System.IO.Path]::GetFullPath((Join-Path $ProjectRoot $relative))
    $rootPrefix = $ProjectRoot + '\'
    if (-not $candidate.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing path outside project root: $candidate"
    }
    if ($candidate.TrimEnd('\') -eq $ProjectRoot) {
        throw "Refusing project root itself: $candidate"
    }
    if (-not (Test-Path -LiteralPath $candidate -PathType Container)) {
        continue
    }
    if ($skipAppBuild -and $relative -eq 'app\build') {
        continue
    }

    $measure = Get-DirectoryMeasure -Path $candidate
    $targets += [pscustomobject]@{
        Relative = $relative
        FullName = $candidate
        Bytes    = $measure.Bytes
        Files    = $measure.Files
    }
}

if ($targets.Count -eq 0) {
    Write-Host "No allowlisted generated directories were found."
    return
}

$targets | Select-Object Relative, Files,
    @{Name = 'SizeMB'; Expression = { [math]::Round($_.Bytes / 1MB, 2) } },
    @{Name = 'SizeGB'; Expression = { [math]::Round($_.Bytes / 1GB, 3) } } |
    Format-Table -AutoSize

$totalBytes = [int64](($targets | Measure-Object -Property Bytes -Sum).Sum)
Write-Host ("Total selected: {0:N2} MB ({1:N3} GB) across {2} directories" -f ($totalBytes / 1MB), ($totalBytes / 1GB), $targets.Count)
Write-Host ""

if ($DryRun) {
    Write-Host "Dry run complete. No files were deleted."
    return
}

foreach ($target in $targets) {
    if ($PSCmdlet.ShouldProcess($target.FullName, 'Remove generated directory')) {
        Remove-DirectoryWithFallback -Path $target.FullName
        Write-Host "Removed: $($target.Relative) ($([math]::Round($target.Bytes / 1MB, 2)) MB)"
    }
}

Write-Host ""
Write-Host "Cleanup complete. Preserved source, .git, Gradle wrapper, app\release, app\schemas, signing files and local configuration."
