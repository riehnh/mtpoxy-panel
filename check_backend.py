#!/usr/bin/env python3
"""Check that a managed backend relays a real req_pq_multi to Telegram."""
import argparse
import concurrent.futures
import hashlib
import json
import secrets
import socket
import struct
import subprocess
import time

from common import ROOT, load_json


def aes(data, key, iv):
    result = subprocess.run(['openssl', 'enc', '-aes-256-ctr', '-nosalt',
                             '-K', key.hex(), '-iv', iv.hex()], input=data,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True, timeout=5)
    return result.stdout


def check(item, dc=2):
    secret = bytes.fromhex(item['secret'])
    while True:
        header = bytearray(secrets.token_bytes(64))
        if header[0] != 0xef and header[4:8] != bytes(4) and header[:4] not in (b'HEAD', b'POST', b'GET ', b'OPTI', b'\xee' * 4, b'\xdd' * 4):
            break
    reverse = bytes(header[8:56])[::-1]
    key = hashlib.sha256(bytes(header[8:40]) + secret).digest()
    iv = bytes(header[40:56])
    incoming_key = hashlib.sha256(reverse[:32] + secret).digest()
    incoming_iv = reverse[32:48]
    header[56:60] = b'\xef' * 4
    header[60:62] = struct.pack('<h', dc)
    nonce = secrets.token_bytes(16)
    query = struct.pack('<I', 0xbe7e8ef1) + nonce
    message = bytes(8) + struct.pack('<QI', (int(time.time()) << 32), len(query)) + query
    wire = bytes([len(message) // 4]) + message
    encrypted = aes(bytes(header) + wire, key, iv)
    with socket.create_connection(('127.0.0.1', item['backend_port']), timeout=8) as connection:
        connection.sendall(bytes(header[:56]) + encrypted[56:])
        response = connection.recv(4096)
        if not response:
            raise RuntimeError('Backend closed the connection')
        clear = aes(response, incoming_key, incoming_iv)
        while len(clear) < 41:
            chunk = connection.recv(4096)
            if not chunk:
                raise RuntimeError('Backend closed before a complete Telegram response')
            response += chunk
            clear = aes(response, incoming_key, incoming_iv)
        offset = 1 if clear[0] < 0x7f else 4
        constructor = struct.unpack_from('<I', clear, offset + 20)[0]
        received_nonce = clear[offset + 24:offset + 40]
        if constructor != 0x05162463 or received_nonce != nonce:
            raise RuntimeError('Invalid Telegram resPQ response')
    return True


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('site_id', type=int)
    args = parser.parse_args()
    item = next(row for row in load_json(ROOT / 'sites.json', []) if row['id'] == args.site_id)
    def probe(dc):
        for attempt in range(2):
            try:
                check(item, dc)
                return dc
            except (OSError, RuntimeError, subprocess.SubprocessError):
                pass
        return None
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
        reachable = [dc for dc in pool.map(probe, range(1, 6)) if dc]
    if not reachable:
        raise SystemExit('Telegram не ответил. Проверьте исходящее соединение или укажите другой SOCKS5 в настройках.')
    print(json.dumps({'reachable_dcs': reachable, 'unreachable_dcs': [dc for dc in range(1, 6) if dc not in reachable]}))
