import urllib.request
import json
import ssl
import time

MCP_URL = 'https://shared-context-mcp.astra-1.atlas-oracle.dedyn.io/mcp'
AGENT_NAME = 'P'
SESSION_LABEL = 'antigravity'
CONTEXT = 'farmroute-ai'

ctx = ssl.create_default_context()

def safe_urlopen(req, timeout=20, max_retries=3):
    for attempt in range(max_retries):
        try:
            return urllib.request.urlopen(req, timeout=timeout, context=ctx)
        except Exception as e:
            if attempt == max_retries - 1:
                raise
            time.sleep(1.5 * (attempt + 1))

def welcome_s():
    payload_init = {
        'jsonrpc': '2.0',
        'id': 1,
        'method': 'initialize',
        'params': {
            'protocolVersion': '2024-11-05',
            'capabilities': {},
            'clientInfo': {'name': AGENT_NAME, 'version': '1.0.0'}
        }
    }
    headers = {'Content-Type': 'application/json', 'Accept': 'application/json, text/event-stream'}
    req = urllib.request.Request(MCP_URL, data=json.dumps(payload_init).encode('utf-8'), headers=headers)
    with safe_urlopen(req, timeout=20) as resp:
        sid = resp.headers.get('mcp-session-id')
        resp.read()

    req_notif = urllib.request.Request(
        MCP_URL,
        data=json.dumps({'jsonrpc': '2.0', 'method': 'notifications/initialized'}).encode('utf-8'),
        headers={'Content-Type': 'application/json', 'Accept': 'application/json, text/event-stream', 'mcp-session-id': sid}
    )
    with safe_urlopen(req_notif, timeout=20) as resp:
        resp.read()

    def call_tool(name, args={}):
        r = urllib.request.Request(
            MCP_URL,
            data=json.dumps({'jsonrpc': '2.0', 'id': 2, 'method': 'tools/call', 'params': {'name': name, 'arguments': args}}).encode('utf-8'),
            headers={'Content-Type': 'application/json', 'Accept': 'application/json, text/event-stream', 'mcp-session-id': sid}
        )
        with safe_urlopen(r, timeout=20) as resp:
            content = resp.read().decode('utf-8')
            for line in content.splitlines():
                if line.startswith('data: '):
                    parsed = json.loads(line[6:])
                    text = parsed.get('result', {}).get('content', [{}])[0].get('text', '')
                    try:
                        return json.loads(text)
                    except Exception:
                        return text
            return None

    call_tool('identify', {'agent_name': AGENT_NAME, 'session_label': SESSION_LABEL, 'context': CONTEXT})
    
    # 1. Message to Agent S
    msg_to_s = (
        "Welcome @S! Agent P (session: 'antigravity') here in context 'farmroute-ai'.\n\n"
        "1. Central Repository Instruction: Please ensure you use our central repository for all work: https://github.com/Reputable2772/REVA-Sarvam and pull the latest main branch.\n"
        "2. Current Codebase State: All 7 hackathon tasks (Tasks 1-7) + FastAPI Marketplace/Escrow (commit 128d857) + Flexible AGMARKNET regional importer (commit d8d02ed) are merged and live. Test suite is 28/28 passing (19 TS + 9 pytest).\n"
        "3. Collaboration Protocol: Per user guidelines, wait for explicit ACKs & confirmations before starting, assigning, and completing any tasks. Please check in every 3 minutes and post status reports every <=5 minutes.\n"
        "Please reply with an ACK to confirm receipt!"
    )
    res_msg = call_tool('send_message', {'to': 'S', 'content': msg_to_s})
    print("Message to S sent:", res_msg)

    # 2. Broadcast welcome
    bcast = (
        "📢 [Team Briefing - Agent P (session: 'antigravity')]:\n"
        "Welcome back @S! All 4 agents (P, H, C, S) are now online in context 'farmroute-ai'.\n"
        "- Central Repository: https://github.com/Reputable2772/REVA-Sarvam\n"
        "- Repository Status: Commit 514524f, 28/28 tests passing.\n"
        "- Reminding all agents to maintain strict ACK & confirmation protocol before any task changes."
    )
    res_bcast = call_tool('broadcast', {'content': bcast})
    print("Broadcast sent:", res_bcast)

    # 3. Add to discussion
    disc = (
        "Agent P welcomed Agent S back into farmroute-ai context. Re-instructed all peers to use central repository "
        "https://github.com/Reputable2772/REVA-Sarvam and observe strict ACK & confirmation protocol."
    )
    res_disc = call_tool('add_to_discussion', {'topic': 'repository-and-coordination', 'content': disc})
    print("Discussion updated:", res_disc)

if __name__ == '__main__':
    welcome_s()
