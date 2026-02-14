param(
  [int]$TimeLimit = 30,
  [string]$OutRoot = "results",
  [int]$MinUtilMushroom = 500,
  [int]$MinUtilRetail   = 5000,
  [int]$MinUtilFoodmart = 500,
  [int]$MinUtilChain    = 5000
)

$ErrorActionPreference = "Stop"

# ---- Paths (project root = where you launch the script) ----
$ProjRoot = (Get-Location).Path
$MyJava   = Join-Path $ProjRoot "my-java"
$DataDir  = Join-Path $ProjRoot "data"
$SpmfJar  = Join-Path $ProjRoot "tools\spmf.jar"   # <-- make sure this exists

# Timestamped output folder
$stamp  = Get-Date -Format "yyyyMMdd_HHmmss"
$outDir = Join-Path $ProjRoot (Join-Path $OutRoot "part2_$stamp")
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Run-CmdToLog {
  param(
    [string]$Title,
    [string]$WorkDir,
    [string]$Exe,
    [string[]]$Args,
    [string]$LogPath
  )

  Write-Host ""
  Write-Host ">>> $Title"
  Write-Host "    WD : $WorkDir"
  Write-Host "    CMD: $Exe $($Args -join ' ')"
  Write-Host "    LOG: $LogPath"

  New-Item -ItemType Directory -Force -Path (Split-Path $LogPath) | Out-Null

  # Run synchronously, capture stdout+stderr into log
  $pinfo = New-Object System.Diagnostics.ProcessStartInfo
  $pinfo.FileName = $Exe
  $pinfo.WorkingDirectory = $WorkDir
  $pinfo.RedirectStandardOutput = $true
  $pinfo.RedirectStandardError  = $true
  $pinfo.UseShellExecute = $false

  foreach ($a in $Args) { [void]$pinfo.ArgumentList.Add($a) }

  $p = New-Object System.Diagnostics.Process
  $p.StartInfo = $pinfo
  [void]$p.Start()

  $stdout = $p.StandardOutput.ReadToEnd()
  $stderr = $p.StandardError.ReadToEnd()
  $p.WaitForExit()

  ($stdout + "`n" + $stderr) | Set-Content -Encoding UTF8 $LogPath

  if ($p.ExitCode -ne 0) {
    Write-Host "!!! Non-zero exit code: $($p.ExitCode). See: $LogPath"
  }
}

function Run-UL {
  param([string]$tag, [string]$file, [int]$min)
  $log = Join-Path $outDir ("ul_{0}.log" -f $tag)

  # IMPORTANT: pass -Dexec.args as a SINGLE token (quotes)
  $argsString = "..\data\$file $min 0 $TimeLimit"
  Run-CmdToLog "UL-Miner | $tag" $MyJava "mvn" @("-q","exec:java","-Dexec.mainClass=com.tp3.Main","-Dexec.args=$argsString") $log
}

function Run-CHOCO {
  param([string]$tag, [string]$file, [int]$min)
  $log = Join-Path $outDir ("choco_{0}.log" -f $tag)

  $argsString = "..\data\$file $min 0 $TimeLimit"
  Run-CmdToLog "Choco-HUIM | $tag" $MyJava "mvn" @("-q","exec:java","-Dexec.mainClass=com.tp3.choco.huim.ChocoHuimMain","-Dexec.args=$argsString") $log
}

function Run-SPMF-EFIM {
  param([string]$tag, [string]$file, [int]$min)

  if (!(Test-Path $SpmfJar)) {
    throw "SPMF jar not found at: $SpmfJar (put spmf.jar in .\tools\spmf.jar)"
  }

  $log = Join-Path $outDir ("spmf_EFIM_{0}.log" -f $tag)

  $inPath = Join-Path $DataDir $file

  # CRITICAL: write patterns to NUL to avoid 25GB outputs
  $outPath = "NUL"

  Run-CmdToLog "SPMF EFIM | $tag" $ProjRoot "java" @(
    "-jar", $SpmfJar,
    "run", "EFIM",
    $inPath,
    $outPath,
    "$min"
  ) $log
}

Write-Host ">>> Building project (mvn clean compile)..."
Run-CmdToLog "Build" $MyJava "mvn" @("clean","compile") (Join-Path $outDir "build.log")
Write-Host ">>> Build finished. Logs: $outDir"

# ---- Datasets (you asked: mushroom, retail, foodmart small, chainstore if time) ----
# Choose the right file names from your /data folder:
# mushroom_utility_SPMF.txt
# retail_utility_spmf.txt
# foodmart.txt  (if this is NOT utility format, don’t run EFIM on it)
# chainstore_utility.txt

# 1) mushroom
Run-UL        "mushroom_min${TimeLimit}s" "mushroom_utility_SPMF.txt" $MinUtilMushroom
Run-CHOCO     "mushroom_min${TimeLimit}s" "mushroom_utility_SPMF.txt" $MinUtilMushroom
Run-SPMF-EFIM "mushroom_min${TimeLimit}s" "mushroom_utility_SPMF.txt" $MinUtilMushroom

# 2) retail
Run-UL        "retail_min${TimeLimit}s" "retail_utility_spmf.txt" $MinUtilRetail
Run-CHOCO     "retail_min${TimeLimit}s" "retail_utility_spmf.txt" $MinUtilRetail
Run-SPMF-EFIM "retail_min${TimeLimit}s" "retail_utility_spmf.txt" $MinUtilRetail

# 3) chainstore (utility)
Run-UL        "chainstore_min${TimeLimit}s" "chainstore_utility.txt" $MinUtilChain
Run-CHOCO     "chainstore_min${TimeLimit}s" "chainstore_utility.txt" $MinUtilChain
Run-SPMF-EFIM "chainstore_min${TimeLimit}s" "chainstore_utility.txt" $MinUtilChain

# 4) foodmart:
# WARNING: your foodmart.txt looks like a non-utility file (likely FIM). EFIM needs utility format.
# If you have a utility version later, swap file name here.
# Otherwise comment these lines out.
# Run-UL        "foodmart_min${TimeLimit}s" "foodmart.txt" $MinUtilFoodmart
# Run-CHOCO     "foodmart_min${TimeLimit}s" "foodmart.txt" $MinUtilFoodmart
# Run-SPMF-EFIM "foodmart_min${TimeLimit}s" "foodmart.txt" $MinUtilFoodmart

Write-Host ""
Write-Host "DONE. Logs are in: $outDir"