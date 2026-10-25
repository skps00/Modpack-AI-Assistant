$ErrorActionPreference = "Stop"
$inst = "C:\Users\skps9\Documents\PrismLauncher-Windows-MinGW-w64-Portable-11.1.0\instances\packai_sandbox_All the Mods 8 - ATM8"
$mods = Join-Path $inst "minecraft\mods"
$src  = "C:\Users\skps9\packai-0.2.3.new.jar"
$dst  = Join-Path $mods "packai-0.2.3.jar"
$bak  = Join-Path $inst "jar_backups"
New-Item -ItemType Directory -Force -Path $bak | Out-Null

# --- guard: never overwrite the jar while this instance is running ---
# A jar swapped under a live JVM keeps the old file handle; classes loaded later
# then fail with ClassNotFoundException even though the class exists on disk.
$instName = Split-Path -Leaf $inst
$running = @()
try {
    $running = Get-CimInstance Win32_Process -Filter "Name='javaw.exe'" |
        Where-Object { $_.CommandLine -and ($_.CommandLine -like ("*" + $instName + "*")) }
} catch {
    $running = @()
}
if ($running.Count -gt 0) {
    Copy-Item -LiteralPath $src -Destination ($dst + ".pending") -Force
    Write-Output ("REFUSED: instance is running (pid=" + (($running | ForEach-Object { $_.ProcessId }) -join ",") + ")")
    Write-Output "staged as .pending - close Minecraft, then re-run or rename manually"
    exit 2
}

if (Test-Path -LiteralPath $dst) {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    Copy-Item -LiteralPath $dst -Destination (Join-Path $bak ("packai-0.2.3.$stamp.jar")) -Force
    Write-Output "backed up -> jar_backups\packai-0.2.3.$stamp.jar"
}
try {
    Copy-Item -LiteralPath $src -Destination $dst -Force
    $a = (Get-FileHash -LiteralPath $src -Algorithm SHA256).Hash
    $b = (Get-FileHash -LiteralPath $dst -Algorithm SHA256).Hash
    if ($a -ne $b) { Write-Output "SHA MISMATCH"; exit 1 }
    Write-Output ("deployed ok sha=" + $b.Substring(0,16))
    Get-ChildItem -LiteralPath $mods -Filter "packai*.jar" | ForEach-Object { Write-Output ("  mods: " + $_.Name + " " + [math]::Round($_.Length/1KB) + "KB") }
} catch {
    Write-Output ("LOCKED: " + $_.Exception.Message)
    Copy-Item -LiteralPath $src -Destination ($dst + ".pending") -Force
    Write-Output "staged as .pending (jar locked, rename after closing the game)"
}
