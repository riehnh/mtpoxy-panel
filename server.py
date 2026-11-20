#!/usr/bin/env python3
"""Authenticated web UI and public recipe sites; Python 3.10+, standard library."""
import argparse
from collections import defaultdict
import getpass
import hashlib
import hmac
from http import HTTPStatus
from http.cookies import SimpleCookie
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import ipaddress
import json
import mimetypes
import os
from pathlib import Path
import re
import secrets
import ssl
import threading
import time
import urllib.parse
import urllib.request

from common import APP, ROOT, load_json, save_json
import provision
import proxy_stats

SESSIONS = {}
ATTEMPTS = defaultdict(list)
AUTH_LOCK = threading.Lock()


def password_hash(password, salt):
    return hashlib.pbkdf2_hmac('sha256', password.encode(), bytes.fromhex(salt), 310000).hex()


class Handler(BaseHTTPRequestHandler):
    server_version = 'MTPoxy'
    protocol_version = 'HTTP/1.1'

    def setup(self):
        super().setup()
        self.connection.settimeout(15)

    def log_message(self, *_):
        # URL query strings and request bodies may contain credentials.
        pass

    def reply(self, body, status=200, content_type='application/json; charset=utf-8', cookie=None, download=None):
        if not isinstance(body, bytes):
            body = json.dumps(body, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header('Content-Type', content_type)
        self.send_header('Content-Length', str(len(body)))
        if download:
            self.send_header('Content-Disposition', f'attachment; filename="{download}"')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Referrer-Policy', 'same-origin')
        self.send_header('Cache-Control', 'no-store' if self.path.startswith(('/manage', '/mange')) else 'public, max-age=120')
        self.send_header('Content-Security-Policy', "default-src 'self'; img-src 'self' https://cdn.dummyjson.com; style-src 'self'; script-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")
        if cookie:
            self.send_header('Set-Cookie', cookie)
        self.end_headers()
        self.wfile.write(body)

    def session(self):
        try:
            cookies = SimpleCookie(self.headers.get('Cookie', ''))
            key = cookies['mtpoxy_session'].value
        except (KeyError, ValueError):
            return None
        with AUTH_LOCK:
            session = SESSIONS.get(key)
            if session and session['expires'] > time.time():
                return session
            SESSIONS.pop(key, None)
            return None

    def allowed_panel_host(self):
        hostname = self.headers.get('Host', '').split(':')[0].lower()
        settings = load_json(ROOT / 'settings.json', {})
        expected = settings.get('panel_hostname', '')
        return hostname in ('localhost', '127.0.0.1', settings.get('public_ip')) or bool(expected and hostname == expected)

    def stats_authorized(self):
        authorization = self.headers.get('Authorization', '')
        if authorization:
            if not authorization.startswith('Bearer ') or len(authorization) > 256:
                return False
            expected = load_json(ROOT / 'settings.json', {}).get('stats_key_hash', '')
            actual = hashlib.sha256(authorization[7:].encode()).hexdigest()
            return bool(expected) and hmac.compare_digest(expected, actual)
        return bool(self.session())

    def do_GET(self):
        path = urllib.parse.urlsplit(self.path).path
        # Keep the requested /mange spelling as an entry point; assets/API use /manage.
        if path in ('/mange', '/mange/'):
            path = '/manage'
        if path == '/' and self.allowed_panel_host() and self.headers.get('Host', '').split(':')[0] == load_json(ROOT / 'settings.json').get('public_ip'):
            return self.asset('panel.html')
        if path.startswith('/manage'):
            if not self.allowed_panel_host():
                return self.reply({'error': 'Not found'}, 404)
            session = self.session()
            if path == '/manage/api/stats' or path.startswith('/manage/api/stats/'):
                if not self.stats_authorized():
                    return self.reply({'error': 'Нужен API-ключ статистики или вход в панель'}, 401)
                records = provision.sites()
                if path != '/manage/api/stats':
                    identifier = path.removeprefix('/manage/api/stats/')
                    if not re.fullmatch(r'[1-9][0-9]{0,18}', identifier):
                        return self.reply({'error': 'Прокси не найден'}, 404)
                    records = [row for row in records if row['id'] == int(identifier)]
                    if not records:
                        return self.reply({'error': 'Прокси не найден'}, 404)
                return self.reply(proxy_stats.snapshot(records))
            if path == '/manage/download/android.apk':
                if not session:
                    return self.reply({'error': 'Войдите в панель'}, 401)
                apk = APP / 'downloads' / 'MTPoxy.apk'
                if not apk.is_file():
                    return self.reply({'error': 'APK отсутствует. Обновите установку из GitHub.'}, 404)
                return self.reply(apk.read_bytes(), content_type='application/vnd.android.package-archive', download='Yoxy.apk')
            if path == '/manage/api/session':
                return self.reply({'authenticated': bool(session), 'csrf': session['csrf'] if session else None})
            if path == '/manage/api/state':
                if not session:
                    return self.reply({'error': 'Войдите в панель'}, 401)
                settings = load_json(ROOT / 'settings.json')
                return self.reply({'sites': provision.public_sites(), 'busy': provision.OPERATION.locked(),
                                   'settings': {key: settings.get(key, '') for key in ('email', 'public_ip', 'panel_hostname', 'socks_host', 'socks_port', 'socks_user')},
                                   'token_configured': bool(settings.get('dynv6_token'))})
            if path in ('/manage', '/manage/'):
                return self.asset('panel.html')
            if path in ('/manage/panel.js', '/manage/panel.css', '/manage/logo.svg'):
                return self.asset(path.rsplit('/', 1)[1])
            return self.reply({'error': 'Not found'}, 404)
        hostname = self.headers.get('Host', '').split(':')[0].lower()
        if hostname not in [row['hostname'] for row in provision.sites()]:
            return self.reply({'error': 'Unknown site'}, 404)
        if path == '/api/recipes':
            return self.reply(load_json(ROOT / 'recipes.json', {'recipes': []}))
        if path == '/':
            return self.asset('recipes.html')
        if path in ('/recipes.js', '/recipes.css', '/logo.svg'):
            return self.asset(path[1:])
        return self.reply(b'Page not found', 404, 'text/plain')

    def asset(self, name):
        path = APP / 'static' / name
        content_type = mimetypes.guess_type(str(path))[0] or 'application/octet-stream'
        return self.reply(path.read_bytes(), content_type=content_type + '; charset=utf-8')

    def do_POST(self):
        path = urllib.parse.urlsplit(self.path).path
        if not path.startswith('/manage/api/') or not self.allowed_panel_host():
            return self.reply({'error': 'Not found'}, 404)
        host = self.headers.get('Host', '')
        local = host.split(':')[0] in ('localhost', '127.0.0.1')
        expected_origin = ('http' if local else 'https') + '://' + host
        if self.headers.get('Origin') != expected_origin:
            return self.reply({'error': 'Неверный Origin'}, 403)
        try:
            length = int(self.headers.get('Content-Length', '0'))
            if length < 1 or length > 16000:
                return self.reply({'error': 'Неверный размер запроса'}, 400)
            data = json.loads(self.rfile.read(length))
            if not isinstance(data, dict):
                raise ValueError('Ожидается JSON объект')
            if path == '/manage/api/login':
                return self.login(data, local)
            session = self.session()
            if not session:
                return self.reply({'error': 'Войдите в панель'}, 401)
            if not hmac.compare_digest(self.headers.get('X-CSRF-Token', ''), session['csrf']):
                return self.reply({'error': 'Обновите страницу и повторите запрос'}, 403)
            if path == '/manage/api/logout':
                with AUTH_LOCK:
                    for key, value in list(SESSIONS.items()):
                        if value is session:
                            del SESSIONS[key]
                return self.reply({'ok': True}, cookie='mtpoxy_session=; Path=/manage; HttpOnly; SameSite=Strict; Max-Age=0')
            if path == '/manage/api/statskey':
                key = None if data.get('revoke') is True else secrets.token_urlsafe(32)
                with provision.LOCK:
                    settings = load_json(ROOT / 'settings.json')
                    if key:
                        settings['stats_key_hash'] = hashlib.sha256(key.encode()).hexdigest()
                    else:
                        settings.pop('stats_key_hash', None)
                    save_json(ROOT / 'settings.json', settings)
                return self.reply({'key': key, 'scope': 'stats:read', 'revoked': key is None})
            if path == '/manage/api/create':
                retry = data.get('retry_id')
                if retry is not None and type(retry) is not int:
                    raise ValueError('Неверный номер записи')
                return self.reply({'id': provision.start(str(data.get('slug', '')), retry)})
            if path == '/manage/api/settings':
                if provision.OPERATION.locked():
                    raise ValueError('Сначала дождитесь завершения создания')
                with provision.LOCK:
                    settings = load_json(ROOT / 'settings.json')
                    email = str(data.get('email', settings.get('email', ''))).strip()
                    if not re.fullmatch(r'[A-Za-z0-9._+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}', email):
                        raise ValueError('Укажите корректный email для HTTPS-сертификатов')
                    token = str(data.get('token', '')).strip()
                    if token:
                        if not re.fullmatch(r'[A-Za-z0-9._\-]{10,200}', token):
                            raise ValueError('Неверный формат токена')
                        settings['dynv6_token'] = token
                    if 'socks_host' in data:
                        socks_host = str(data['socks_host']).strip()
                        if socks_host:
                            if not re.fullmatch(r'[A-Za-z0-9.\-]{1,253}', socks_host):
                                raise ValueError('Укажите IP или имя SOCKS5-сервера')
                            port = int(data.get('socks_port') or 1080)
                            if not 1 <= port <= 65535:
                                raise ValueError('Неверный порт SOCKS5')
                            settings.update(socks_host=socks_host, socks_port=port,
                                            socks_user=str(data.get('socks_user', '')))
                            if data.get('socks_password'):
                                settings['socks_password'] = str(data['socks_password'])
                        else:
                            for key in ('socks_host', 'socks_port', 'socks_user', 'socks_password'):
                                settings.pop(key, None)
                    settings['email'] = email
                    save_json(ROOT / 'settings.json', settings)
                return self.reply({'ok': True})
            return self.reply({'error': 'Not found'}, 404)
        except (ValueError, KeyError) as error:
            self.reply({'error': str(error)}, 400)
        except Exception:
            self.reply({'error': 'Внутренняя ошибка. Проверьте journalctl -u mtpoxy-panel'}, 500)

    def login(self, data, local):
        address = self.client_address[0]
        if address == '127.0.0.1':
            address = self.headers.get('X-Forwarded-For', address).split(',')[0].strip()
        now = time.time()
        with AUTH_LOCK:
            for key in list(ATTEMPTS):
                ATTEMPTS[key] = [stamp for stamp in ATTEMPTS[key] if stamp > now - 900]
                if not ATTEMPTS[key]:
                    del ATTEMPTS[key]
            recent = ATTEMPTS[address]
            if len(recent) >= 8 or len(ATTEMPTS) > 1000:
                return self.reply({'error': 'Слишком много попыток. Повторите через 15 минут.'}, 429)
            recent.append(now)
        settings = load_json(ROOT / 'settings.json')
        password = str(data.get('password', ''))
        verified = hmac.compare_digest(password_hash(password, settings['salt']), settings['password_hash'])
        if not verified:
            return self.reply({'error': 'Неверный пароль'}, 401)
        key = secrets.token_urlsafe(32)
        session = {'csrf': secrets.token_urlsafe(24), 'expires': now + 28800}
        with AUTH_LOCK:
            for expired in [key for key, value in SESSIONS.items() if value['expires'] <= now]:
                del SESSIONS[expired]
            if len(SESSIONS) >= 100:
                SESSIONS.pop(next(iter(SESSIONS)))
            SESSIONS[key] = session
            ATTEMPTS.pop(address, None)
        cookie = f'mtpoxy_session={key}; Path=/manage; HttpOnly; SameSite=Strict; Max-Age=28800'
        if not local:
            cookie += '; Secure'
        return self.reply({'ok': True, 'csrf': session['csrf']}, cookie=cookie)


def recipe_refresh_loop():
    while True:
        try:
            provision.refresh_recipes()
        except Exception:
            pass  # Retain the last successful catalog if the external API is down.
        time.sleep(86400)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('command', choices=['serve', 'init', 'create'])
    parser.add_argument('--slug', default='')
    parser.add_argument('--json-stdin', action='store_true')
    args = parser.parse_args()
    ROOT.mkdir(parents=True, exist_ok=True, mode=0o700)
    if args.command == 'init':
        if (ROOT / 'settings.json').exists():
            raise SystemExit('Настройки уже существуют. Измените их через панель.')
        if args.json_stdin:
            import sys
            data = json.load(sys.stdin)
        else:
            data = {'public_ip': input('IPv4 этого VPS: ').strip(),
                    'email': input('Email для HTTPS: ').strip(),
                    'dynv6_token': getpass.getpass('API-токен dynv6: ').strip(),
                    'password': getpass.getpass('Пароль панели (от 12 символов): ')}
        ipaddress.IPv4Address(data['public_ip'])
        if len(data['password']) < 12:
            raise SystemExit('Пароль должен содержать минимум 12 символов')
        if data.get('email') and not re.fullmatch(r'[A-Za-z0-9._+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}', data['email']):
            raise SystemExit('Некорректный email')
        salt = secrets.token_hex(16)
        settings = {key: data[key] for key in ('public_ip', 'email', 'dynv6_token')}
        settings.update(salt=salt, password_hash=password_hash(data['password'], salt), panel_hostname='')
        save_json(ROOT / 'settings.json', settings)
        print('Настройки сохранены')
    elif args.command == 'create':
        identifier = provision.start(args.slug)
        while provision.OPERATION.locked():
            time.sleep(2)
        row = next(row for row in provision.sites() if row['id'] == identifier)
        if row['status'] != 'active':
            raise SystemExit(row['message'])
        print('Панель: https://' + row['hostname'] + '/manage')
        print('WEB-proxy: ' + row['link'])
    else:
        with provision.LOCK:
            rows = provision.sites()
            for row in rows:
                if row['status'] == 'installing':
                    row.update(status='error', message='Установка прервана перезапуском панели. Нажмите «Повторить».')
            save_json(ROOT / 'sites.json', rows)
        threading.Thread(target=recipe_refresh_loop, daemon=True).start()
        server = ThreadingHTTPServer(('127.0.0.1', 9080), Handler)
        server.daemon_threads = True
        if (ROOT / 'panel-cert.pem').exists():
            secure_server = ThreadingHTTPServer(('0.0.0.0', 9443), Handler)
            context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
            context.minimum_version = ssl.TLSVersion.TLSv1_2
            context.load_cert_chain(str(ROOT / 'panel-cert.pem'), str(ROOT / 'panel-key.pem'))
            secure_server.socket = context.wrap_socket(secure_server.socket, server_side=True)
            secure_server.daemon_threads = True
            threading.Thread(target=secure_server.serve_forever, daemon=True).start()
        server.serve_forever()


if __name__ == '__main__':
    main()
