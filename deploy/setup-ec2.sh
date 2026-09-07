#!/usr/bin/env bash
# One-time EC2 provisioning for the piggyback backend demo box.
# Target: Amazon Linux 2023, free-tier x86 instance (t2.micro / t3.micro).
# Run as root (sudo bash setup-ec2.sh).
#
# ── Bootstrap order (avoids locking yourself out) ────────────────────────────
#  1. Create the instance with SG inbound: 80 (0.0.0.0/0) + 22 (YOUR IP only).
#     A fresh instance's sshd listens on 22 - opening only 2222 first means
#     you cannot get in to run this script.
#  2. SSH in on 22 and run this script (moves sshd to 2222).
#  3. KEEP THIS SESSION OPEN. From a second terminal confirm
#     `ssh -p 2222 ...` works, then in the SG: add 2222 (0.0.0.0/0), remove 22.
#     Port 2222 is bot-noise reduction only - the security boundary is the key.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SSH_PORT=2222
DEPLOY_USER=deploy
APP_DIR=/opt/piggyback
COMPOSE_VERSION=v2.29.7

echo "==> Docker"
dnf install -y docker
systemctl enable --now docker

echo "==> docker compose plugin (AL2023's docker package does not include it)"
mkdir -p /usr/libexec/docker/cli-plugins
curl -fsSL "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-x86_64" \
  -o /usr/libexec/docker/cli-plugins/docker-compose
chmod +x /usr/libexec/docker/cli-plugins/docker-compose
docker compose version

echo "==> 2 GiB swap (persistent - without fstab it vanishes on first reboot)"
if [ ! -f /swapfile ]; then
  dd if=/dev/zero of=/swapfile bs=1M count=2048
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
echo 'vm.swappiness=10' > /etc/sysctl.d/99-swap.conf
sysctl -p /etc/sysctl.d/99-swap.conf

echo "==> Deploy user (docker group only, no sudo)"
if ! id "${DEPLOY_USER}" &>/dev/null; then
  useradd -m -G docker "${DEPLOY_USER}"
fi
mkdir -p "${APP_DIR}/deploy"
chown -R "${DEPLOY_USER}:${DEPLOY_USER}" "${APP_DIR}"

echo "==> Copy the current user's authorized_keys to ${DEPLOY_USER} (deploy key goes here)"
install -d -m 700 -o "${DEPLOY_USER}" -g "${DEPLOY_USER}" "/home/${DEPLOY_USER}/.ssh"
if [ -f "/home/ec2-user/.ssh/authorized_keys" ]; then
  install -m 600 -o "${DEPLOY_USER}" -g "${DEPLOY_USER}" \
    /home/ec2-user/.ssh/authorized_keys "/home/${DEPLOY_USER}/.ssh/authorized_keys"
fi

echo "==> Move sshd to port ${SSH_PORT} (key-only auth)"
sed -i -E "s/^#?Port .*/Port ${SSH_PORT}/" /etc/ssh/sshd_config
sed -i -E "s/^#?PasswordAuthentication .*/PasswordAuthentication no/" /etc/ssh/sshd_config
# SELinux (if enforcing) must allow the new port before sshd will bind it.
if command -v semanage &>/dev/null && [ "$(getenforce 2>/dev/null)" = "Enforcing" ]; then
  semanage port -a -t ssh_port_t -p tcp "${SSH_PORT}" || true
fi
systemctl restart sshd
echo "    sshd now on ${SSH_PORT}. Verify from a SECOND terminal before closing this one!"

echo "==> (Recommended) Enable SSM Session Manager"
echo "    Attach an instance profile with the AmazonSSMManagedInstanceCore policy"
echo "    in the AWS console - gives a debugging shell with zero open ports."

cat <<EOF

==> Done. Next steps (see docs/DEPLOYMENT.md):
  1. Create ${APP_DIR}/.env (as ${DEPLOY_USER}):  MYSQL_PASSWORD=..., JWT_SECRET=...
     then: chown ${DEPLOY_USER}:${DEPLOY_USER} ${APP_DIR}/.env && chmod 600 ${APP_DIR}/.env
  2. Register GitHub Secrets: EC2_HOST (Elastic IP), EC2_USER=${DEPLOY_USER},
     EC2_SSH_KEY (private key), EC2_SSH_PORT=${SSH_PORT}
  3. Update the security group per the bootstrap order comment above.
EOF
