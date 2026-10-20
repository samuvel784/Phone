# Generates a ready-to-publish GitHub Pages site for the Nova Dialer privacy policy.
#
# This script never pushes, publishes, or changes repository settings. It only reads PRIVACY.md
# and writes a small Jekyll site under build/privacy-pages/ (already git-ignored).
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\prepare_privacy_pages.ps1

$ErrorActionPreference = 'Stop'

$root = $PSScriptRoot
$source = Join-Path $root 'PRIVACY.md'
$output = Join-Path $root 'build\privacy-pages'

if (-not (Test-Path $source)) {
    throw "PRIVACY.md not found at $source"
}

New-Item -ItemType Directory -Force -Path $output | Out-Null

# Read and write UTF-8 without a BOM. Jekyll treats a BOM as page content and would not parse
# the YAML front matter.
$policy = [IO.File]::ReadAllText($source, [Text.Encoding]::UTF8)
$utf8 = New-Object System.Text.UTF8Encoding($false)

# GitHub Pages renders Markdown, but Jekyll requires YAML front matter at the very start.
$frontMatter = @"
---
title: Nova Dialer Privacy Policy
description: Privacy policy for the Nova Dialer Android application.
---

"@
[IO.File]::WriteAllText((Join-Path $output 'index.md'), $frontMatter + $policy, $utf8)

# One of the themes GitHub Pages supports natively; no extra build step or dependency.
$config = @"
title: Nova Dialer Privacy Policy
description: Privacy policy for the Nova Dialer Android application.
theme: jekyll-theme-minimal
"@
[IO.File]::WriteAllText((Join-Path $output '_config.yml'), $config, $utf8)

Write-Host "Prepared GitHub Pages site in: $output"
Get-ChildItem $output | ForEach-Object { Write-Host "  $($_.Name) ($($_.Length) bytes)" }
Write-Host ""
Write-Host "Nothing was pushed or published. See docs/PRIVACY_HOSTING.md for the exact steps."
