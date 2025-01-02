# Setup script for PowerShell on Windows
# This will run export.ps1 in the ESP-IDF SDK,
# which will make tools like idf.py work in this environment.
#
# .\setup_env.ps1


# Check if ESP-IDF environment is already set up
if ($env:IDF_PATH -and (Test-Path $env:IDF_PATH)) {
  Write-Host "ESP-IDF environment already set up."
  return
}

# Set ESP-IDF path (adjust if necessary)
$espIdfPath = "C:\Users\ccart\esp\v5.3.2\esp-idf"

# Check if ESP-IDF path is valid
if (-not (Test-Path $espIdfPath)) {
  Write-Error "Invalid ESP-IDF path: $espIdfPath"
  return
}

# Call export.ps1 to set up the environment
. "$espIdfPath\export.ps1"

Write-Host "ESP-IDF environment set up for project: $($MyInvocation.MyCommand.Path)"

