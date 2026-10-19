<#
.SYNOPSIS
    Registers Remote Laptop Biometric Unlock as a Windows Boot Task.

.DESCRIPTION
    Creates an elevated Scheduled Task that starts agent.py silently with pythonw.exe
    at system boot (before user login) and on battery/AC power.
    Requires Administrator privileges.
#>

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Host "[*] Requesting Administrator privileges to register boot task..." -ForegroundColor Yellow
    try {
        Start-Process powershell.exe -ArgumentList ("-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`"") -Verb RunAs
        exit
    } catch {
        Write-Error "Failed to elevate process. Please right-click PowerShell, select 'Run as Administrator', and re-run this script."
        exit 1
    }
}

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " Remote Laptop Unlock - Register Boot Task (Task Scheduler)" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

$taskName = "RemoteLaptopUnlockBoot"
$scriptDir = Split-Path -Parent $PSCommandPath
$agentPy = Join-Path $scriptDir "agent.py"

# Locate pythonw.exe
$pythonw = (Get-Command pythonw.exe -ErrorAction SilentlyContinue).Source
if (-not $pythonw) {
    $pythonw = "C:\Python315\pythonw.exe"
}

if (-not (Test-Path $pythonw)) {
    Write-Error "Could not find pythonw.exe at: $pythonw"
    exit 1
}

Write-Host "Python Executable : $pythonw" -ForegroundColor White
Write-Host "Agent Script Path : $agentPy" -ForegroundColor White
Write-Host "Working Directory : $scriptDir" -ForegroundColor White
echo ""

# Terminate any running agent.py processes before upgrading
Write-Host "[1/3] Stopping old agent processes and task instance..." -ForegroundColor Gray
Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like "*agent.py*" } | ForEach-Object {
    Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    Write-Host " Terminated process ID: $($_.ProcessId)" -ForegroundColor Yellow
}
schtasks.exe /Delete /TN $taskName /F 2>$null

# Create Task XML definition to ensure battery execution and highest privileges
$taskXml = @"
<?xml version="1.0" encoding="UTF-16"?>
<Task version="1.2" xmlns="http://schemas.microsoft.com/windows/2004/02/mit/task">
  <RegistrationInfo>
    <Description>Remote Laptop Biometric Unlock background agent at system boot.</Description>
  </RegistrationInfo>
  <Triggers>
    <BootTrigger>
      <Enabled>true</Enabled>
    </BootTrigger>
    <LogonTrigger>
      <Enabled>true</Enabled>
    </LogonTrigger>
  </Triggers>
  <Principals>
    <Principal id="Author">
      <UserId>S-1-5-18</UserId>
      <RunLevel>HighestAvailable</RunLevel>
    </Principal>
  </Principals>
  <Settings>
    <MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>
    <DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries>
    <StopIfGoingOnBatteries>false</StopIfGoingOnBatteries>
    <AllowHardTerminate>true</AllowHardTerminate>
    <StartWhenAvailable>true</StartWhenAvailable>
    <RunOnlyIfNetworkAvailable>false</RunOnlyIfNetworkAvailable>
    <IdleSettings>
      <StopOnIdleEnd>false</StopOnIdleEnd>
      <RestartOnIdle>false</RestartOnIdle>
    </IdleSettings>
    <AllowStartOnDemand>true</AllowStartOnDemand>
    <Enabled>true</Enabled>
    <Hidden>false</Hidden>
    <RunOnlyIfIdle>false</RunOnlyIfIdle>
    <WakeToRun>false</WakeToRun>
    <ExecutionTimeLimit>PT0S</ExecutionTimeLimit>
    <Priority>7</Priority>
    <RestartOnFailure>
      <Interval>PT1M</Interval>
      <Count>3</Count>
    </RestartOnFailure>
  </Settings>
  <Actions Context="Author">
    <Exec>
      <Command>$pythonw</Command>
      <Arguments>`"$agentPy`"</Arguments>
      <WorkingDirectory>$scriptDir</WorkingDirectory>
    </Exec>
  </Actions>
</Task>
"@

$tempXml = Join-Path $env:TEMP "RemoteLaptopUnlockTask.xml"
[System.IO.File]::WriteAllText($tempXml, $taskXml, [System.Text.Encoding]::Unicode)

Write-Host "[2/3] Registering task '$taskName' with Task Scheduler..." -ForegroundColor Green
$regResult = schtasks.exe /Create /XML $tempXml /TN $taskName /F
Remove-Item $tempXml -ErrorAction SilentlyContinue

if ($LASTEXITCODE -eq 0) {
    Write-Host "[3/3] Starting task immediately..." -ForegroundColor Green
    schtasks.exe /Run /TN $taskName
    Start-Sleep -Seconds 2

    # Verify agent is responding
    try {
        $resp = Invoke-RestMethod -Uri "http://127.0.0.1:5000/ping" -TimeoutSec 3
        Write-Host " [OK] Agent is active and responding!" -ForegroundColor Green
        Write-Host " Status  : $($resp.status)" -ForegroundColor White
        Write-Host " Author  : $($resp.author)" -ForegroundColor White
        Write-Host " License : $($resp.license)" -ForegroundColor White
    } catch {
        Write-Host " [INFO] Agent started in background." -ForegroundColor Yellow
    }

    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host " [SUCCESS] Boot Task Registered Successfully!" -ForegroundColor Green
    Write-Host " Made by specxy.net | License: specxy.net" -ForegroundColor Green
    Write-Host " The agent will now automatically run when the laptop boots," -ForegroundColor White
    Write-Host " even before you log in, and on battery or AC power." -ForegroundColor White
    Write-Host "============================================================" -ForegroundColor Cyan
} else {
    Write-Host "[ERROR] Failed to register scheduled task." -ForegroundColor Red
}

Write-Host "Press any key to exit..."
[void][System.Console]::ReadKey()
