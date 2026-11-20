"""Provision local services. Only validated names and integer ports reach tools."""
import base64
import hashlib
import http.client
import ipaddress
import json
import os
from pathlib import Path
import re
import secrets
import socket
import ssl
import sys
import subprocess
import threading
import time
import urllib.error
import urllib.parse

from common import APP, ROOT, load_json, request, save_json

LOCK = threading.RLock()
OPERATION = threading.Lock()
HOST_PATTERN = re.compile(r'[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.dynv6\.net\Z')


def run(*args, timeout=90):
    result = subprocess.run(args, text=True, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, timeout=timeout)
    if result.returncode:
        # Commands never contain tokens or passwords. Keep profile contents out of errors.
        raise RuntimeError(f'{args[0]} завершился с кодом {result.returncode}: {result.stdout[-1200:]}')
    return result.stdout


def sites():
    return load_json(ROOT / 'sites.json', [])


def update(identifier, **changes):
    with LOCK:
        records = sites()
        item = next(row for row in records if row['id'] == identifier)
        item.update(changes)
        save_json(ROOT / 'sites.json', records)
        return item.copy()


def public_sites():
    with LOCK:
        return [{key: value for key, value in row.items() if key != 'secret'} for row in sites()]


def refresh_recipes():
    recipes = []
    for offset in range(0, 24, 6):
        try:
            data = request(f'https://dummyjson.com/recipes?limit=6&skip={offset}')
            batch = data.get('recipes', [])
            if not batch or not all(isinstance(row.get('name'), str) for row in batch):
                raise ValueError('API рецептов вернул пустой каталог')
            recipes.extend(batch)
        except (OSError, ValueError):
            if not recipes:
                raise
            break
    if not recipes:
        raise ValueError('API рецептов вернул пустой каталог')
    save_json(ROOT / 'recipes.json', {'recipes': recipes, 'updated': int(time.time())})


def dns_create(hostname, settings):
    api = 'https://dynv6.com/api/v2/zones'
    token = settings['dynv6_token']
    try:
        zone = request(api + '/by-name/' + hostname, token=token)
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
        zone = request(api, {'name': hostname, 'ipv4address': settings['public_ip']}, token)
    if zone.get('ipv4address') != settings['public_ip']:
        request(api + '/' + str(zone['id']), {'ipv4address': settings['public_ip']}, token, 'PATCH')
    return zone['id']


def wait_dns(hostname, address, report, seconds=180):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            query = urllib.parse.urlencode({'name': hostname, 'type': 'A'})
            result = request('https://dns.google/resolve?' + query)
            answers = [row.get('data') for row in result.get('Answer', []) if row.get('type') == 1]
            if result.get('Status') == 0 and address in answers:
                return
        except (OSError, ValueError):
            pass
        report('Ожидаем публикации DNS — повторная проверка через 10 секунд')
        time.sleep(10)
    raise RuntimeError('dynv6 пока не публикует IP в DNS. Имя сохранено. Нажмите «Повторить» позже.')


def write(path, text, mode=0o644):
    destination = Path(path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(destination.suffix + '.new')
    temporary.write_text(text, encoding='utf-8')
    temporary.chmod(mode)
    os.replace(temporary, destination)


def verify_https(hostname):
    """Verify the public certificate through the local TLS terminator.

    A connection from the VPS to its own public IPv4 bypasses prerouting NAT.
    Use loopback with the real TLS server name to check the same certificate.
    """
    context = ssl.create_default_context()
    connection = http.client.HTTPConnection('127.0.0.1', 8443, timeout=15)
    raw = socket.create_connection(('127.0.0.1', 8443), timeout=15)
    try:
        connection.sock = context.wrap_socket(raw, server_hostname=hostname)
        connection.request('GET', '/', headers={'Host': hostname})
        response = connection.getresponse()
        return response.status == 200 and b'Recipe' in response.read(8192)
    finally:
        connection.close()
        raw.close()


def service_units(item, settings):
    identifier = item['id']
    directory = Path('/etc/mtpoxy/sites') / str(identifier)
    directory.mkdir(parents=True, exist_ok=True)
    Path('/etc/mtpoxy').chmod(0o755)
    directory.parent.chmod(0o755)
    secret = item['secret']
    save_json(directory / 'profiles.json', {'profiles': [{
        'name': 'default', 'secret': secret,
        'backend': f"127.0.0.1:{item['backend_port']}", 'carrier_mode': 'websocket'}]})
    save_json(directory / 'config.json', {
        'public_hostname': item['hostname'], 'base_path': '',
        'listen': f"127.0.0.1:{item['relay_port']}",
        'admin_listen': f"127.0.0.1:{item['relay_port'] + 1}",
        'public_upstream': 'http://127.0.0.1:9080',
        'profiles_file': str(directory / 'profiles.json'),
        'token_key_file': str(directory / 'token.key')})
    if not (directory / 'token.key').exists():
        (directory / 'token.key').write_bytes(secrets.token_bytes(32))
        (directory / 'token.key').chmod(0o600)
    # Official MTProxy cannot bind its public listener to loopback. A dedicated
    # nftables chain blocks all managed backend/statistics ports externally.
    backend_command = f"/opt/MTProxy/objs/bin/mtproto-proxy -u mtproxy -p {item['backend_port'] + 1} -H {item['backend_port']} -S {secret} --aes-pwd /etc/mtproxy/proxy-secret /etc/mtproxy/proxy-multi.conf -M 1 -C 1024"
    backend_user = ''
    if not settings.get('socks_host') and not Path('/etc/mtproxy/proxy-multi.conf').exists():
        run(sys.executable, str(APP / 'fetch_proxy_config.py'), timeout=90)
    if settings.get('socks_host'):
        import grp
        group = grp.getgrnam('mtproxy').gr_gid
        os.chown(directory, 0, group)
        directory.chmod(0o750)
        configuration = {
            'PORT': item['backend_port'], 'USERS': {'web': secret},
            'MODES': {'classic': True, 'secure': True, 'tls': False},
            'USE_MIDDLE_PROXY': False, 'PREFER_IPV6': False, 'FAST_MODE': False,
            'MASK': False, 'LISTEN_ADDR_IPV4': '127.0.0.1', 'LISTEN_ADDR_IPV6': None,
            'SOCKS5_HOST': settings['socks_host'], 'SOCKS5_PORT': settings['socks_port'],
            'SOCKS5_USER': settings.get('socks_user') or None,
            'SOCKS5_PASS': settings.get('socks_password') or None,
        }
        write(directory / 'backend.py', '\n'.join(f'{key} = {value!r}' for key, value in configuration.items()) + '\n', 0o640)
        os.chown(directory / 'backend.py', 0, group)
        backend_command = f'/usr/bin/python3 /opt/mtpoxy-python-backend/mtprotoproxy.py {directory}/backend.py'
        backend_user = 'User=mtproxy\nGroup=mtproxy'
    backend = f'''[Unit]
Description=MTPoxy Telegram backend {identifier}
After=network-online.target mtpoxy-backend-firewall.service
Requires=mtpoxy-backend-firewall.service
[Service]
{backend_user}
ExecStart={backend_command}
Restart=on-failure
RestartSec=3
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
[Install]
WantedBy=multi-user.target
'''
    relay = f'''[Unit]
Description=MTPoxy WEB relay {identifier}
After=mtpoxy-backend-{identifier}.service mtpoxy-panel.service
[Service]
ExecStart=/usr/local/bin/tproxy-server -config {directory}/config.json
Restart=on-failure
RestartSec=3
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
RestrictAddressFamilies=AF_INET AF_INET6 AF_UNIX
[Install]
WantedBy=multi-user.target
'''
    write(f'/etc/systemd/system/mtpoxy-backend-{identifier}.service', backend, 0o600)
    write(f'/etc/systemd/system/mtpoxy-relay-{identifier}.service', relay)
    run('/usr/local/bin/tproxy-server', '-config', str(directory / 'config.json'), '-check')


def allocate_ports(records):
    """Find a free four-port block, retaining allocations made by older versions."""
    reserved = set()
    for row in records:
        for key in ('relay_port', 'backend_port'):
            if key in row:
                reserved.update((row[key], row[key] + 1))
    for base in range(10000, 65532, 4):
        block = range(base, base + 4)
        if any(port in reserved for port in block):
            continue
        probes = []
        try:
            for port in block:
                probe = socket.socket()
                probes.append(probe)
                probe.bind(('0.0.0.0', port))
            return base + 2, base
        except OSError:
            continue
        finally:
            for probe in probes:
                probe.close()
    raise ValueError('На VPS закончились свободные локальные порты')


def update_backend_firewall():
    ports = sorted({port for row in sites() for port in (row['backend_port'], row['backend_port'] + 1)})
    values = ', '.join(map(str, ports))
    rule = f'iifname != "lo" tcp dport {{ {values} }} drop' if ports else ''
    persistent = f'''table inet mtpoxy_backend {{
 chain input {{
  type filter hook input priority -10; policy accept;
  {rule}
 }}
}}
'''
    write('/etc/mtpoxy/backend.nft', persistent)
    # nft applies the flush and replacement in one atomic transaction.
    write('/etc/mtpoxy/backend-update.nft',
          'flush chain inet mtpoxy_backend input\n' +
          (f'add rule inet mtpoxy_backend input {rule}\n' if rule else ''))
    run('/usr/sbin/nft', '-c', '-f', '/etc/mtpoxy/backend-update.nft')
    run('/usr/sbin/nft', '-f', '/etc/mtpoxy/backend-update.nft')


def routing_files(records, settings):
    active = [row for row in records if row['status'] in ('active', 'installing')]
    caddy = '''{
 email EMAIL
 admin 127.0.0.1:2029
 https_port 8443
 auto_https disable_redirects
 servers {
  protocols h1 h2
  timeouts {
   read_header 10s
   read_body 60s
  }
 }
}
'''.replace('EMAIL', settings['email'])
    for row in active:
        host = row['hostname']
        caddy += f'''
{host} {{
 bind 127.0.0.1
 encode zstd gzip
 reverse_proxy 127.0.0.1:{row['relay_port']} {{
  transport http {{
   response_header_timeout 40s
  }}
 }}
}}
http://{host} {{
 bind 0.0.0.0
 redir https://{{host}}{{uri}} permanent
}}
'''
    hosts = ' '.join(row['hostname'] for row in active)
    haproxy = f'''global
 user haproxy
 group haproxy
 maxconn 4096
defaults
 mode tcp
 timeout connect 5s
 timeout client 1h
 timeout server 1h
frontend telegram_shared
 bind 0.0.0.0:1443
 tcp-request inspect-delay 5s
 tcp-request content accept if {{ req.ssl_hello_type 1 }}
 use_backend web_proxy if {{ req.ssl_sni -i {hosts} }}
 default_backend existing_mtg
backend web_proxy
 server web 127.0.0.1:8443
backend existing_mtg
 server mtg 127.0.0.1:443
'''
    return caddy, haproxy


def apply_routes(settings):
    caddy, router = routing_files(sites(), settings)
    write('/etc/mtpoxy/Caddyfile.candidate', caddy)
    write('/etc/mtpoxy/haproxy.candidate', router)
    run('/usr/local/bin/caddy', 'validate', '--config', '/etc/mtpoxy/Caddyfile.candidate', '--adapter', 'caddyfile')
    run('/usr/sbin/haproxy', '-c', '-f', '/etc/mtpoxy/haproxy.candidate')
    backups = {}
    for path, content in [('/etc/mtpoxy/Caddyfile', caddy), ('/etc/mtpoxy/haproxy.cfg', router)]:
        destination = Path(path)
        backups[path] = destination.read_bytes() if destination.exists() else None
        write(path, content)
    try:
        for service in ('mtpoxy-caddy', 'mtpoxy-router'):
            if subprocess.run(['systemctl', 'is-active', '--quiet', service]).returncode == 0:
                run('systemctl', 'reload', service)
            else:
                run('systemctl', 'enable', '--now', service)
        run('systemctl', 'enable', '--now', 'mtpoxy-routing')
    except Exception:
        for path, previous in backups.items():
            if previous is not None:
                Path(path).write_bytes(previous)
        for service in ('mtpoxy-caddy', 'mtpoxy-router'):
            subprocess.run(['systemctl', 'reload', service], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        raise


def provision(identifier):
    settings = load_json(ROOT / 'settings.json')
    def report(message):
        update(identifier, message=message, updated=int(time.time()))
    try:
        item = update(identifier, status='installing', message='Создаём поддомен dynv6')
        zone = dns_create(item['hostname'], settings)
        update(identifier, zone_id=zone)
        wait_dns(item['hostname'], settings['public_ip'], report)
        report('Загружаем каталог рецептов')
        if not load_json(ROOT / 'recipes.json'):
            refresh_recipes()
        report('Создаём отдельные службы WEB-proxy')
        service_units(item, settings)
        update_backend_firewall()
        run('systemctl', 'daemon-reload')
        for name in ('backend', 'relay'):
            run('systemctl', 'enable', f'mtpoxy-{name}-{identifier}')
            run('systemctl', 'restart', f'mtpoxy-{name}-{identifier}')
        deadline = time.monotonic() + 90
        while True:
            try:
                import urllib.request
                with socket.create_connection(('127.0.0.1', item['backend_port']), timeout=3):
                    pass
                with urllib.request.urlopen(f"http://127.0.0.1:{item['relay_port'] + 1}/readyz", timeout=5) as response:
                    if response.status != 200:
                        raise OSError('Relay is not ready')
                break
            except (OSError, ValueError):
                if time.monotonic() > deadline:
                    raise RuntimeError('WEB relay не готов. Проверьте журнал службы.')
                time.sleep(2)
        report('Проверяем реальный ответ Telegram')
        checks = json.loads(run(sys.executable, str(APP / 'check_backend.py'), str(identifier), timeout=40))
        report('Выпускаем HTTPS-сертификат')
        apply_routes(settings)
        deadline = time.monotonic() + 210
        url = 'https://' + item['hostname'] + '/'
        while True:
            try:
                if verify_https(item['hostname']):
                    break
            except OSError:
                pass
            if time.monotonic() > deadline:
                raise RuntimeError('HTTPS пока недоступен. Проверьте DNS и входящие TCP 80/443, затем повторите.')
            time.sleep(5)
        link = 'https://t.me/webproxy?' + urllib.parse.urlencode({'server': item['hostname'], 'secret': item['secret']})
        warning = ''
        if checks['unreachable_dcs']:
            warning = 'Ответила только часть серверов Telegram. Если подключение не работает, смените исходящий SOCKS5.'
        update(identifier, status='active', message='Готово', link=link, site_url=url,
               checks=checks, warning=warning, updated=int(time.time()))
        with LOCK:
            settings = load_json(ROOT / 'settings.json')
            if not settings.get('panel_hostname'):
                settings['panel_hostname'] = item['hostname']
                save_json(ROOT / 'settings.json', settings)
    except Exception as error:
        update(identifier, status='error', message=f'{type(error).__name__}: {error}', updated=int(time.time()))
    finally:
        OPERATION.release()


def start(slug='', retry_id=None):
    settings = load_json(ROOT / 'settings.json', {})
    if not settings.get('dynv6_token') or not settings.get('email'):
        raise ValueError('Сначала сохраните токен dynv6 и email в настройках')
    if subprocess.run(['systemctl', 'is-active', '--quiet', 'mtpoxy-caddy']).returncode != 0:
        with socket.socket() as probe:
            try:
                probe.bind(('0.0.0.0', 80))
            except OSError:
                raise ValueError('Порт 80 занят веб-сервером. Нужна интеграция с существующим HTTPS-сервером.')
    if not OPERATION.acquire(blocking=False):
        raise ValueError('Создание уже выполняется. Дождитесь завершения.')
    try:
        with LOCK:
            records = sites()
            if retry_id is not None:
                item = next((row for row in records if row['id'] == retry_id), None)
                if item is None or item['status'] != 'error':
                    raise ValueError('Повтор доступен для записи с ошибкой')
                item['status'] = 'installing'
            else:
                slug = slug.strip().lower() or ('recipebook-' + secrets.token_hex(3))
                hostname = slug + '.dynv6.net'
                if not HOST_PATTERN.fullmatch(hostname):
                    raise ValueError('Имя должно содержать латинские буквы, цифры и дефисы, до 63 символов')
                if any(row['hostname'] == hostname for row in records):
                    raise ValueError('Это имя уже есть в панели')
                identifier = max((row['id'] for row in records), default=0) + 1
                relay_port, backend_port = allocate_ports(records)
                item = {'id': identifier, 'hostname': hostname, 'title': 'Recipe Book',
                        'secret': secrets.token_hex(16), 'relay_port': relay_port,
                        'backend_port': backend_port, 'status': 'installing',
                        'message': 'Подготовка', 'created': int(time.time())}
                records.append(item)
            save_json(ROOT / 'sites.json', records)
            threading.Thread(target=provision, args=(item['id'],), daemon=True).start()
            return item['id']
    except Exception:
        OPERATION.release()
        raise
