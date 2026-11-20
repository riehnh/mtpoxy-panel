#!/usr/bin/env python3
import getpass
import secrets
import subprocess
from common import ROOT, load_json, save_json
from server import password_hash


if __name__ == '__main__':
    password = getpass.getpass('Новый пароль панели (от 12 символов): ')
    if len(password) < 12:
        raise SystemExit('Минимум 12 символов')
    if password != getpass.getpass('Повторите пароль: '):
        raise SystemExit('Пароли не совпали')
    settings = load_json(ROOT / 'settings.json')
    settings['salt'] = secrets.token_hex(16)
    settings['password_hash'] = password_hash(password, settings['salt'])
    save_json(ROOT / 'settings.json', settings)
    (ROOT / 'initial-password').unlink(missing_ok=True)
    subprocess.run(['systemctl', 'restart', 'mtpoxy-panel'], check=True)
    print('Пароль изменён, старые сессии завершены')
