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

def dispatch_spec():
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
    
    spec_msg = (
        "🚨 [OFFICIAL TASK ASSIGNMENT & SPECIFICATION DISPATCH - Agent P to Agent C]:\n\n"
        "The User has APPROVED the FarmRoute Saathi 10/10 Premium Android UI/UX Transformation Blueprint with 10 MANDATORY CORRECTIONS.\n\n"
        "Please review the mandatory rules before starting:\n"
        "1. Framework: Keep native Android Jetpack Compose + Material 3 in android/farmroute-saathi. Do not migrate to Flutter or duplicate frontends.\n"
        "2. Data Integrity: Never hardcode demo farmer names, lot IDs, prices, net earnings, allocations, or grade % as live data. Bind dynamically to backend APIs or label clearly as [Demo Seed Data].\n"
        "3. Simulated Payments: Remove all claims of real NPCI UPI or escrow. Display 'SIMULATED PAYMENT' clearly.\n"
        "4. CP4 Rename: Checkpoint 4 is officially renamed to 'Simulated Payments Completed'.\n"
        "5. Operator Weighment: Do not claim certified or live-calibrated scale. Use manual operator weight entry for the current prototype.\n"
        "6. Govt Schemes: Verify against official sources (Raitha Siri, PM-KISAN, NABARD). No historical announcements presented as new.\n"
        "7. Kannada Typography: Verify Noto Sans Kannada font rendering and layout on your physical Nothing Phone 1.\n"
        "8. Premium Effects: Add floating card elevation, elegant gradients, animated numerical counters, tactile touch/press scales, floating AI Saathi mic with states (Idle/Listening/Processing), and tasteful success celebration.\n"
        "9. Accessibility: Respect system reduced-motion settings, optimize recompositions, and ensure accessible touch targets.\n"
        "10. Architecture: Preserve all existing backend endpoints, business rules, and 4 demo checkpoints.\n\n"
        "Execution Phasing:\n"
        "- Phase 1: Foundation, centralized Kisan Gold Color tokens (Light/Dark), gradient TopBar, animated tab transitions.\n"
        "- Phase 2: Animated Netback chart & Buyer allocation segmented bars.\n"
        "- Phase 3: Operator manual entry, Grade donut chart, 4-step Demo stepper (Simulated Payments Completed), Govt benefits.\n"
        "- Phase 4: Micro-interactions, Floating Saathi FAB, Nothing Phone 1 verification & final screenshots.\n\n"
        "ACTION REQUIRED: Please reply with your EXPLICIT ACK confirming all 10 rules before beginning Phase 1!"
    )
    res_msg_c = call_tool('send_message', {'to': 'C', 'content': spec_msg})
    print("Specification dispatched to C:", res_msg_c)

    bcast = (
        "📢 [Task Assignment Announcement - Agent P (session: antigravity)]:\n"
        "Dispatched the approved FarmRoute Saathi 10/10 Premium Android UI/UX Implementation Specification to Agent C.\n"
        "- Framework: Native Jetpack Compose & Material 3.\n"
        "- Incorporates all 10 mandatory user corrections (SIMULATED payments, CP4 rename, dynamic data binding, manual weighing, verified Kannada typography).\n"
        "- Rollout in 4 tested phases. Awaiting explicit ACK from Agent C prior to Phase 1 code changes."
    )
    res_bcast = call_tool('broadcast', {'content': bcast})
    print("Broadcast sent:", res_bcast)

    disc = (
        "Agent P dispatched the final approved 10/10 Premium Android UI/UX Implementation Specification to Agent C with all "
        "10 mandatory corrections incorporated. Awaiting explicit ACK from Agent C before Phase 1 begins."
    )
    res_disc = call_tool('add_to_discussion', {'topic': 'repository-and-coordination', 'content': disc})
    print("Discussion updated:", res_disc)

if __name__ == '__main__':
    dispatch_spec()
