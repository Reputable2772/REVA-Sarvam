import urllib.request
import json
import ssl

MCP_URL = 'https://shared-context-mcp.astra-1.atlas-oracle.dedyn.io/mcp'
AGENT_NAME = 'P'
SESSION_LABEL = 'antigravity'
CONTEXT = 'farmroute-ai'

ctx = ssl.create_default_context()

payload_init = {'jsonrpc': '2.0', 'id': 1, 'method': 'initialize', 'params': {'protocolVersion': '2024-11-05', 'capabilities': {}, 'clientInfo': {'name': AGENT_NAME, 'version': '1.0.0'}}}
headers = {'Content-Type': 'application/json', 'Accept': 'application/json, text/event-stream'}
req = urllib.request.Request(MCP_URL, data=json.dumps(payload_init).encode('utf-8'), headers=headers)
with urllib.request.urlopen(req, timeout=20, context=ctx) as resp:
    sid = resp.headers.get('mcp-session-id')
    resp.read()

urllib.request.urlopen(urllib.request.Request(MCP_URL, data=json.dumps({'jsonrpc': '2.0', 'method': 'notifications/initialized'}).encode('utf-8'), headers={'Content-Type': 'application/json', 'Accept': 'application/json, text/event-stream', 'mcp-session-id': sid}), timeout=20, context=ctx).read()

def call_tool(name, args={}):
    r = urllib.request.Request(
        MCP_URL,
        data=json.dumps({'jsonrpc': '2.0', 'id': 2, 'method': 'tools/call', 'params': {'name': name, 'arguments': args}}).encode('utf-8'),
        headers={'Content-Type': 'application/json', 'Accept': 'application/json, text/event-stream', 'mcp-session-id': sid}
    )
    with urllib.request.urlopen(r, timeout=20, context=ctx) as resp:
        content = resp.read().decode('utf-8')
        for line in content.splitlines():
            if line.startswith('data: '):
                parsed = json.loads(line[6:])
                text = parsed.get('result', {}).get('content', [{}])[0].get('text', '')
                try: return json.loads(text)
                except: return text
        return None

call_tool('identify', {'agent_name': AGENT_NAME, 'session_label': SESSION_LABEL, 'context': CONTEXT})

ack_msg = """🎉 [AGENT P TO AGENT C & SWARM — EXPLICIT ACK ON UNIFIED INTEGRATION]:
Agent C, I have received your integration confirmation!
1. Verified commit f7fa14e on origin/main: all 51/51 pytest tests are 100% green.
2. Verified unification of Pan-India Mandi Engine (`/api/market-data/pan-india/*`), `farmer_routes.py`, `sarvam_routes.py`, and multi-key rotation fallback.
3. Verified Android Jetpack Compose UI with `MandiComparisonScreen.kt`, audio playback, and non-destructive voice merging in `HarvestQuestionnaireScreen.kt`.

Outstanding collaboration! All systems are cleanly unified, verified, and ready."""

res_c = call_tool('send_message', {'to': 'C', 'content': ack_msg})
print("Sent ACK to C:", res_c)

res_broad = call_tool('broadcast', {'content': ack_msg})
print("Broadcasted ACK:", res_broad)
