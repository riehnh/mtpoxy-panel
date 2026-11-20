import http.client
import json
import os
from pathlib import Path
import tempfile
import threading
import unittest
from unittest.mock import patch

temporary = tempfile.TemporaryDirectory()
os.environ['MTPOXY_STATE'] = temporary.name
import common
import provision
import server


class PanelTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.settings = {'public_ip': '203.0.113.12', 'email': 'test@example.com',
                        'dynv6_token': 'DO-NOT-RETURN-THIS-TOKEN', 'panel_hostname': '',
                        'salt': 'a1' * 16}
        cls.settings['password_hash'] = server.password_hash('correct-test-password', cls.settings['salt'])
        common.save_json(common.ROOT / 'settings.json', cls.settings)
        cls.http = server.ThreadingHTTPServer(('127.0.0.1', 0), server.Handler)
        cls.port = cls.http.server_port
        threading.Thread(target=cls.http.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.http.shutdown()
        cls.http.server_close()
        temporary.cleanup()

    def call(self, path, data=None, cookie=None, csrf=None, origin=True):
        connection = http.client.HTTPConnection('127.0.0.1', self.port)
        headers = {'Host': f'localhost:{self.port}'}
        if origin:
            headers['Origin'] = f'http://localhost:{self.port}'
        if cookie:
            headers['Cookie'] = cookie
        if csrf:
            headers['X-CSRF-Token'] = csrf
        body = json.dumps(data).encode() if data is not None else None
        if body:
            headers['Content-Type'] = 'application/json'
        connection.request('POST' if body else 'GET', '/manage/api/' + path, body=body, headers=headers)
        response = connection.getresponse()
        result = (response.status, dict(response.getheaders()), json.loads(response.read()))
        connection.close()
        return result

    def login(self):
        status, headers, body = self.call('login', {'password': 'correct-test-password'})
        self.assertEqual(status, 200)
        return headers['Set-Cookie'].split(';')[0], body['csrf']

    def test_android_download_and_mange_alias(self):
        def get(path, host='localhost'):
            connection = http.client.HTTPConnection('127.0.0.1', self.port)
            connection.request('GET', path, headers={'Host': host})
            response = connection.getresponse()
            result = response.status, dict(response.getheaders()), response.read()
            connection.close()
            return result
        status, headers, body = get('/mange')
        self.assertEqual(status, 200)
        self.assertIn(b'/manage/download/android.apk', body)
        self.assertEqual(headers['Cache-Control'], 'no-store')
        self.assertEqual(get('/mange', 'unrelated.example')[0], 404)
        status, headers, body = get('/manage/download/android.apk')
        self.assertEqual(status, 200)
        self.assertEqual(headers['Content-Type'], 'application/vnd.android.package-archive')
        self.assertIn('attachment;', headers['Content-Disposition'])
        self.assertEqual(body, (server.APP / 'downloads' / 'MTPoxy.apk').read_bytes())
        self.assertTrue(body.startswith(b'PK'))
        self.assertEqual(get('/manage/download/android.apk', 'unrelated.example')[0], 404)

    def test_authentication_and_write_only_token(self):
        self.assertEqual(self.call('state')[0], 401)
        self.assertEqual(self.call('login', {'password': 'wrong'})[0], 401)
        cookie, csrf = self.login()
        status, _, state = self.call('state', cookie=cookie)
        self.assertEqual(status, 200)
        self.assertTrue(state['token_configured'])
        self.assertNotIn('DO-NOT-RETURN-THIS-TOKEN', json.dumps(state))
        self.assertEqual(self.call('settings', {'email': 'other@example.com'}, cookie=cookie)[0], 403)
        self.assertEqual(self.call('settings', {'email': 'other@example.com'}, cookie=cookie, csrf=csrf, origin=False)[0], 403)
        self.assertEqual(self.call('settings', {'email': 'test@example.com'}, cookie=cookie, csrf=csrf)[0], 200)
        self.assertEqual(common.load_json(common.ROOT / 'settings.json')['dynv6_token'], self.settings['dynv6_token'])
        self.call('logout', {}, cookie=cookie, csrf=csrf)
        self.assertEqual(self.call('state', cookie=cookie)[0], 401)

    def test_persisted_sites_do_not_expose_internal_secret(self):
        common.save_json(common.ROOT / 'sites.json', [{'id': 1, 'hostname': 'recipe-test.dynv6.net', 'status': 'error', 'secret': 'private-value'}])
        self.assertNotIn('secret', provision.public_sites()[0])

    def test_dns_retry_reuses_owned_zone(self):
        zone = {'id': 5, 'ipv4address': '203.0.113.12'}
        with patch.object(provision, 'request', return_value=zone) as request:
            identifier = provision.dns_create('recipe-test.dynv6.net', {'dynv6_token': 'test', 'public_ip': '203.0.113.12'})
            self.assertEqual(identifier, 5)
            self.assertEqual(request.call_count, 1)

    def test_routes_keep_existing_service_and_all_websites(self):
        records = [{'hostname': 'recipe-a.dynv6.net', 'relay_port': 18002, 'status': 'active'},
                   {'hostname': 'recipe-b.dynv6.net', 'relay_port': 18004, 'status': 'installing'},
                   {'hostname': 'recipe-c.dynv6.net', 'relay_port': 18006, 'status': 'error'}]
        caddy, router = provision.routing_files(records, {'email': 'test@example.com'})
        self.assertIn('127.0.0.1:443', router)
        self.assertIn('recipe-a.dynv6.net', router)
        self.assertIn('recipe-b.dynv6.net', router)
        self.assertNotIn('recipe-c.dynv6.net', caddy)
        self.assertIn('admin 127.0.0.1:2029', caddy)

    def test_catalog_keeps_last_success_on_upstream_failure(self):
        common.save_json(common.ROOT / 'recipes.json', {'recipes': [{'name': 'Existing'}]})
        with patch.object(provision, 'request', side_effect=OSError('API down')):
            with self.assertRaises(OSError):
                provision.refresh_recipes()
        self.assertEqual(common.load_json(common.ROOT / 'recipes.json')['recipes'][0]['name'], 'Existing')

    def test_port_allocator_retains_legacy_allocations_and_skips_busy_ports(self):
        class Probe:
            def bind(self, address):
                if address[1] == 10000:
                    raise OSError('Occupied by another service')
            def close(self):
                pass
        with patch.object(provision.socket, 'socket', side_effect=lambda: Probe()):
            relay, backend = provision.allocate_ports([
                {'relay_port': 18002, 'backend_port': 30002},
                {'relay_port': 10006, 'backend_port': 10004},
                {'relay_port': 18004, 'backend_port': 30004},
            ])
        self.assertEqual((relay, backend), (10010, 10008))


if __name__ == '__main__':
    unittest.main()
