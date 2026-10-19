<#
.SYNOPSIS
    Configures Windows Defender Firewall inbound rules for Remote Laptop Biometric Unlock.

.DESCRIPTION
    Opens TCP Port 5000 (Flask HTTP Server) and UDP Port 5005 (Discovery Beacon)
    for local network communication with the companion Android app.
    Requires Administrator privileges.
#>

# ---------------------------------------------------------------------------
# Check & Elevate Administrator Privileges
# ---------------------------------------------------------------------------
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Host "[*] Requesting Administrator privileges to configure Windows Firewall..." -ForegroundColor Yellow
    try {
        Start-Process powershell.exe -ArgumentList ("-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`"") -Verb RunAs
        exit
    } catch {
        Write-Error "Failed to elevate process. Please right-click PowerShell, select 'Run as Administrator', and re-run this script."
        exit 1
    }
}

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " Remote Laptop Unlock - Windows Firewall Configuration" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

$tcpPort = 5000
$udpPort = 5005
$tcpRuleName = "RemoteLaptopUnlock-HTTP-5000"
$udpRuleName = "RemoteLaptopUnlock-UDP-5005"

# ---------------------------------------------------------------------------
# Remove Old / Duplicate Rules
# ---------------------------------------------------------------------------
Write-Host "[1/3] Cleaning up any existing firewall rules..." -ForegroundColor Gray
Get-NetFirewallRule -DisplayName $tcpRuleName -ErrorAction SilentlyContinue | Remove-NetFirewallRule
Get-NetFirewallRule -DisplayName $udpRuleName -ErrorAction SilentlyContinue | Remove-NetFirewallRule

# ---------------------------------------------------------------------------
# Register Inbound TCP Rule (Flask HTTP Server)
# ---------------------------------------------------------------------------
Write-Host "[2/3] Adding inbound TCP rule for port $tcpPort (HTTP Unlock Service)..." -ForegroundColor Green
New-NetFirewallRule -DisplayName $tcpRuleName `
    -Description "Allows incoming HTTP requests from the Remote Laptop Unlock Android app" `
    -Direction Inbound `
    -LocalPort $tcpPort `
    -Protocol TCP `
    -Action Allow `
    -Profile Private, Domain, Public `
    -Enabled True | Out-Null

# ---------------------------------------------------------------------------
# Register Inbound UDP Rule (Discovery Beacon)
# ---------------------------------------------------------------------------
Write-Host "[3/3] Adding inbound UDP rule for port $udpPort (LAN Discovery Beacon)..." -ForegroundColor Green
New-NetFirewallRule -DisplayName $udpRuleName `
    -Description "Allows incoming and broadcast UDP packets for Remote Laptop Unlock discovery" `
    -Direction Inbound `
    -LocalPort $udpPort `
    -Protocol UDP `
    -Action Allow `
    -Profile Private, Domain, Public `
    -Enabled True | Out-Null

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " Firewall rules successfully configured!" -ForegroundColor Green
Write-Host " - TCP Port $tcpPort : ALLOWED (Inbound HTTP)" -ForegroundColor White
Write-Host " - UDP Port $udpPort : ALLOWED (Inbound Discovery)" -ForegroundColor White
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "Press any key to exit..."
[void][System.Console]::ReadKey()
