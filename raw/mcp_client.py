#!/usr/bin/env python3
"""Minimal MCP client (Streamable HTTP, SSE fallback) for the JetBrains MCP Server. stdlib only.

usage: mcp_client.py <base-url> list
       mcp_client.py <base-url> call <tool> '<json-args>'
"""
import json, sys, threading, queue, urllib.request, urllib.error, time, re

BASE = sys.argv[1].rstrip('/')
NEXT_ID = [1]

def nid():
    NEXT_ID[0] += 1
    return NEXT_ID[0]

# ---------- Streamable HTTP ----------
class Streamable:
    def __init__(self, url):
        self.url = url
        self.session = None
    def _post(self, body, expect_result=True):
        data = json.dumps(body).encode()
        req = urllib.request.Request(self.url, data=data, method='POST')
        req.add_header('Content-Type', 'application/json')
        req.add_header('Accept', 'application/json, text/event-stream')
        if self.session:
            req.add_header('Mcp-Session-Id', self.session)
        resp = urllib.request.urlopen(req, timeout=900)
        sid = resp.headers.get('Mcp-Session-Id')
        if sid:
            self.session = sid
        ctype = resp.headers.get('Content-Type', '')
        raw = resp.read().decode()
        if not expect_result:
            return None
        if 'text/event-stream' in ctype:
            for line in raw.splitlines():
                if line.startswith('data:'):
                    msg = json.loads(line[5:].strip())
                    if 'result' in msg or 'error' in msg:
                        return msg
            raise RuntimeError('no result in SSE body: ' + raw[:500])
        return json.loads(raw) if raw.strip() else None
    def start(self):
        r = self._post({'jsonrpc': '2.0', 'id': nid(), 'method': 'initialize', 'params': {
            'protocolVersion': '2025-03-26', 'capabilities': {}, 'clientInfo': {'name': 'katachi-evidence', 'version': '0'}}})
        self._post({'jsonrpc': '2.0', 'method': 'notifications/initialized'}, expect_result=False)
        return r
    def request(self, method, params):
        return self._post({'jsonrpc': '2.0', 'id': nid(), 'method': method, 'params': params})

# ---------- SSE (legacy) ----------
class Sse:
    def __init__(self, url):
        self.url = url
        self.post_url = None
        self.q = queue.Queue()
        self.ready = threading.Event()
    def _reader(self):
        req = urllib.request.Request(self.url, headers={'Accept': 'text/event-stream'})
        resp = urllib.request.urlopen(req, timeout=900)
        event, data = None, []
        for raw in resp:
            line = raw.decode().rstrip('\n').rstrip('\r')
            if line == '':
                if event == 'endpoint':
                    ep = ''.join(data)
                    self.post_url = ep if ep.startswith('http') else re.match(r'(https?://[^/]+)', self.url).group(1) + ep
                    self.ready.set()
                elif data:
                    try:
                        self.q.put(json.loads(''.join(data)))
                    except Exception:
                        pass
                event, data = None, []
                continue
            if line.startswith('event:'):
                event = line[6:].strip()
            elif line.startswith('data:'):
                data.append(line[5:].strip())
    def _post(self, body):
        data = json.dumps(body).encode()
        req = urllib.request.Request(self.post_url, data=data, method='POST')
        req.add_header('Content-Type', 'application/json')
        urllib.request.urlopen(req, timeout=60).read()
    def start(self):
        threading.Thread(target=self._reader, daemon=True).start()
        if not self.ready.wait(10):
            raise RuntimeError('no endpoint event')
        i = nid()
        self._post({'jsonrpc': '2.0', 'id': i, 'method': 'initialize', 'params': {
            'protocolVersion': '2025-03-26', 'capabilities': {}, 'clientInfo': {'name': 'katachi-evidence', 'version': '0'}}})
        r = self._wait(i)
        self._post({'jsonrpc': '2.0', 'method': 'notifications/initialized'})
        return r
    def _wait(self, i, timeout=900):
        end = time.time() + timeout
        while time.time() < end:
            try:
                m = self.q.get(timeout=1)
            except queue.Empty:
                continue
            if m.get('id') == i:
                return m
        raise RuntimeError('timeout waiting for id %s' % i)
    def request(self, method, params):
        i = nid()
        self._post({'jsonrpc': '2.0', 'id': i, 'method': method, 'params': params})
        return self._wait(i)

def connect():
    errors = []
    for cls, path in ((Streamable, '/mcp'), (Sse, '/sse')):
        try:
            c = cls(BASE + path)
            init = c.start()
            return c, path, init
        except Exception as e:  # noqa
            errors.append('%s: %r' % (path, e))
    raise SystemExit('could not connect: ' + '; '.join(errors))

def main():
    c, path, init = connect()
    cmd = sys.argv[2]
    out = {'transport': path, 'serverInfo': init.get('result', {}).get('serverInfo')}
    if cmd == 'list':
        r = c.request('tools/list', {})
        out['request'] = {'method': 'tools/list', 'params': {}}
        out['response'] = r
    elif cmd == 'call':
        tool = sys.argv[3]
        args = json.loads(sys.argv[4]) if len(sys.argv) > 4 else {}
        params = {'name': tool, 'arguments': args}
        out['request'] = {'method': 'tools/call', 'params': params}
        t0 = time.time()
        out['response'] = c.request('tools/call', params)
        out['elapsedSeconds'] = round(time.time() - t0, 1)
    print(json.dumps(out, ensure_ascii=False, indent=2))

if __name__ == '__main__':
    main()
