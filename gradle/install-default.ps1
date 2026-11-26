param([Parameter(Mandatory = $true)][string]$JarPath)

$ErrorActionPreference = 'Stop'
$game = Join-Path $env:LOCALAPPDATA 'Programs\crit launcher\launcher_data\instances\default\game'
$mods = Join-Path $game 'mods'
if (!(Test-Path -LiteralPath $mods -PathType Container)) {
    throw "Crit Launcher's default mods folder was not found: $mods"
}

$name = Split-Path -Path $JarPath -Leaf
$target = Join-Path $mods $name
$hash = (Get-FileHash -LiteralPath $JarPath).Hash
$otherVersions = Get-ChildItem -LiteralPath $mods -Filter 'crit-client-*.jar' -File | Where-Object Name -NE $name
if ($otherVersions) {
    throw "Another Crit Client JAR is already in mods: $($otherVersions.Name -join ', '). Move it out before installing."
}
if ((Test-Path -LiteralPath $target -PathType Leaf) -and (Get-FileHash -LiteralPath $target).Hash -eq $hash) {
    Write-Output "Already installed: $target"
    exit 0
}

function Assert-MinecraftClosed {
    $client = Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" | Where-Object {
        $_.CommandLine -match 'KnotClient|net\.minecraft\.client\.main\.Main' -or
        $_.CommandLine -like "*$game*"
    }
    if ($client) {
        throw 'Close Minecraft before installing Crit Client, then run installDefault again.'
    }
}

Assert-MinecraftClosed
$backups = New-Item -ItemType Directory -Path (Join-Path $game 'critclient-backups') -Force
$stamp = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$staged = Join-Path $backups.FullName "install-$stamp.tmp"
try {
    Copy-Item -LiteralPath $JarPath -Destination $staged
    if ((Get-FileHash -LiteralPath $staged).Hash -ne $hash) { throw 'The staged JAR did not match the build.' }
    Assert-MinecraftClosed
    if (Test-Path -LiteralPath $target -PathType Leaf) {
        $backup = Join-Path $backups.FullName "$stamp-$name"
        [IO.File]::Replace($staged, $target, $backup)
        Write-Output "Previous JAR backed up to $backup"
    } else {
        [IO.File]::Move($staged, $target)
    }
    Write-Output "Installed: $target"
} finally {
    if (Test-Path -LiteralPath $staged) { Remove-Item -LiteralPath $staged }
}
