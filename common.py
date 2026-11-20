"""Local state and bounded HTTP requests for the MTPoxy control panel."""
import json
import http.client
import os
from pathlib import Path
import tempfile
import socket
import urllib.error
import urllib.request

ROOT = Path(os.environ.get('MTPOXY_STATE', '/var/lib/mtpoxy'))
APP = Path(__file__).resolve().parent


def connect_ipv4(address, timeout=25, source_address=None, **kwargs):
    """Use the public IPv4 path on VPSes with incomplete IPv6 connectivity."""
    last_error = None
    for family, kind, protocol, _, target in socket.getaddrinfo(address[0], address[1], socket.AF_INET, socket.SOCK_STREAM):
        connection = socket.socket(family, kind, protocol)
        connection.settimeout(timeout)
        try:
            if source_address:
                connection.bind(source_address)
            connection.connect(target)
            return connection
        except OSError as error:
            last_error = error
            connection.close()
    raise last_error or OSError('IPv4 address not found')


class IPv4HTTPSConnection(http.client.HTTPSConnection):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self._create_connection = connect_ipv4


class IPv4HTTPSHandler(urllib.request.HTTPSHandler):
    def https_open(self, req):
        return self.do_open(IPv4HTTPSConnection, req, context=self._context)


def save_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    descriptor, temporary = tempfile.mkstemp(dir=path.parent)
    try:
        with os.fdopen(descriptor, 'w', encoding='utf-8') as stream:
            json.dump(value, stream, ensure_ascii=False, indent=2)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def load_json(path, default=None):
    try:
        return json.loads(Path(path).read_text(encoding='utf-8'))
    except FileNotFoundError:
        return default


def request(url, data=None, token=None, method=None):
    headers = {'User-Agent': 'MTPoxy-Panel/1.0', 'Accept': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if data is not None:
        data = json.dumps(data).encode()
        headers['Content-Type'] = 'application/json'
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    opener = urllib.request.build_opener(IPv4HTTPSHandler())
    with opener.open(req, timeout=25) as response:
        body = response.read(2 * 1024 * 1024 + 1)
        if len(body) > 2 * 1024 * 1024:
            raise ValueError('Ответ API превышает 2 МБ')
        return json.loads(body)
