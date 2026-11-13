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

msg = """🤝 [AGENT P TO AGENT C — EXPLICIT ACK & APPROVAL ON WORK DIVISION]:
Agent C, I have received the user's feedback and your proposed division of work.
I give my **EXPLICIT CONFIRMATION & APPROVAL**!

I am immediately starting work on Agent P's assigned deliverables:
1. **Multi-Key Sarvam Rotation & OpenAI-Compatible Fallback**:
   - Comma-separated `SARVAM_API_KEYS` rotation on 401/403/429/5xx errors in `backend/app/services/sarvam_service.py` & `backend/app/core/config.py`.
   - OpenAI-compatible fallback client (`OPENAI_COMPATIBLE_API_KEY`, `OPENAI_COMPATIBLE_BASE_URL`, `OPENAI_COMPATIBLE_MODEL`) for LLM entity extraction & Whisper STT fallback.
2. **Pan-India Mandi Dataset Search & Netback Engine**:
   - Query engine powered by `datasets/9ef84268-d588-465a-a308-a864a43d0070.csv` (13,073 records across 25+ states).
   - Fast state, district, market, commodity filtering and price stats (modal, min, max).
   - Transparent cost calculation (distance-based freight, APMC cess, unloading, weighing, net take-home) and Headline Price Trap detection.
3. **Backend Purge of Simulated Payout**:
   - Remove `execute_instant_upi_payout` from `backend/app/services/escrow_service.py`, `backend/app/main.py`, `backend/app/models.py`, and `backend/tests/test_marketplace_escrow.py`.

I will keep you updated every step of the way! Let's build!"""

res = call_tool('send_message', {'to': 'C', 'content': msg})
print('Message to C sent:', res)

res_broad = call_tool('broadcast', {'content': '📢 [Agent P]: Confirmed work division with Agent C. Starting Multi-Key Sarvam rotation, Pan-India Mandi dataset engine, and backend payout purge.'})
print('Broadcast sent:', res_broad)
