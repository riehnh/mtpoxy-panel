#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

# Public install example in README. --source is used for a checked-out repository.
repository=
source_directory=
public_ip=
while (($#)); do
    case "$1" in
        --repo) repository="${2:?}"; shift 2 ;;
        --source) source_directory="${2:?}"; shift 2 ;;
        --public-ip) public_ip="${2:?}"; shift 2 ;;
        *) echo "Unknown option: $1" >&2; exit 2 ;;
    esac
done
if ((EUID != 0)); then echo 'Запустите установщик от root (sudo bash …).' >&2; exit 1; fi
if [[ $(uname -m) != x86_64 ]]; then echo 'Поддерживается VPS x86_64/amd64.' >&2; exit 1; fi
source /etc/os-release
case "$ID" in ubuntu|debian) ;; *) echo 'Требуется Ubuntu или Debian.' >&2; exit 1 ;; esac
if [[ -z "$source_directory" && ! "$repository" =~ ^https://github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(\.git)?$ ]]; then
    echo 'Укажите --repo https://github.com/USER/mtpoxy-panel или --source /путь/к/проекту.' >&2
    exit 2
fi
export DEBIAN_FRONTEND=noninteractive
echo '[1/6] Устанавливаем зависимости'
apt-get update -qq
apt-get install -y --no-install-recommends python3 curl ca-certificates git openssl nftables haproxy \
    build-essential pkg-config libssl-dev zlib1g-dev python3-cryptography python3-socks
if [[ -z "$public_ip" ]]; then
    public_ip=$(curl -4fsS --connect-timeout 5 --max-time 10 https://api.ipify.org || true)
fi
if [[ -z "$public_ip" ]]; then
    public_ip=$(ip -4 route get 1.1.1.1 | sed -n 's/.* src \([^ ]*\).*/\1/p' | head -1)
fi
python3 - "$public_ip" <<'PY'
import ipaddress,sys
address=ipaddress.IPv4Address(sys.argv[1])
if not address.is_global: raise SystemExit('Укажите публичный IPv4 параметром --public-ip')
PY

echo '[2/6] Устанавливаем Python-панель'
if [[ -z "$source_directory" ]]; then
    source_directory=$(mktemp -d /tmp/mtpoxy-source.XXXXXX)
    git clone --depth 1 "$repository" "$source_directory"
fi
[[ -f "$source_directory/server.py" && -f "$source_directory/static/panel.html" ]]
install -d -m 0755 /opt/mtpoxy
install -d -m 0700 /var/lib/mtpoxy /etc/mtpoxy
if [[ $(readlink -f "$source_directory") != /opt/mtpoxy ]]; then
    cp "$source_directory/"*.py /opt/mtpoxy/
    cp -r "$source_directory/static" /opt/mtpoxy/
fi
python3 -m compileall -q /opt/mtpoxy

echo '[3/6] Собираем официальный WEB relay и MTProxy'
upstream=/opt/mtpoxy-upstream
commit=c8adb8b7c6b7fc46c12ae3acb68be9070c26a8e8
if [[ ! -d "$upstream/.git" ]]; then git clone https://github.com/telegramdesktop/tproxy-server.git "$upstream"; fi
git -C "$upstream" fetch origin "$commit"
git -C "$upstream" checkout --detach "$commit"
if [[ ! -x /opt/MTProxy/objs/bin/mtproto-proxy ]]; then
    # The upstream backend builder is retained. Fetch routing data with bounded
    # retries separately, so a blocked Telegram website cannot hang installation.
    cp "$upstream/deploy/install-mtproxy.sh" /opt/mtpoxy/build-mtproxy.sh
    python3 - <<'PY'
p='/opt/mtpoxy/build-mtproxy.sh'
s=open(p).read().split('install -d -o root -g mtproxy -m 0750 /etc/mtproxy')[0]
open(p,'w').write(s)
PY
    bash /opt/mtpoxy/build-mtproxy.sh
fi
if ! python3 /opt/mtpoxy/fetch_proxy_config.py; then
    echo 'Telegram пока недоступен напрямую. После входа укажите SOCKS5-выход в настройках панели.'
fi
python_backend=/opt/mtpoxy-python-backend
if [[ ! -d "$python_backend/.git" ]]; then
    git clone https://github.com/alexbers/mtprotoproxy.git "$python_backend"
fi
git -C "$python_backend" fetch origin 0614c35020943b2080c9bd27b5e6336270af389f
git -C "$python_backend" checkout --detach 0614c35020943b2080c9bd27b5e6336270af389f
if [[ -x /opt/mtpoxy-go/go/bin/go ]]; then
    export PATH="/opt/mtpoxy-go/go/bin:$PATH"
fi
if ! command -v go >/dev/null || ! go version | grep -Eq 'go1\.(2[0-9]|[3-9][0-9])\.'; then
    archive=$(mktemp /tmp/mtpoxy-go.XXXXXX.tar.gz)
    curl -4fsSL --connect-timeout 15 --max-time 300 https://go.dev/dl/go1.26.5.linux-amd64.tar.gz -o "$archive"
    echo "5c2c3b16caefa1d968a94c1daca04a7ca301a496d9b086e17ad77bb81393f053  $archive" | sha256sum -c -
    install -d /opt/mtpoxy-go
    tar -xzf "$archive" -C /opt/mtpoxy-go
    rm -f "$archive"
    export PATH="/opt/mtpoxy-go/go/bin:$PATH"
fi
pushd "$upstream" >/dev/null
go test ./...
go build -trimpath -ldflags='-s -w' -o /usr/local/bin/tproxy-server ./cmd/tproxy-server
popd >/dev/null
if [[ ! -x /usr/local/bin/caddy ]]; then
    archive=$(mktemp /tmp/mtpoxy-caddy.XXXXXX.tar.gz)
    curl -4fsSL --connect-timeout 15 --max-time 180 \
        https://github.com/caddyserver/caddy/releases/download/v2.11.4/caddy_2.11.4_linux_amd64.tar.gz -o "$archive"
    echo "8220d1f013b6f27510247b2360c9e0ca9f018feebd82515f07635318b34ff9777ccc8fd0b6e6f2486ce3a33fe389fbb7db12d05baa474f4587509fb4f5ebf1c9  $archive" | sha512sum -c -
    tar -xzf "$archive" -C /usr/local/bin caddy
    chmod 0755 /usr/local/bin/caddy
    rm -f "$archive"
fi

echo '[4/6] Готовим HTTPS-вход в панель'
python3 - "$public_ip" <<'PY'
import secrets,sys
sys.path.insert(0,'/opt/mtpoxy')
from common import ROOT,save_json
from server import password_hash
if not (ROOT/'settings.json').exists():
    password=secrets.token_urlsafe(18)
    salt=secrets.token_hex(16)
    settings={'public_ip':sys.argv[1],'email':'','dynv6_token':'',
        'salt':salt,'password_hash':password_hash(password,salt),'panel_hostname':''}
    import json
    from pathlib import Path
    try:
        config=json.loads(Path('/usr/local/x-ui/bin/config.json').read_text())
        local=next(i for i in config.get('inbounds',[]) if i.get('protocol')=='socks' and i.get('listen')=='127.0.0.1')
        settings.update(socks_host='127.0.0.1',socks_port=int(local['port']))
        accounts=local.get('settings',{}).get('accounts',[])
        if accounts: settings.update(socks_user=accounts[0]['user'],socks_password=accounts[0]['pass'])
    except (OSError,ValueError,KeyError,StopIteration): pass
    save_json(ROOT/'settings.json',settings)
    (ROOT/'initial-password').write_text(password+'\n')
    (ROOT/'initial-password').chmod(0o600)
PY
if [[ ! -f /var/lib/mtpoxy/panel-cert.pem ]]; then
    openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
        -subj /CN=MTPoxy-Panel -addext "subjectAltName=IP:$public_ip" \
        -keyout /var/lib/mtpoxy/panel-key.pem -out /var/lib/mtpoxy/panel-cert.pem >/dev/null 2>&1
fi
python3 - <<'PY'
import socket
from pathlib import Path
for address,port in [('127.0.0.1',9080),('0.0.0.0',9443)]:
    with socket.socket() as sock:
        try: sock.bind((address,port))
        except OSError:
            if not Path('/etc/systemd/system/mtpoxy-panel.service').exists():
                raise SystemExit(f'Порт {port} занят другим приложением')
PY
cat > /etc/systemd/system/mtpoxy-panel.service <<'EOF'
[Unit]
Description=MTPoxy Python control panel
After=network-online.target
Wants=network-online.target
[Service]
WorkingDirectory=/opt/mtpoxy
ExecStart=/usr/bin/python3 /opt/mtpoxy/server.py serve
Restart=on-failure
RestartSec=3
UMask=0077
PrivateTmp=true
NoNewPrivileges=true
[Install]
WantedBy=multi-user.target
EOF

echo '[5/6] Настраиваем службы и маршрутизацию'
cat > /etc/systemd/system/mtpoxy-caddy.service <<'EOF'
[Unit]
Description=MTPoxy HTTPS sites
After=network-online.target mtpoxy-panel.service
[Service]
ExecStart=/usr/local/bin/caddy run --config /etc/mtpoxy/Caddyfile --adapter caddyfile
ExecReload=/usr/local/bin/caddy reload --force --config /etc/mtpoxy/Caddyfile --adapter caddyfile --address 127.0.0.1:2029
Environment=HOME=/var/lib/mtpoxy/caddy
Restart=on-failure
RestartSec=3
UMask=0077
[Install]
WantedBy=multi-user.target
EOF
cat > /etc/systemd/system/mtpoxy-router.service <<'EOF'
[Unit]
Description=MTPoxy TLS name router
After=network-online.target
[Service]
Type=notify
ExecStart=/usr/sbin/haproxy -Ws -f /etc/mtpoxy/haproxy.cfg -p /run/mtpoxy-haproxy.pid
ExecReload=/usr/sbin/haproxy -c -f /etc/mtpoxy/haproxy.cfg
ExecReload=/bin/kill -USR2 $MAINPID
Restart=on-failure
[Install]
WantedBy=multi-user.target
EOF
cat > /etc/mtpoxy/routing.nft <<EOF
table ip mtpoxy_route {
 chain incoming {
  type nat hook prerouting priority dstnat; policy accept;
  ip daddr $public_ip tcp dport 443 counter redirect to :1443
 }
 chain protect_router {
  type filter hook input priority -9; policy accept;
  iifname != "lo" tcp dport 1443 ct original proto-dst != 443 drop
 }
}
EOF
cat > /etc/systemd/system/mtpoxy-routing.service <<'EOF'
[Unit]
Description=MTPoxy inbound TLS routing
After=network-online.target mtpoxy-router.service
Requires=mtpoxy-router.service
[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=/usr/sbin/nft -f /etc/mtpoxy/routing.nft
ExecStop=/usr/sbin/nft delete table ip mtpoxy_route
[Install]
WantedBy=multi-user.target
EOF
if [[ ! -f /etc/mtpoxy/backend.nft ]]; then
cat > /etc/mtpoxy/backend.nft <<'EOF'
table inet mtpoxy_backend {
 chain input {
  type filter hook input priority -10; policy accept;
 }
}
EOF
fi
cat > /etc/systemd/system/mtpoxy-backend-firewall.service <<'EOF'
[Unit]
Description=MTPoxy local backend firewall
Before=mtpoxy-panel.service
[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=/usr/sbin/nft -f /etc/mtpoxy/backend.nft
ExecStop=/usr/sbin/nft delete table inet mtpoxy_backend
[Install]
WantedBy=multi-user.target
EOF
cat > /etc/systemd/system/mtpoxy-refresh.service <<'EOF'
[Unit]
Description=Refresh official Telegram routing configuration
[Service]
Type=oneshot
ExecStart=/usr/bin/python3 /opt/mtpoxy/fetch_proxy_config.py
EOF
cat > /etc/systemd/system/mtpoxy-refresh.timer <<'EOF'
[Unit]
Description=Daily Telegram routing update
[Timer]
OnBootSec=10min
OnUnitActiveSec=1d
Persistent=true
[Install]
WantedBy=timers.target
EOF
systemctl daemon-reload
systemctl enable --now mtpoxy-backend-firewall.service mtpoxy-refresh.timer
python3 - <<'PY'
import sys
sys.path.insert(0, '/opt/mtpoxy')
from provision import update_backend_firewall
update_backend_firewall()
PY
systemctl enable mtpoxy-panel.service
systemctl restart mtpoxy-panel.service
if command -v ufw >/dev/null && ufw status | grep -q 'Status: active'; then
    ufw allow 9443/tcp
    ufw allow 80/tcp
    ufw allow 443/tcp
fi
echo '[6/6] Проверяем панель'
panel_ready=
for attempt in {1..20}; do
    if curl -kfsS --max-time 3 "https://$public_ip:9443/manage" -o /dev/null; then
        panel_ready=1
        break
    fi
    sleep 1
done
if [[ -z "$panel_ready" ]]; then
    echo 'HTTPS-вход в панель недоступен. Проверьте journalctl -u mtpoxy-panel и TCP 9443.' >&2
    exit 1
fi
systemctl is-active --quiet mtpoxy-panel
echo
echo "Панель: https://$public_ip:9443/manage"
if [[ -f /var/lib/mtpoxy/initial-password ]]; then
    printf 'Пароль: '; cat /var/lib/mtpoxy/initial-password
fi
echo 'Первый вход использует самоподписанный HTTPS-сертификат. Сверьте отпечаток и подтвердите переход в браузере.'
openssl x509 -in /var/lib/mtpoxy/panel-cert.pem -noout -fingerprint -sha256
echo 'В панели укажите токен dynv6 и email, затем нажмите «Создать WEB-proxy».'
