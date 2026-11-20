"""Read only the fixed loopback metrics endpoint of each managed WEB relay."""
from concurrent.futures import ThreadPoolExecutor
import re
import threading
import time
import urllib.request

FIELDS = {
    'active_sessions': 'tproxy_sessions_live',
    'active_connections': 'tproxy_streams_live',
    'sessions_created_total': 'tproxy_sessions_created_total',
    'connections_opened_total': 'tproxy_streams_opened_total',
    'bytes_up_total': 'tproxy_bytes_up_total',
    'bytes_down_total': 'tproxy_bytes_down_total',
}
LOCK = threading.Lock()
CACHE = {}
TTL = 5


def parse_metrics(body):
    values = {}
    for line in body.splitlines():
        match = re.fullmatch(r'(tproxy_[a-z_]+) ([0-9]+)', line.strip())
        if match:
            values[match[1]] = int(match[2])
    if not all(name in values for name in FIELDS.values()):
        raise ValueError('Incomplete relay metrics')
    return {key: values[name] for key, name in FIELDS.items()}


def read_site(row):
    result = {'id': row['id'], 'hostname': row['hostname'], 'status': row['status'],
              'available': False, 'sampled_at': int(time.time()),
              'unique_ips_24h': None, 'unique_ips_supported': False,
              **{key: None for key in FIELDS}}
    if row['status'] != 'active':
        result['error'] = 'proxy_not_active'
        return result
    try:
        port = row['relay_port']
        if type(port) is not int or not 1 <= port < 65535:
            raise ValueError('Invalid relay port')
        # Ignore environment HTTP proxies: the admin service must remain local.
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
        with opener.open(f'http://127.0.0.1:{port + 1}/metrics', timeout=2) as response:
            body = response.read(65537)
        if len(body) > 65536:
            raise ValueError('Oversized metrics')
        result.update(parse_metrics(body.decode('ascii')))
        result['available'] = True
    except (OSError, ValueError, KeyError):
        result['error'] = 'relay_metrics_unavailable'
    return result


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def snapshot(records):
    """Cache per configuration; serialize refreshes to bound concurrent probes."""
    with LOCK:
        now = time.monotonic()
        pending = []
        keys = []
        for row in records:
            key = (row['id'], row['hostname'], row['status'], row.get('relay_port'))
            keys.append(key)
            if key not in CACHE or now - CACHE[key][0] >= TTL:
                pending.append((key, row))
        if pending:
            with ThreadPoolExecutor(max_workers=min(8, len(pending))) as pool:
                samples = pool.map(read_site, [row for _, row in pending])
                for (key, _), sample in zip(pending, samples):
                    CACHE[key] = (time.monotonic(), sample)
        # Keep the cache bounded even when proxies are removed/reconfigured.
        if len(CACHE) > max(256, len(records) * 2):
            keep = set(keys)
            for key in list(CACHE):
                if key not in keep:
                    del CACHE[key]
        return {'schema_version': 1, 'generated_at': int(time.time()),
                'cache_seconds': TTL, 'counter_scope': 'relay_process_lifetime',
                'resets_on_relay_restart': True,
                'sites': [CACHE[key][1].copy() for key in keys]}
