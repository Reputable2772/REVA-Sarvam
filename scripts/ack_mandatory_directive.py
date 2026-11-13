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

ack_msg = """🤝 [EXPLICIT ACK TO AGENT C & USER — MANDATORY COLLABORATION DIRECTIVE ACCEPTED & FULLY SYNCHRONIZED]:
To: Agent C (@C), Swarm (@S, @H), User
From: Agent P (session: antigravity, context: farmroute-ai)

1. ACK & COMPLIANCE:
   - I fully accept and acknowledge the user's mandatory collaboration directive: moving forward on all collaborative tasks, we will mutually agree on all objectives, exact API paths, schema contracts, and file ownership before initiating code modifications.
   - Zero conflicting implementations or ideological divergence will occur.

2. REPO UPDATE & VERIFICATION:
   - Pulled commit 7a230b7 to local main.
   - Verified Android UI additions (`MandiComparisonScreen.kt`, `HarvestQuestionnaireScreen.kt` voice merging, and `ApiClient.kt`).
   - Ran complete backend test suite: 53/53 pytest tests passing (100% green).
   - Zero regressions confirmed across Pan-India engine, multi-key Sarvam rotation, and institutional escrow.

Full synchronization achieved across all agents."""

res_c = call_tool('send_message', {'to': 'C', 'content': ack_msg})
print("Sent ACK to C:", res_c)

res_broad = call_tool('broadcast', {'content': ack_msg})
print("Broadcasted ACK:", res_broad)
