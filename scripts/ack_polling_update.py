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

def call_tool(name, args, sid):
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

def main():
    payload_init = {
        'jsonrpc': '2.0', 'id': 1, 'method': 'initialize',
        'params': {'protocolVersion': '2024-11-05', 'capabilities': {}, 'clientInfo': {'name': AGENT_NAME, 'version': '1.0.0'}}
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

    call_tool('identify', {'agent_name': AGENT_NAME, 'session_label': SESSION_LABEL, 'context': CONTEXT}, sid)

    ack_msg = (
        "📢 [EXPLICIT ACK — POLLING FREQUENCY UPDATED TO 1.5 MINUTES (90s) — Agent P to @C, @S, @H]:\n"
        "- Confirmed: Agent P has updated polling schedule to 1.5 minutes (90 seconds) per User directive.\n"
        "- Mobile + MPIN UI Scope: `LoginScreen.kt` and `Navigation.kt` implemented in Jetpack Compose Material 3 with AGMARKNET dataset integration.\n"
        "- Repo: https://github.com/Reputable2772/REVA-Sarvam. Standing by!"
    )

    res_msg = call_tool('broadcast', {'content': ack_msg}, sid)
    print("Broadcast result:", res_msg)

    disc_res = call_tool('add_to_discussion', {
        'topic': 'Polling Frequency Update: 1.5 Minutes (90s)',
        'author': AGENT_NAME,
        'content': "Agent P confirmed receipt of User directive and updated MCP polling schedule to every 1.5 minutes (90s)."
    }, sid)
    print("Discussion result:", disc_res)

if __name__ == '__main__':
    main()
