#Requires -Version 5.1
<#
.SYNOPSIS
    Deploy app.jar to S3, then pull and restart on EC2.

.DESCRIPTION
    Build → Upload app.jar → S3 → SSH into EC2 → Pull from S3 → Restart myapp.service

    Infrastructure reference: terraform/main.tf, variables.tf, outputs.tf
      S3 bucket : <project_name>-jar-bucket  (default: makecall-jar-bucket)
      App dir   : /opt/app/app.jar
      Service   : myapp.service  (systemd)
      SSH user  : ec2-user
      Region    : ap-south-1

.PARAMETER JarPath
    Local path to the built JAR file. Default: target\app.jar

.PARAMETER S3Bucket
    S3 bucket name. Default: auto-detected from Terraform output or 'makecall-jar-bucket'

.PARAMETER EC2Host
    EC2 Elastic IP address. Default: auto-detected from Terraform output.

.PARAMETER SshKey
    Path to the .pem SSH key file. Default: auto-detected from Terraform output.

.PARAMETER Region
    AWS region. Default: ap-south-1

.PARAMETER AwsProfile
    AWS CLI profile name (optional).

.PARAMETER SkipBuild
    Skip the Maven build step. Use when JAR is already built.

.PARAMETER NoRollback
    Disable automatic rollback if deployment health-check fails.

.PARAMETER DryRun
    Preview all steps without executing them.

.EXAMPLE
    .\deploy.ps1
    # Full flow: build + upload to S3 + deploy on EC2

.EXAMPLE
    .\deploy.ps1 -SkipBuild
    # Deploy pre-built target\app.jar

.EXAMPLE
    .\deploy.ps1 -EC2Host 65.0.1.2 -SshKey C:\Users\you\.ssh\mykey.pem -SkipBuild

.EXAMPLE
    .\deploy.ps1 -DryRun
    # Preview steps without executing

.NOTES
    Requirements: AWS CLI v2, OpenSSH (built into Windows 10+), Java/Maven (for build step)
    Install AWS CLI: https://aws.amazon.com/cli/
    Enable OpenSSH:  Settings > Apps > Optional features > OpenSSH Client
#>

[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$JarPath      = "",
    [string]$S3Bucket     = "",
    [string]$EC2Host      = "",
    [string]$SshKey       = "",
    [string]$Region       = "ap-south-1",
    [string]$AwsProfile   = "",
    [switch]$SkipBuild,
    [switch]$NoRollback,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ─── Colour helpers ───────────────────────────────────────────────────────────
function Write-Step   { param($msg) Write-Host "`n━━━ $msg ━━━" -ForegroundColor Cyan -NoNewline; Write-Host "" }
function Write-Log    { param($msg) Write-Host "[deploy] $msg"  -ForegroundColor Cyan }
function Write-Ok     { param($msg) Write-Host "[  OK  ] $msg"  -ForegroundColor Green }
function Write-Warn   { param($msg) Write-Host "[ WARN ] $msg"  -ForegroundColor Yellow }
function Write-Err    { param($msg) Write-Host "[ERROR ] $msg"  -ForegroundColor Red; throw $msg }

function Invoke-Step {
    param([string]$Description, [scriptblock]$Action)
    if ($DryRun) {
        Write-Host "  [dry-run] $Description" -ForegroundColor Yellow
    } else {
        & $Action
    }
}

# ─── Script root ──────────────────────────────────────────────────────────────
$ScriptRoot = $PSScriptRoot
$TfDir      = Join-Path $ScriptRoot "terraform"

if (-not $JarPath) { $JarPath = Join-Path $ScriptRoot "target\app.jar" }

$S3Key           = "app.jar"
$SshUser         = "ec2-user"
$RemoteJar       = "/opt/app/app.jar"
$RemoteJarBackup = "/opt/app/app.jar.bak"
$ServiceName     = "myapp.service"
$HealthPort      = 8080
$HealthWaitSecs  = 60

# AWS CLI base command
$AwsBase = if ($AwsProfile) { "aws --profile $AwsProfile" } else { "aws" }

# ─── Step 0: Prerequisites ────────────────────────────────────────────────────
Write-Step "Checking prerequisites"

foreach ($tool in @("aws", "ssh")) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        Write-Err "$tool not found in PATH.$(if($tool -eq 'aws'){' Install: https://aws.amazon.com/cli/'}else{' Enable OpenSSH: Settings > Apps > Optional features'})"
    }
}

if (-not $DryRun) {
    $callerIdentity = Invoke-Expression "$AwsBase sts get-caller-identity --region $Region --output json" 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Err "AWS credentials not configured or expired. Run: aws configure"
    }
    $identity = $callerIdentity | ConvertFrom-Json
    Write-Ok "AWS account: $($identity.Account)  ARN: $($identity.Arn)"
}

# ─── Step 1: Resolve targets from Terraform ───────────────────────────────────
Write-Step "Resolving deployment targets"

if ((-not $S3Bucket) -or (-not $EC2Host)) {
    if (Test-Path $TfDir) {
        Write-Log "Reading Terraform outputs from $TfDir ..."
        try {
            Push-Location $TfDir
            $tfRaw = terraform output -json 2>$null
            Pop-Location

            if ($tfRaw) {
                $tf = $tfRaw | ConvertFrom-Json
                if (-not $S3Bucket -and $tf.s3_bucket_name) { $S3Bucket = $tf.s3_bucket_name.value }
                if (-not $EC2Host  -and $tf.elastic_ip)     { $EC2Host  = $tf.elastic_ip.value     }
                if (-not $SshKey   -and $tf.key_name) {
                    $keyName = $tf.key_name.value
                    $candidate = "$HOME\.ssh\$keyName.pem"
                    if (Test-Path $candidate) { $SshKey = $candidate }
                }
            }
        } catch {
            Write-Warn "Could not read Terraform outputs: $_"
        }
    }
}

# Final fallbacks
if (-not $S3Bucket) { $S3Bucket = "makecall-jar-bucket" }
if (-not $EC2Host)  { Write-Err "EC2 host IP not found. Use -EC2Host <IP> or run 'terraform apply' first." }
if (-not $SshKey) {
    foreach ($candidate in @(
        "$HOME\.ssh\phonebooth-key.pem",
        "$HOME\.ssh\makecall-key.pem",
        "$HOME\.ssh\id_rsa"
    )) {
        if (Test-Path $candidate) { $SshKey = $candidate; break }
    }
    if (-not $SshKey) { Write-Err "SSH key not found. Use -SshKey C:\path\to\key.pem" }
}

# Normalise key path for SSH (use forward slashes in WSL-compatible format)
$SshKeyForSsh = $SshKey -replace '\\', '/'
# If on native Windows (not WSL), convert C:\ paths for OpenSSH
if ($SshKeyForSsh -match '^([A-Za-z]):(.*)') {
    $SshKeyForSsh = "/mnt/$($matches[1].ToLower())$($matches[2])"
}

Write-Log "S3 Bucket  : s3://$S3Bucket/$S3Key"
Write-Log "EC2 Host   : $EC2Host"
Write-Log "SSH Key    : $SshKey"
Write-Log "Region     : $Region"
Write-Ok  "Targets resolved"

# SSH common options
$SshOpts = @("-i", $SshKey, "-o", "StrictHostKeyChecking=no", "-o", "ConnectTimeout=15", "-o", "BatchMode=yes")

# ─── Step 2: Maven build ──────────────────────────────────────────────────────
Write-Step "Building JAR"

if (-not $SkipBuild) {
    $mvnCmd = $null
    if (Get-Command mvn -ErrorAction SilentlyContinue) {
        $mvnCmd = "mvn"
    } elseif (Test-Path (Join-Path $ScriptRoot "mvnw.cmd")) {
        $mvnCmd = Join-Path $ScriptRoot "mvnw.cmd"
    } elseif (Test-Path (Join-Path $ScriptRoot "mvnw")) {
        $mvnCmd = Join-Path $ScriptRoot "mvnw"
    }

    if ($mvnCmd) {
        $pom = Join-Path $ScriptRoot "pom.xml"
        Write-Log "Running: $mvnCmd clean package -DskipTests -f $pom"
        Invoke-Step "mvn clean package -DskipTests" {
            & $mvnCmd clean package -DskipTests -f $pom -q
            if ($LASTEXITCODE -ne 0) { Write-Err "Maven build failed" }
        }
        Write-Ok "Build complete"
    } else {
        Write-Warn "Maven not found — skipping build. Add -SkipBuild to suppress."
    }
} else {
    Write-Log "Skipping build (-SkipBuild)"
}

# ─── Verify local JAR ─────────────────────────────────────────────────────────
if (-not (Test-Path $JarPath)) {
    Write-Err "JAR not found: $JarPath`n  Build first, or use -JarPath <path> -SkipBuild"
}

$jarInfo = Get-Item $JarPath
$jarSizeMB = [math]::Round($jarInfo.Length / 1MB, 2)
Write-Log "Local JAR  : $JarPath ($jarSizeMB MB)"

# ─── Step 3: Upload to S3 ─────────────────────────────────────────────────────
Write-Step "Uploading app.jar → S3"

$S3Uri  = "s3://$S3Bucket/$S3Key"
$deployedAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")

Invoke-Step "aws s3 cp $JarPath $S3Uri" {
    $uploadArgs = @("s3", "cp", $JarPath, $S3Uri,
                    "--region", $Region,
                    "--no-progress",
                    "--metadata", "deployed-at=$deployedAt")
    if ($AwsProfile) { $uploadArgs = @("--profile", $AwsProfile) + $uploadArgs }

    & aws @uploadArgs
    if ($LASTEXITCODE -ne 0) { Write-Err "S3 upload failed" }
}

Write-Ok "Uploaded → $S3Uri"

if (-not $DryRun) {
    $s3Meta = Invoke-Expression "$AwsBase s3 ls $S3Uri --region $Region" 2>&1
    Write-Log "S3 listing : $s3Meta"
}

# ─── Step 4: SSH connectivity check ──────────────────────────────────────────
Write-Step "Connecting to EC2 ($EC2Host)"

if (-not $DryRun) {
    $sshTest = ssh @SshOpts "$SshUser@$EC2Host" "echo 'SSH OK'" 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Cannot SSH to $EC2Host. Check the IP, key path, and security group port 22.`n  Tried key: $SshKey"
    }
    Write-Ok "SSH OK — $sshTest"
}

# ─── Step 5: Remote deployment ────────────────────────────────────────────────
Write-Step "Deploying on EC2"

$noRollbackStr = if ($NoRollback) { "true" } else { "false" }

# Build the remote bash script as a here-string
$remoteScript = @"
#!/bin/bash
set -euo pipefail

S3_URI="s3://$S3Bucket/$S3Key"
REMOTE_JAR="$RemoteJar"
REMOTE_JAR_BACKUP="$RemoteJarBackup"
SERVICE="$ServiceName"
REGION="$Region"
HEALTH_PORT=$HealthPort
HEALTH_WAIT=$HealthWaitSecs
NO_ROLLBACK=$noRollbackStr

log()     { echo "[remote] \`$*"; }
success() { echo "[  OK  ] \`$*"; }
warn()    { echo "[ WARN ] \`$*"; }
die()     { echo "[ERROR ] \`$*" >&2; exit 1; }

# Backup current JAR
if [ -f "\`$REMOTE_JAR" ]; then
  cp "\`$REMOTE_JAR" "\`$REMOTE_JAR_BACKUP"
  log "Backed up \`$REMOTE_JAR → \`$REMOTE_JAR_BACKUP"
fi

# Download new JAR from S3
log "Downloading from \`$S3_URI ..."
aws s3 cp "\`$S3_URI" "\`$REMOTE_JAR" --region "\`$REGION" --only-show-errors || die "S3 download failed"
[ -s "\`$REMOTE_JAR" ] || die "Downloaded JAR is empty"
success "Downloaded: \`$(du -h \`$REMOTE_JAR | cut -f1)"

# Restart service
log "Restarting \`$SERVICE ..."
systemctl restart "\`$SERVICE"
sleep 3

# Wait for port to open
log "Waiting up to \`${HEALTH_WAIT}s for port \`$HEALTH_PORT ..."
elapsed=0
while [ \`$elapsed -lt \`$HEALTH_WAIT ]; do
  if ss -tlnp 2>/dev/null | grep -q ":\`$HEALTH_PORT"; then
    success "Listening on port \`$HEALTH_PORT"
    break
  fi
  elapsed=\`$((elapsed + 5))
  echo "  ... \`${elapsed}s"
  sleep 5
done

if ! ss -tlnp 2>/dev/null | grep -q ":\`$HEALTH_PORT"; then
  warn "Port \`$HEALTH_PORT not open after \`${HEALTH_WAIT}s — checking service log ..."
  systemctl status "\`$SERVICE" --no-pager -l | tail -20 || true

  if [ "\`$NO_ROLLBACK" = "false" ] && [ -f "\`$REMOTE_JAR_BACKUP" ]; then
    warn "Rolling back ..."
    cp "\`$REMOTE_JAR_BACKUP" "\`$REMOTE_JAR"
    systemctl restart "\`$SERVICE"
    sleep 5
    if ss -tlnp 2>/dev/null | grep -q ":\`$HEALTH_PORT"; then
      warn "Rollback OK — old version running"
    else
      die "Rollback failed — manual fix needed"
    fi
    exit 2
  fi
  exit 1
fi

echo ""
echo "=============================================="
systemctl status "\`$SERVICE" --no-pager | head -n 8
echo "----------------------------------------------"
echo "JAR  : \`$(du -h \`$REMOTE_JAR | cut -f1)"
echo "Port : \`$HEALTH_PORT OPEN"
echo "Logs : journalctl -u \`$SERVICE -f"
echo "=============================================="
"@

if ($DryRun) {
    Write-Host "  [dry-run] Would run remote deployment script on $SshUser@$EC2Host" -ForegroundColor Yellow
} else {
    # Write script to a temp file and pipe via SSH
    $tmpScript = [System.IO.Path]::GetTempFileName() + ".sh"
    $remoteScript | Set-Content -Path $tmpScript -Encoding UTF8

    try {
        # Use scp to copy the script, then run it
        scp @SshOpts $tmpScript "${SshUser}@${EC2Host}:/tmp/_deploy_$($PID).sh"
        ssh @SshOpts "$SshUser@$EC2Host" "sudo bash /tmp/_deploy_$($PID).sh; sudo rm -f /tmp/_deploy_$($PID).sh"
        $deployExit = $LASTEXITCODE
    } finally {
        Remove-Item $tmpScript -Force -ErrorAction SilentlyContinue
    }

    switch ($deployExit) {
        0 { Write-Ok "Deployment successful on EC2" }
        2 { Write-Warn "Deployment rolled back to previous version"; exit 2 }
        default { Write-Err "Remote deployment failed (exit $deployExit)" }
    }
}

# ─── Summary ──────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "╔══════════════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║          DEPLOYMENT COMPLETE  ✓                  ║" -ForegroundColor Green
Write-Host "╚══════════════════════════════════════════════════╝" -ForegroundColor Green
Write-Host "  JAR      : $JarPath ($jarSizeMB MB)"
Write-Host "  S3       : $S3Uri"
Write-Host "  EC2      : $SshUser@$EC2Host"
Write-Host "  Service  : $ServiceName"
Write-Host "  App URL  : https://makecall.in"
Write-Host ""
Write-Host "  View logs:" -ForegroundColor Cyan
Write-Host "    ssh -i `"$SshKey`" $SshUser@$EC2Host 'sudo journalctl -u $ServiceName -f'"
Write-Host ""

