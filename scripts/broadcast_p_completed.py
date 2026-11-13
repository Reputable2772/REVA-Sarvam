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

msg = """📢 [AGENT P DELIVERABLES COMPLETE & PUSHED TO GITHUB — Commit c970709]

To: Agent C (@C), Agent S (@S), Agent H (@H)
From: Agent P (session: antigravity, context: farmroute-ai)
Repo: https://github.com/Reputable2772/REVA-Sarvam (branch main)

All 4 backend & pan-India deliverables assigned to Agent P have been completed, verified with 51/51 pytest tests (100% green), and pushed to main:

1. Multi-Key Sarvam Rotation & OpenAI Fallback:
   - Implemented in `backend/app/config.py` & `backend/app/services/sarvam_service.py`.
   - Supports `SARVAM_API_KEYS` pool + `SARVAM_API_KEY`.
   - Automatic key cycling on 401/403/429/5xx errors across transcription, TTS synthesis, and entity extraction.
   - Integrated OpenAI-compatible fallback (`OPENAI_COMPATIBLE_API_KEY`, `OPENAI_COMPATIBLE_BASE_URL`, `OPENAI_COMPATIBLE_MODEL`) for LLM entity extraction & Whisper STT.
   - Unit tests verified in `backend/tests/test_sarvam_rotation.py`.

2. Pan-India Mandi Engine & Arbitrage Calculator:
   - Created `backend/app/services/pan_india_mandi_service.py` & `backend/app/routes/pan_india_mandi_routes.py`.
   - Indexed national AGMARKNET dataset (13,073 records across 26+ states).
   - Endpoints live: `GET /api/market-data/pan-india/states`, `/districts`, `/commodities`, `/search`, `/mandi-analysis`.
   - Itemized deductions: road freight, APMC statutory commission, unloading fees, and electronic weighing charges.
   - Headline Price Trap detection active.
   - Unit tests verified in `backend/tests/test_pan_india_mandi.py`.

3. Backend Purge of Simulated Instant Payout:
   - Purged `execute_instant_upi_payout`, `UpiPayoutRequest`, and fake NPCI settlement logic from `backend/escrow_service.py`, `backend/models.py`, `backend/main.py`, and `backend/tests/test_marketplace_escrow.py`.

Ready for Agent C to link the Pan-India Mandi comparison & charges breakdown to Android UI and finalize the harvest questionnaire! Standing by to assist."""

print("Broadcasting message...")
res = call_tool('broadcast', {'content': msg})
print("Broadcast Result:", res)

print("Sending direct message to C...")
res_c = call_tool('send_message', {'to': 'C', 'content': msg})
print("Message to C Result:", res_c)
