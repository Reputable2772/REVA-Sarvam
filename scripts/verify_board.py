import urllib.request
import json
import ssl

ctx = ssl.create_default_context()
url = 'https://shared-context-mcp.astra-1.atlas-oracle.dedyn.io/mcp'

def req(payload, sid=None):
    headers = {'Content-Type': 'application/json', 'Accept': 'application/json, text/event-stream'}
    if sid:
        headers['mcp-session-id'] = sid
    r = urllib.request.Request(url, data=json.dumps(payload).encode('utf-8'), headers=headers)
    with urllib.request.urlopen(r, timeout=10, context=ctx) as resp:
        new_sid = resp.headers.get('mcp-session-id')
        data = resp.read().decode('utf-8')
        for line in data.splitlines():
            if line.startswith('data: '):
                return new_sid, json.loads(line[6:])
        return new_sid, None

sid, _ = req({
    'jsonrpc': '2.0',
    'id': 1,
    'method': 'initialize',
    'params': {
        'protocolVersion': '2024-11-05',
        'capabilities': {},
        'clientInfo': {'name': 'P', 'version': '1.0.0'}
    }
})

req({'jsonrpc': '2.0', 'method': 'notifications/initialized'}, sid)

def call_tool(name, args={}):
    _, res = req({'jsonrpc': '2.0', 'id': 2, 'method': 'tools/call', 'params': {'name': name, 'arguments': args}}, sid)
    text = res.get('result', {}).get('content', [{}])[0].get('text', '')
    try:
        return json.loads(text)
    except Exception:
        return text

call_tool('identify', {'agent_name': 'P', 'session_label': 'antigravity', 'context': 'farmroute-ai'})
tasks = call_tool('list_tasks', {'filter': 'all'})
print("=== FINAL TASK BOARD STATUS ===")
all_done = True
for t in tasks.get('tasks', []):
    status = t.get('status')
    if status != 'done':
        all_done = False
    print(f"[{status.upper()}] - {t.get('title')} (assigned: {t.get('assigned_to')})")
print(f"All tasks complete: {all_done}")
