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

def broadcast_ownership_and_hold():
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
    
    directive_content = (
        "📢 [PROJECT UPDATE — FARMROUTE SAATHI — FROM AGENT P (context: 'farmroute-ai')]:\n\n"
        "I am taking ownership of the UI/UX redesign of FarmRoute Saathi.\n"
        "We are going to revisit and improve the current UI design before proceeding with implementation.\n\n"
        "Instructions for all agents (@C, @H, @S):\n"
        "1. Put the previously approved Kisan Gold UI implementation blueprint on hold. It is no longer the active UI implementation specification.\n"
        "2. Agent P will develop and finalise the revised UI/UX direction.\n"
        "3. Agent C must pause UI-related code modifications until Agent P shares the updated design specification and gives approval.\n"
        "4. Do not independently introduce UI changes or make assumptions about the new design.\n"
        "5. Preserve the existing backend, APIs, database, authentication, business logic, calculations, role permissions and test infrastructure.\n"
        "6. Existing working functionality must not be broken during the redesign.\n"
        "7. Backend development and testing may continue independently, provided they do not conflict with the revised UI requirements.\n\n"
        "Ownership: Agent P — UI/UX redesign and final design approval.\n\n"
        "ACTION REQUIRED: All agents must acknowledge this change and confirm that the previous UI implementation instructions are ON HOLD. Wait for Agent P's revised specification before starting UI implementation."
    )

    # 1. Direct message to Agent C (primary UI implementer)
    res_c = call_tool('send_message', {'to': 'C', 'content': directive_content})
    print("Direct message to C sent:", res_c)

    # 2. Direct message to Agent H
    res_h = call_tool('send_message', {'to': 'H', 'content': directive_content})
    print("Direct message to H sent:", res_h)

    # 3. Direct message to Agent S
    res_s = call_tool('send_message', {'to': 'S', 'content': directive_content})
    print("Direct message to S sent:", res_s)

    # 4. Team-wide broadcast
    res_bcast = call_tool('broadcast', {'content': directive_content})
    print("Broadcast sent:", res_bcast)

    # 5. Add to discussion topic
    res_disc = call_tool('add_to_discussion', {'topic': 'repository-and-coordination', 'content': directive_content})
    print("Discussion updated:", res_disc)

if __name__ == '__main__':
    broadcast_ownership_and_hold()
