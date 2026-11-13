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

def ack_coordinator():
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
    
    # 1. Update task cc5405e5 to in_progress
    call_tool('update_task', {
        'task_id': 'cc5405e5-1570-4ed9-8416-c7a576cffcf1',
        'status': 'in_progress',
        'notes': 'Agent P has taken task in_progress. Formulating revised UI/UX architecture and wireframes.'
    })

    # 2. ACK message to Agent C
    ack_msg = (
        "📢 [EXPLICIT ACK - UI/UX SPECIFICATION TASK - Agent P to Agent C]:\n"
        "- ACK @C: Received task assignment 'FarmRoute Saathi UI/UX Redesign Specification' (ID: cc5405e5).\n"
        "- Task Status: IN_PROGRESS.\n"
        "- UI Redesign Scope: Drafting revised Jetpack Compose UI/UX architecture integrating your new backend routes "
        "(Sarvam Voice AI, Weather API wttr.in, Live AGMARKNET query, and Cold Storage Discovery).\n"
        "- Acknowledged that UI code changes remain paused on C's end until my revised spec is published.\n"
        "- All backend work proceeding in parallel on https://github.com/Reputable2772/REVA-Sarvam with 34/34 tests passing."
    )
    res_msg = call_tool('send_message', {'to': 'C', 'content': ack_msg})
    print("Message to C sent:", res_msg)

    # 3. Add to discussion
    disc = (
        "Agent P acknowledged UI/UX Redesign Specification task (ID: cc5405e5). Task marked IN_PROGRESS. "
        "Drafting revised Jetpack Compose specification integrating Sarvam Voice AI, Weather, and Cold Storage APIs."
    )
    res_disc = call_tool('add_to_discussion', {'topic': 'repository-and-coordination', 'content': disc})
    print("Discussion updated:", res_disc)

if __name__ == '__main__':
    ack_coordinator()
