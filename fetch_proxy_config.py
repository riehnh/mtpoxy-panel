#!/usr/bin/env python3
"""Refresh official Telegram routing data, with an existing local SOCKS fallback."""
import json
import os
from pathlib import Path
import subprocess
import tempfile


def configurations():
    yield ''
    try:
        settings = json.loads(Path('/var/lib/mtpoxy/settings.json').read_text())
        if settings.get('socks_host'):
            config = f'proxy = "socks5h://{settings["socks_host"]}:{int(settings["socks_port"])}"\n'
            if settings.get('socks_user'):
                config += 'proxy-user = ' + json.dumps(settings['socks_user'] + ':' + settings.get('socks_password', '')) + '\n'
            yield config
    except (OSError, ValueError, KeyError):
        pass
    # Some existing VPS installations route Telegram through their local Xray.
    # Read credentials locally, send curl config over stdin, never log them.
    try:
        data = json.loads(Path('/usr/local/x-ui/bin/config.json').read_text())
        for inbound in data.get('inbounds', []):
            if inbound.get('protocol') != 'socks' or inbound.get('listen') != '127.0.0.1':
                continue
            config = f'proxy = "socks5h://127.0.0.1:{int(inbound["port"])}"\n'
            accounts = inbound.get('settings', {}).get('accounts', [])
            if accounts:
                config += 'proxy-user = ' + json.dumps(accounts[0]['user'] + ':' + accounts[0]['pass']) + '\n'
            yield config
    except (OSError, ValueError, KeyError):
        pass


def main():
    destination = Path('/etc/mtproxy')
    destination.mkdir(mode=0o750, exist_ok=True)
    for config in configurations():
        with tempfile.TemporaryDirectory() as directory:
            success = True
            for name, endpoint in [('proxy-secret', 'getProxySecret'), ('proxy-multi.conf', 'getProxyConfig')]:
                result = subprocess.run(['curl', '--config', '-', '-4', '-fsS', '--connect-timeout', '8',
                                         '--max-time', '25', '-o', directory + '/' + name,
                                         'https://core.telegram.org/' + endpoint], input=config.encode(),
                                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                if result.returncode:
                    success = False
                    break
            if not success:
                continue
            secret = Path(directory, 'proxy-secret').read_bytes()
            routing = Path(directory, 'proxy-multi.conf').read_bytes()
            if len(secret) != 128 or b'default ' not in routing or b'proxy_for ' not in routing:
                continue
            import grp
            group = grp.getgrnam('mtproxy').gr_gid
            for name in ('proxy-secret', 'proxy-multi.conf'):
                target = destination / name
                temporary = target.with_suffix('.new')
                temporary.write_bytes(Path(directory, name).read_bytes())
                os.chown(temporary, 0, group)
                temporary.chmod(0o640)
                os.replace(temporary, target)
            print('Official Telegram configuration updated')
            return
    raise SystemExit('Не удалось получить конфигурацию core.telegram.org. Проверьте доступ VPS к Telegram.')


if __name__ == '__main__':
    main()
