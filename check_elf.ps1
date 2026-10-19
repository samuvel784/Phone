Add-Type -AssemblyName System.IO.Compression.FileSystem
$apkPath = "app\build\outputs\apk\debug\app-debug.apk"
$tempDir = "app\build\extracted_so"
if (Test-Path $tempDir) { Remove-Item -Recurse -Force $tempDir }
[System.IO.Compression.ZipFile]::ExtractToDirectory($apkPath, $tempDir)

$soFiles = Get-ChildItem -Path $tempDir -Filter "*.so" -Recurse
foreach ($file in $soFiles) {
    $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
    if ($bytes.Length -gt 4 -and $bytes[0] -eq 0x7f -and $bytes[1] -eq 0x45 -and $bytes[2] -eq 0x4c -and $bytes[3] -eq 0x46) {
        $is64 = $bytes[4] -eq 2
        $phoff = if ($is64) { [BitConverter]::ToInt64($bytes, 32) } else { [BitConverter]::ToInt32($bytes, 28) }
        $phentsize = if ($is64) { [BitConverter]::ToUInt16($bytes, 54) } else { [BitConverter]::ToUInt16($bytes, 42) }
        $phnum = if ($is64) { [BitConverter]::ToUInt16($bytes, 56) } else { [BitConverter]::ToUInt16($bytes, 44) }

        Write-Output "----------------------------------------"
        Write-Output "Library: $($file.Name) | ABI: $(Split-Path (Split-Path $file.FullName -Parent) -Leaf) | Format: $(if($is64){'64-bit'}else{'32-bit'})"

        for ($i = 0; $i -lt $phnum; $i++) {
            $offset = $phoff + ($i * $phentsize)
            $p_type = [BitConverter]::ToUInt32($bytes, $offset)
            if ($p_type -eq 1) { # PT_LOAD
                $alignOffset = if ($is64) { $offset + 48 } else { $offset + 28 }
                $p_align = if ($is64) { [BitConverter]::ToInt64($bytes, $alignOffset) } else { [BitConverter]::ToInt32($bytes, $alignOffset) }
                $vaddrOffset = if ($is64) { $offset + 16 } else { $offset + 8 }
                $p_vaddr = if ($is64) { [BitConverter]::ToInt64($bytes, $vaddrOffset) } else { [BitConverter]::ToInt32($bytes, $vaddrOffset) }
                $hexAlign = '0x{0:X}' -f $p_align
                $compat16k = if ($p_align -ge 16384) { 'YES (>= 16KB)' } else { 'NO (< 16KB)' }
                Write-Output "  LOAD Segment: vaddr=0x{0:X}, align={1} ({2}) -> 16KB Compatible: {3}" -f $p_vaddr, $p_align, $hexAlign, $compat16k
            }
        }
    }
}
