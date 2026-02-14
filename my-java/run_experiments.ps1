# run_experiments.ps1
# Run controlled experiments for HUIM:
# - Utility-List (UL) miner: com.tp3.Main
# - Choco declarative: com.tp3.choco.huim.ChocoHuimMain
# All runs have a fair time limit (30s by default) and are logged.

param(
  [int]$TimeLimitSec = 30,
  [int]$UlPrintLimit = 0,     # UL: how many HUIs to print
  [int]$ChocoPrintLimit = 5   # Choco: how many solutions to print
)

$ErrorActionPreference = "Stop"

function Run-One {
  param(
    [string]$Tag,       # ul / choco
    [string]$DatasetName,
    [string]$FilePath,
    [int]$MinUtil,
    [string]$MainClass,
    [int]$PrintLimit
  )

  if (!(Test-Path $FilePath)) {
    Write-Host "SKIP (missing): $FilePath"
    return
  }

  $timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
  $safeFile = ($DatasetName -replace '[^a-zA-Z0-9_\-]', '_')
  $outFile = "logs\$Tag\${safeFile}_min${MinUtil}_t${TimeLimitSec}s_${timestamp}.log"

  Write-Host "RUN [$Tag] dataset=$DatasetName minUtil=$MinUtil t=${TimeLimitSec}s -> $outFile"

  $argsString = "$FilePath $MinUtil $PrintLimit $TimeLimitSec"

  # Run and log everything (stdout+stderr)
  & mvn -q exec:java "-Dexec.mainClass=$MainClass" "-Dexec.args=$argsString" 2>&1 |
    Tee-Object -FilePath $outFile

  Write-Host "DONE -> $outFile"
  Write-Host ""
}

# --- Datasets you actually have in ../data (based on your ls) ---
$datasets = @(
  @{ name="mushroom";  path="..\data\mushroom_utility_SPMF.txt";   mins=@(500, 2000, 5000, 10000, 20000, 40000) },
  @{ name="retail";    path="..\data\retail_utility_spmf.txt";     mins=@(5000, 10000, 20000, 40000, 80000) },
  @{ name="foodmart";  path="..\data\foodmart.txt";               mins=@(500, 2000, 5000, 10000) },   # will auto-skip if format/wrong/missing
  @{ name="chainstore";path="..\data\chainstore_utility.txt";      mins=@(5000, 10000, 20000, 40000, 80000) }
)

# Main classes
$UL_MAIN    = "com.tp3.Main"
$CHOCO_MAIN = "com.tp3.choco.huim.ChocoHuimMain"

Write-Host "=== Compile (safe) ==="
& mvn -q clean compile | Out-Null

Write-Host "=== START EXPERIMENTS (t=$TimeLimitSec sec) ==="
foreach ($d in $datasets) {
  foreach ($m in $d.mins) {
    Run-One -Tag "ul"    -DatasetName $d.name -FilePath $d.path -MinUtil $m -MainClass $UL_MAIN    -PrintLimit $UlPrintLimit
    Run-One -Tag "choco" -DatasetName $d.name -FilePath $d.path -MinUtil $m -MainClass $CHOCO_MAIN -PrintLimit $ChocoPrintLimit
  }
}

Write-Host "=== ALL DONE ==="
Write-Host "Logs are in: .\logs\ul and .\logs\choco"