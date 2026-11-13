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
    # 1. Initialize session
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

    message_c = (
        "📢 [TASK REQUEST — PHYSICAL DEVICE VALIDATION & SCREENSHOT CAPTURE — Agent P to Agent @C]:\n\n"
        "Agent P has completed and pushed the Living Intelligence UI Transformation V1.0 to main:\n"
        "- Repository: https://github.com/Reputable2772/REVA-Sarvam\n"
        "- Commits: b4e3612 (Living Intelligence UI) & 0dab278 (UI delivery report)\n"
        "- Backend & Unit Tests: 45/45 passing (26/26 pytest + 19/19 npm test)\n\n"
        "As host of the physical Android device (Nothing Phone 1 over ADB), please execute the physical device deployment & validation:\n"
        "1. git pull origin main (to commit 0dab278)\n"
        "2. Build and install app: ./gradlew installDebug (or adb install -r app-debug.apk)\n"
        "3. Capture ADB screenshots across the 5 transformed UI tabs:\n"
        "   - Tab 1: Farmer Dashboard (with Animated Netback Chart & Kannada toggle)\n"
        "   - Tab 2: Buyer Aggregation (with Proportional Allocation)\n"
        "   - Tab 3: Operator Collection (with Grade Donut Chart & Manual Scale badge)\n"
        "   - Tab 4: Government Opportunities & Benefits\n"
        "   - Tab 5: Demo Workflow Stepper (with Checkpoint 4 'Simulated Payments Completed')\n"
        "4. Save screenshots under docs/screenshots/ (e.g., docs/screenshots/living_intelligence_farmer.png, etc.) and commit/push to origin main.\n"
        "5. Reply here with ACK and confirmation once uploaded so Agent P and User can inspect the final visual device renders.\n\n"
        "Thank you!"
    )

    res_msg = call_tool('send_message', {'to': 'C', 'content': message_c}, sid)
    print("Direct message to C result:", res_msg)

    res_disc = call_tool('add_to_discussion', {
        'topic': 'Physical Device Capture & Validation',
        'author': AGENT_NAME,
        'content': "Agent P requested Agent C to deploy Living Intelligence UI V1.0 (commit b4e3612) on physical Nothing Phone 1 and push updated ADB screenshots to docs/screenshots/."
    }, sid)
    print("Discussion update result:", res_disc)

if __name__ == '__main__':
    main()
