#!/usr/bin/env bash
# =============================================================================
#  deploy.sh  —  Build → Upload app.jar to S3 → Pull & restart on EC2
#
#  Infrastructure reference: terraform/main.tf, variables.tf, outputs.tf
#    S3 bucket  : ${project_name}-jar-bucket   (default: makecall-jar-bucket)
#    App dir    : /opt/app/app.jar
#    Service    : myapp.service  (systemd)
#    SSH user   : ec2-user
#    Region     : ap-south-1
#
#  Usage:
#    ./deploy.sh [options]
#
#  Options:
#    --jar      PATH     Local JAR path           (default: target/app.jar)
#    --bucket   NAME     S3 bucket name           (default: from terraform output)
#    --host     IP       EC2 Elastic IP           (default: from terraform output)
#    --key      PATH     SSH key .pem file        (default: ~/.ssh/<key_name>.pem)
#    --region   REGION   AWS region               (default: ap-south-1)
#    --profile  NAME     AWS CLI profile          (optional)
#    --skip-build        Skip Maven build step
#    --no-rollback       Disable auto-rollback on failure
#    --dry-run           Show what would happen, don't execute
#    -h, --help          Show this help
#
#  Examples:
#    ./deploy.sh                              # build + deploy with terraform defaults
#    ./deploy.sh --skip-build                 # deploy pre-built JAR only
#    ./deploy.sh --host 65.0.1.2 --key ~/.ssh/mykey.pem
#    ./deploy.sh --dry-run                    # preview steps
# =============================================================================

set -euo pipefail

# ─── Colours ──────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

log()     { echo -e "${CYAN}[deploy]${RESET} $*"; }
success() { echo -e "${GREEN}[  OK  ]${RESET} $*"; }
warn()    { echo -e "${YELLOW}[ WARN ]${RESET} $*"; }
error()   { echo -e "${RED}[ERROR ]${RESET} $*" >&2; }
step()    { echo -e "\n${BOLD}${CYAN}━━━ $* ━━━${RESET}"; }
die()     { error "$*"; exit 1; }

# ─── Defaults (match terraform/variables.tf) ──────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TF_DIR="$SCRIPT_DIR/terraform"

JAR_LOCAL="$SCRIPT_DIR/target/app.jar"
S3_KEY="app.jar"
REGION="ap-south-1"
SSH_USER="ec2-user"
REMOTE_JAR="/opt/app/app.jar"
REMOTE_JAR_BACKUP="/opt/app/app.jar.bak"
SERVICE_NAME="myapp.service"
HEALTH_CHECK_PORT=8080
HEALTH_WAIT_SECS=60

S3_BUCKET=""
EC2_HOST=""
SSH_KEY=""
AWS_PROFILE=""
SKIP_BUILD=false
NO_ROLLBACK=false
DRY_RUN=false

# ─── Argument parsing ─────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case "$1" in
    --jar)      JAR_LOCAL="$2";    shift 2 ;;
    --bucket)   S3_BUCKET="$2";   shift 2 ;;
    --host)     EC2_HOST="$2";    shift 2 ;;
    --key)      SSH_KEY="$2";     shift 2 ;;
    --region)   REGION="$2";      shift 2 ;;
    --profile)  AWS_PROFILE="$2"; shift 2 ;;
    --skip-build)  SKIP_BUILD=true;    shift ;;
    --no-rollback) NO_ROLLBACK=true;   shift ;;
    --dry-run)     DRY_RUN=true;       shift ;;
    -h|--help)
      sed -n '/^#  Usage:/,/^# ====/p' "$0" | sed 's/^#  \?//'
      exit 0 ;;
    *) die "Unknown option: $1  (run with --help for usage)" ;;
  esac
done

# ─── Dry-run wrapper ──────────────────────────────────────────────────────────
run() {
  if $DRY_RUN; then
    echo -e "  ${YELLOW}[dry-run]${RESET} $*"
  else
    "$@"
  fi
}

# ─── Prerequisite checks ──────────────────────────────────────────────────────
step "Checking prerequisites"

command -v aws  >/dev/null 2>&1 || die "AWS CLI not found. Install from https://aws.amazon.com/cli/"
command -v ssh  >/dev/null 2>&1 || die "ssh not found."
command -v scp  >/dev/null 2>&1 || die "scp not found."

AWS_CMD="aws"
[[ -n "$AWS_PROFILE" ]] && AWS_CMD="aws --profile $AWS_PROFILE"

# Validate AWS credentials
if ! $DRY_RUN; then
  $AWS_CMD sts get-caller-identity --region "$REGION" --output text >/dev/null 2>&1 \
    || die "AWS credentials not configured or expired. Run: aws configure"
fi
success "AWS credentials OK"

# ─── Resolve S3 bucket & EC2 host from Terraform outputs ─────────────────────
step "Resolving deployment targets"

if [[ -z "$S3_BUCKET" || -z "$EC2_HOST" ]]; then
  if [[ -d "$TF_DIR" ]]; then
    log "Reading Terraform outputs from $TF_DIR ..."
    TF_OUTPUTS=$( cd "$TF_DIR" && terraform output -json 2>/dev/null ) || true

    if [[ -n "$TF_OUTPUTS" ]]; then
      [[ -z "$S3_BUCKET" ]] && \
        S3_BUCKET=$(echo "$TF_OUTPUTS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('s3_bucket_name',{}).get('value',''))" 2>/dev/null) || true
      [[ -z "$EC2_HOST" ]] && \
        EC2_HOST=$(echo "$TF_OUTPUTS"  | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('elastic_ip',{}).get('value',''))"    2>/dev/null) || true
      [[ -z "$SSH_KEY" ]] && {
        KEY_NAME=$(echo "$TF_OUTPUTS"  | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('key_name',{}).get('value',''))"       2>/dev/null) || true
        [[ -n "$KEY_NAME" ]] && SSH_KEY="$HOME/.ssh/${KEY_NAME}.pem"
      }
    fi
  fi
fi

# Final fallbacks
[[ -z "$S3_BUCKET" ]] && S3_BUCKET="makecall-jar-bucket"
[[ -z "$EC2_HOST"  ]] && die "EC2 host IP not found. Use --host <IP> or run 'terraform apply' first."
[[ -z "$SSH_KEY"   ]] && {
  # Try common key locations
  for candidate in "$HOME/.ssh/phonebooth-key.pem" "$HOME/.ssh/makecall-key.pem" "$HOME/.ssh/id_rsa"; do
    [[ -f "$candidate" ]] && { SSH_KEY="$candidate"; break; }
  done
  [[ -z "$SSH_KEY" ]] && die "SSH key not found. Use --key /path/to/key.pem"
}

[[ -f "$SSH_KEY" ]] || die "SSH key not found: $SSH_KEY"
chmod 400 "$SSH_KEY" 2>/dev/null || true

log "S3 Bucket : s3://$S3_BUCKET/$S3_KEY"
log "EC2 Host  : $EC2_HOST"
log "SSH Key   : $SSH_KEY"
log "Region    : $REGION"
success "Targets resolved"

# ─── SSH helper ───────────────────────────────────────────────────────────────
#SSH_OPTS="-i $SSH_KEY -o StrictHostKeyChecking=no -o ConnectTimeout=15 -o BatchMode=yes"
#remote() { ssh $SSH_OPTS "$SSH_USER@$EC2_HOST" "$@"; }
SSH_OPTS="-4 -i $SSH_KEY -o StrictHostKeyChecking=no -o ConnectTimeout=15 -o BatchMode=yes"
remote() { ssh $SSH_OPTS "$SSH_USER@$EC2_HOST" "$@"; }

# ─── Step 1: Build (optional) ─────────────────────────────────────────────────
if ! $SKIP_BUILD; then
  step "Building JAR with Maven"
  if command -v mvn >/dev/null 2>&1; then
    run mvn -f "$SCRIPT_DIR/pom.xml" clean package -DskipTests -q
    success "Maven build complete"
  elif [[ -f "$SCRIPT_DIR/mvnw" ]]; then
    run "$SCRIPT_DIR/mvnw" -f "$SCRIPT_DIR/pom.xml" clean package -DskipTests -q
    success "Maven wrapper build complete"
  else
    warn "Maven not found — skipping build (use --skip-build to suppress this warning)"
  fi
else
  log "Skipping build (--skip-build)"
fi

# ─── Verify local JAR ─────────────────────────────────────────────────────────
[[ -f "$JAR_LOCAL" ]] || die "JAR not found: $JAR_LOCAL\n  Build first, or use --jar <path>"
JAR_SIZE=$(du -h "$JAR_LOCAL" | cut -f1)
JAR_MD5=$(md5sum "$JAR_LOCAL" 2>/dev/null | cut -d' ' -f1 || md5 -q "$JAR_LOCAL" 2>/dev/null || echo "n/a")
log "Local JAR : $JAR_LOCAL ($JAR_SIZE, md5: $JAR_MD5)"

# ─── Step 2: Upload JAR to S3 ─────────────────────────────────────────────────
step "Uploading app.jar → S3"

S3_URI="s3://$S3_BUCKET/$S3_KEY"
run $AWS_CMD s3 cp "$JAR_LOCAL" "$S3_URI" \
    --region "$REGION" \
    --no-progress \
    --metadata "md5=$JAR_MD5,deployed-at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"

success "Uploaded → $S3_URI"

# ─── Verify upload ────────────────────────────────────────────────────────────
if ! $DRY_RUN; then
  S3_SIZE=$( $AWS_CMD s3 ls "$S3_URI" --region "$REGION" | awk '{print $3}' )
  log "S3 object size: $S3_SIZE bytes"
fi

# ─── Step 3: Test SSH connectivity ────────────────────────────────────────────
step "Connecting to EC2 ($EC2_HOST)"

if ! $DRY_RUN; then
ssh $SSH_OPTS "$SSH_USER@$EC2_HOST" "echo 'SSH OK'" >/dev/null 2>&1 \
  || die "Cannot SSH to $EC2_HOST. Check the host IP, key, and security group."
fi
success "SSH connection OK"

# ─── Step 4: Deploy on EC2 ────────────────────────────────────────────────────
step "Deploying on EC2"

if $DRY_RUN; then
  log "[dry-run] Would run remote deployment script on $EC2_HOST"
else

DEPLOY_SCRIPT=$(cat <<'REMOTE_SCRIPT'
#!/bin/bash
set -euo pipefail

S3_URI="s3://$S3_BUCKET/$S3_KEY"
REMOTE_JAR="$REMOTE_JAR"
REMOTE_JAR_BACKUP="$REMOTE_JAR_BACKUP"
SERVICE="$SERVICE_NAME"
REGION="$REGION"
HEALTH_PORT="$HEALTH_CHECK_PORT"
HEALTH_WAIT="$HEALTH_WAIT_SECS"
NO_ROLLBACK="$NO_ROLLBACK"

log()     { echo "[remote] \$*"; }
success() { echo "[  OK  ] \$*"; }
warn()    { echo "[ WARN ] \$*"; }
die()     { echo "[ERROR ] \$*" >&2; exit 1; }

# --- Backup current JAR ---
if [ -f "\$REMOTE_JAR" ]; then
  cp "\$REMOTE_JAR" "\$REMOTE_JAR_BACKUP"
  log "Backed up existing JAR → \$REMOTE_JAR_BACKUP"
fi

# --- Pull new JAR from S3 ---
# --- Pull new JAR from S3 ---
log "============================================"
log "Starting S3 JAR download"
log "S3 URI       : $S3_URI"
log "Remote JAR   : $REMOTE_JAR"
log "Region       : $REGION"
log "Endpoint     : https://s3.dualstack.${REGION}.amazonaws.com"
log "============================================"

log "Checking AWS CLI..."
aws --version

log "Checking AWS identity..."
aws sts get-caller-identity --output json || warn "STS identity check failed"

log "Checking S3 bucket access..."
aws s3 ls "s3://makecall-jar-bucket" \
  --region "$REGION" \
  --endpoint-url "https://s3.dualstack.${REGION}.amazonaws.com" \
  || warn "S3 bucket access check failed"

log "Starting JAR download..."

timeout 60 aws s3 cp "$S3_URI" "$REMOTE_JAR" \
  --region "$REGION" \
  --endpoint-url "https://s3.dualstack.${REGION}.amazonaws.com" \
  --only-show-errors

log "Starting JAR download..."

if ! timeout 60 aws s3 cp "$S3_URI" "$REMOTE_JAR" \
    --region "$REGION" \
    --endpoint-url "https://s3.dualstack.${REGION}.amazonaws.com" \
    --only-show-errors; then

    echo "[ERROR] S3 download failed."
    echo "[ERROR] S3 URI: $S3_URI"
    echo "[ERROR] Target: $REMOTE_JAR"
    exit 1
fi

echo "[OK] S3 download completed."

log "Checking downloaded JAR..."

if [ ! -f "$REMOTE_JAR" ]; then
    die "Downloaded JAR is missing: $REMOTE_JAR"
fi

if [ ! -s "$REMOTE_JAR" ]; then
    die "Downloaded JAR is empty: $REMOTE_JAR"
fi

NEW_SIZE=$(du -h "$REMOTE_JAR" | cut -f1)

success "JAR downloaded successfully: $NEW_SIZE"

# --- Restart service ---
log "Restarting \$SERVICE ..."
systemctl restart "\$SERVICE"
sleep 3

# --- Wait for health check ---
log "Waiting up to \${HEALTH_WAIT}s for port \$HEALTH_PORT ..."
elapsed=0
while [ \$elapsed -lt \$HEALTH_WAIT ]; do
  if ss -tlnp 2>/dev/null | grep -q ":\$HEALTH_PORT"; then
    success "Application is listening on port \$HEALTH_PORT"
    break
  fi
  elapsed=\$((elapsed + 5))
  echo "  ... \${elapsed}s elapsed"
  sleep 5
done

if ! ss -tlnp 2>/dev/null | grep -q ":\$HEALTH_PORT"; then
  warn "Port \$HEALTH_PORT not responding after \${HEALTH_WAIT}s"
  # Check service status for clues
  systemctl status "\$SERVICE" --no-pager -l | tail -20 || true

  if [ "\$NO_ROLLBACK" = "false" ] && [ -f "\$REMOTE_JAR_BACKUP" ]; then
    warn "Rolling back to previous version ..."
    cp "\$REMOTE_JAR_BACKUP" "\$REMOTE_JAR"
    systemctl restart "\$SERVICE"
    sleep 5
    if ss -tlnp 2>/dev/null | grep -q ":\$HEALTH_PORT"; then
      warn "Rollback successful — previous version is running"
    else
      die "Rollback also failed — manual intervention required"
    fi
    exit 2
  fi
  exit 1
fi

# --- Final status ---
echo ""
echo "============================================"
systemctl status "\$SERVICE" --no-pager | head -n 8
echo "--------------------------------------------"
echo "JAR size : \$NEW_SIZE"
echo "Service  : \$(systemctl is-active \$SERVICE)"
echo "Port     : \$HEALTH_PORT OPEN"
echo "Logs     : journalctl -u \$SERVICE -f"
echo "============================================"
REMOTE_SCRIPT
)

# Run the remote script via SSH as root (service requires sudo/root)
echo "$DEPLOY_SCRIPT" | ssh $SSH_OPTS "$SSH_USER@$EC2_HOST" "sudo bash -s"
DEPLOY_EXIT=$?

case $DEPLOY_EXIT in
  0) success "Deployment successful on EC2" ;;
  2) warn "Deployment rolled back to previous version"; exit 2 ;;
  *) die "Deployment failed on EC2 (exit $DEPLOY_EXIT)" ;;
esac

fi  # end dry-run check

# ─── Summary ──────────────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}${BOLD}╔══════════════════════════════════════════════════╗${RESET}"
echo -e "${GREEN}${BOLD}║           DEPLOYMENT COMPLETE  ✓                ║${RESET}"
echo -e "${GREEN}${BOLD}╚══════════════════════════════════════════════════╝${RESET}"
echo -e "  JAR     : $JAR_LOCAL ($JAR_SIZE)"
echo -e "  S3      : $S3_URI"
echo -e "  EC2     : $SSH_USER@$EC2_HOST"
echo -e "  Service : $SERVICE_NAME"
echo -e "  URL     : https://$(remote "hostname -f 2>/dev/null || echo $EC2_HOST" 2>/dev/null || echo "$EC2_HOST")"
echo ""
echo -e "  View logs : ssh $SSH_OPTS $SSH_USER@$EC2_HOST 'sudo journalctl -u $SERVICE_NAME -f'"
echo ""

