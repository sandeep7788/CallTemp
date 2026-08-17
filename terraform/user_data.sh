#!/bin/bash

set -eo pipefail

LOG_FILE="/var/log/user-data.log"
exec > >(tee -a "$LOG_FILE") 2>&1

echo "=== User-Data Script Started at $(date) ==="

error_exit() {
    echo "ERROR: $1"
    exit 1
}

trap 'error_exit "Script failed at line $LINENO"' ERR

# ---------------------------------------------------------------------------
# STEP 1: Wait for network
# ---------------------------------------------------------------------------
echo "[Step 1] Waiting for network..."

max_attempts=60
attempt=0
while [ $attempt -lt $max_attempts ]; do
    if curl -s --connect-timeout 2 http://169.254.169.254/latest/meta-data/instance-id >/dev/null 2>&1; then
        echo "✓ Metadata service reachable"
        break
    fi
    attempt=$((attempt + 1))
    sleep 2
done

# ---------------------------------------------------------------------------
# STEP 2: System update
# ---------------------------------------------------------------------------
echo "[Step 2] Updating system..."

yum clean all
yum update -y --allowerasing --skip-broken 2>&1 | tail -n 5

echo "✓ System updated"

# ---------------------------------------------------------------------------
# STEP 3: Install Java 21
# ---------------------------------------------------------------------------
echo "[Step 3] Installing Java 21..."

if yum install -y --allowerasing --skip-broken java-21-amazon-corretto 2>/dev/null; then
    echo "✓ Java 21 (Amazon Corretto) installed"
elif yum install -y --allowerasing --skip-broken java-21-openjdk 2>/dev/null; then
    echo "✓ Java 21 (OpenJDK) installed"
else
    error_exit "Failed to install Java 21"
fi

java -version

# ---------------------------------------------------------------------------
# STEP 4: Install tools
# ---------------------------------------------------------------------------
echo "[Step 4] Installing tools..."

yum install -y --allowerasing --skip-broken curl unzip wget 2>&1 | tail -n 3

echo "✓ Tools installed"

# ---------------------------------------------------------------------------
# STEP 5: Install AWS CLI v2
# ---------------------------------------------------------------------------
echo "[Step 5] Installing AWS CLI v2..."

if ! command -v aws >/dev/null 2>&1; then
    ARCH=$(uname -m)
    if [ "$ARCH" = "aarch64" ]; then
        AWSCLI_URL="https://awscli.amazonaws.com/awscli-exe-linux-aarch64.zip"
    else
        AWSCLI_URL="https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip"
    fi
    cd /tmp
    curl -fsSL --connect-timeout 10 --max-time 60 "$AWSCLI_URL" -o awscliv2.zip
    unzip -qo awscliv2.zip
    ./aws/install --update
    cd - >/dev/null
fi

echo "✓ AWS CLI: $(aws --version | cut -d' ' -f1-2)"

# ---------------------------------------------------------------------------
# STEP 6: Create app directory
# ---------------------------------------------------------------------------
echo "[Step 6] Creating app directory..."

mkdir -p /opt/app
chmod 755 /opt/app

echo "✓ Directory ready"

# ---------------------------------------------------------------------------
# STEP 7: Download JAR from S3
# ---------------------------------------------------------------------------
echo "[Step 7] Downloading JAR from S3..."

S3_BUCKET="${s3_bucket}"
JAR_NAME="${jar_name}"
S3_URI="s3://$${S3_BUCKET}/$${JAR_NAME}"
JAR_PATH="/opt/app/app.jar"

max_retries=20
attempt=0
while [ $attempt -lt $max_retries ]; do
    attempt=$((attempt + 1))
    echo "  Attempt $attempt/$max_retries..."
    if aws s3 cp "$S3_URI" "$JAR_PATH" --only-show-errors 2>/dev/null && [ -f "$JAR_PATH" ] && [ -s "$JAR_PATH" ]; then
        echo "✓ JAR downloaded: $(ls -lh $JAR_PATH | awk '{print $5}')"
        break
    fi
    [ $attempt -lt $max_retries ] && sleep $((3 * attempt))
done

[ ! -f "$JAR_PATH" ] || [ ! -s "$JAR_PATH" ] && error_exit "JAR download failed after $max_retries attempts"

# ---------------------------------------------------------------------------
# STEP 8: Create startup script
# ---------------------------------------------------------------------------
echo "[Step 8] Creating startup script..."

cat > /usr/local/bin/start-myapp.sh << 'SCRIPT'
#!/bin/bash
set -euo pipefail
JAR="/opt/app/app.jar"
echo "Starting application from: $JAR"
exec java -jar "$JAR"
SCRIPT

chmod +x /usr/local/bin/start-myapp.sh

echo "✓ Startup script ready"

# ---------------------------------------------------------------------------
# STEP 9: Create systemd service
# ---------------------------------------------------------------------------
echo "[Step 9] Creating systemd service..."

cat > /etc/systemd/system/myapp.service << 'SERVICE'
[Unit]
Description=Spring Boot Application (makecall.in)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=root
ExecStart=/usr/local/bin/start-myapp.sh
Restart=always
RestartSec=10
TimeoutStartSec=300
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
SERVICE

chmod 644 /etc/systemd/system/myapp.service

echo "✓ Service file created"

# ---------------------------------------------------------------------------
# STEP 10: Start Spring Boot service
# ---------------------------------------------------------------------------
echo "[Step 10] Starting Spring Boot service..."

systemctl daemon-reload
systemctl enable myapp.service
systemctl start myapp.service
sleep 3

echo "✓ Spring Boot service started"

# ---------------------------------------------------------------------------
# STEP 11: Verify Spring Boot is listening
# ---------------------------------------------------------------------------
echo "[Step 11] Verifying Spring Boot application..."

max_wait=240
elapsed=0
while [ $elapsed -lt $${max_wait}  ]; do
    if ss -tlnp 2>/dev/null | grep -q ':8080'; then
        echo "✓ Spring Boot is listening on port 8080"
        systemctl status myapp.service --no-pager | head -n 5
        break
    fi
    elapsed=$((elapsed + 5))
    echo "  Waiting for port 8080... ($elapsed/$max_wait seconds)"
    sleep 5
done

if ! ss -tlnp 2>/dev/null | grep -q ':8080'; then
    echo "WARNING: Spring Boot not yet on port 8080 after $${max_wait} s — Nginx will still be configured"
fi

# ---------------------------------------------------------------------------
# STEP 12: Install Nginx
# ---------------------------------------------------------------------------
echo "[Step 12] Installing Nginx..."

yum install -y nginx
systemctl enable nginx
systemctl start nginx

echo "✓ Nginx installed and started"

# ---------------------------------------------------------------------------
# STEP 13: Configure Nginx reverse proxy
# ---------------------------------------------------------------------------
echo "[Step 13] Configuring Nginx reverse proxy..."

# Remove default server block to avoid conflicts
rm -f /etc/nginx/conf.d/default.conf

# Create certbot webroot directory
mkdir -p /var/www/certbot

# Write full config (HTTP redirect + HTTPS reverse proxy).
# If certs are not yet present nginx -t will fail, so we fall back to
# an HTTP-only config that Certbot can use for the ACME challenge.
cat > /etc/nginx/conf.d/myapp.conf <<EOF
# HTTP — redirect all traffic to HTTPS
server {
    listen 80;
    server_name ${domain_name} www.${domain_name};

    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    location / {
        return 301 https://\$host\$request_uri;
    }
}

# HTTPS — reverse proxy to Spring Boot
server {
    listen 443 ssl;
    server_name ${domain_name} www.${domain_name};

    ssl_certificate     /etc/letsencrypt/live/${domain_name}/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/${domain_name}/privkey.pem;

    ssl_protocols             TLSv1.2 TLSv1.3;
    ssl_ciphers               HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;
    ssl_session_cache         shared:SSL:10m;
    ssl_session_timeout       10m;

    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Frame-Options            SAMEORIGIN                           always;
    add_header X-Content-Type-Options     nosniff                              always;
    add_header X-XSS-Protection           "1; mode=block"                      always;
    add_header Referrer-Policy            "strict-origin-when-cross-origin"    always;

    location / {
        proxy_pass         http://127.0.0.1:8080;
        proxy_http_version 1.1;

        proxy_set_header Host              \$host;
        proxy_set_header X-Real-IP         \$remote_addr;
        proxy_set_header X-Forwarded-For   \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;

        # WebSocket support
        proxy_set_header Upgrade    \$http_upgrade;
        proxy_set_header Connection "upgrade";

        proxy_connect_timeout 60s;
        proxy_read_timeout    300s;
        proxy_send_timeout    300s;

        proxy_buffering    on;
        proxy_buffer_size  16k;
        proxy_buffers      4 64k;
    }

    error_page 502 503 504 /50x.html;
    location = /50x.html {
        root /usr/share/nginx/html;
    }
}
EOF

# If SSL certs are absent the HTTPS server block fails validation.
# Use a temporary HTTP-only config so Certbot can complete the ACME challenge.
if ! nginx -t 2>/dev/null; then
    echo "  Certificates absent — using HTTP-only config until Certbot runs"
    cat > /etc/nginx/conf.d/myapp.conf <<EOF2
server {
    listen 80;
    server_name ${domain_name} www.${domain_name};

    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    location / {
        proxy_pass         http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host              \$host;
        proxy_set_header X-Real-IP         \$remote_addr;
        proxy_set_header X-Forwarded-For   \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_set_header Upgrade           \$http_upgrade;
        proxy_set_header Connection        "upgrade";
        proxy_connect_timeout 60s;
        proxy_read_timeout    300s;
        proxy_send_timeout    300s;
    }
}
EOF2
fi

nginx -t
systemctl reload nginx

echo "✓ Nginx reverse proxy configured"

# ---------------------------------------------------------------------------
# STEP 14: Install Certbot
# ---------------------------------------------------------------------------
echo "[Step 14] Installing Certbot..."

dnf install -y python3-pip
pip3 install --quiet certbot certbot-nginx

echo "✓ Certbot installed"

# ---------------------------------------------------------------------------
# STEP 15: Generate SSL certificate via Let's Encrypt
# ---------------------------------------------------------------------------
echo "[Step 15] Generating SSL certificate for ${domain_name}..."

# Brief wait for DNS propagation and nginx to stabilise
sleep 30

SSL_SUCCESS=false

if certbot --nginx \
    -d ${domain_name} \
    -d www.${domain_name} \
    --non-interactive \
    --agree-tos \
    --email s.pareekpro@gmail.com \
    --redirect \
    2>&1; then

    SSL_SUCCESS=true
    echo "✓ SSL certificate obtained and HTTPS configured"

else
    echo "WARNING: SSL certificate could not be obtained (DNS may not have propagated yet)."
    echo "  Retry: certbot --nginx -d ${domain_name} -d www.${domain_name} --non-interactive --agree-tos --email s.pareekpro@gmail.com --redirect"
fi

# ---------------------------------------------------------------------------
# STEP 16: Configure automatic certificate renewal
# ---------------------------------------------------------------------------
echo "[Step 16] Configuring automatic SSL renewal..."

# Runs twice daily as recommended by Let's Encrypt; reloads nginx after renewal
echo "0 0,12 * * * root certbot renew --quiet --post-hook 'systemctl reload nginx'" > /etc/cron.d/certbot-renew
chmod 644 /etc/cron.d/certbot-renew

if [ "$SSL_SUCCESS" = "true" ]; then
    certbot renew --dry-run && echo "✓ Renewal dry-run successful"
fi

echo "✓ Automatic SSL renewal configured"

# ---------------------------------------------------------------------------
# FINAL SUMMARY
# ---------------------------------------------------------------------------
echo ""
echo "=========================================="
echo "=== Deployment Complete at $(date) ==="
echo "=========================================="
echo "Java:         $(java -version 2>&1 | head -n 1)"
echo "Spring Boot:  $(systemctl is-active myapp.service)"
echo "Nginx:        $(systemctl is-active nginx)"
echo "Port 8080:    $(ss -tlnp 2>/dev/null | grep -q ':8080' && echo 'LISTENING' || echo 'NOT LISTENING')"
echo "SSL:          $SSL_SUCCESS"
echo "Domain:       https://${domain_name}"
echo "Bootstrap:    tail -f /var/log/user-data.log"
echo "App logs:     journalctl -u myapp.service -f"
echo "=========================================="
