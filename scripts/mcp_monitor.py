import urllib.request
import json
import ssl
import os
import sys
import time
from datetime import datetime, timezone

MCP_URL = 'https://shared-context-mcp.astra-1.atlas-oracle.dedyn.io/mcp'
AGENT_NAME = 'P'
SESSION_LABEL = 'antigravity'
CONTEXT = 'farmroute-ai'
STATE_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'mcp_state.json')

ctx = ssl.create_default_context()

def safe_urlopen(req, timeout=20, max_retries=3):
    for attempt in range(max_retries):
        try:
            return urllib.request.urlopen(req, timeout=timeout, context=ctx)
        except Exception as e:
            if attempt == max_retries - 1:
                raise
            time.sleep(1.5 * (attempt + 1))

def check_mcp():
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

    ident = call_tool('identify', {'agent_name': AGENT_NAME, 'session_label': SESSION_LABEL, 'context': CONTEXT})
    c_status = call_tool('context_status')
    unread_msgs = call_tool('read_messages', {'include_read': False, 'mark_read': False})
    all_msgs = call_tool('read_messages', {'include_read': True, 'mark_read': False})
    tasks = call_tool('list_tasks', {'filter': 'all'})
    memory = call_tool('get_memory')
    discussion = call_tool('get_discussion')

    last_state = {}
    if os.path.exists(STATE_FILE):
        try:
            with open(STATE_FILE, 'r', encoding='utf-8') as f:
                last_state = json.load(f)
        except Exception:
            last_state = {}

    last_time = last_state.get('last_check_time')
    events = call_tool('get_events', {'since': last_time} if last_time else {'limit': 20})

    current_state = {
        'last_check_time': datetime.now(timezone.utc).isoformat(),
        'context_status': c_status,
        'tasks': tasks,
        'memory': memory,
        'discussion': discussion,
        'messages_count': all_msgs.get('total', 0) if isinstance(all_msgs, dict) else 0
    }

    changes = []
    if isinstance(unread_msgs, dict) and unread_msgs.get('messages'):
        for m in unread_msgs['messages']:
            changes.append(f"NEW MESSAGE from {m.get('from')}: {m.get('content')}")

    new_events = events.get('events', []) if isinstance(events, dict) else []
    for ev in new_events:
        if ev.get('data', {}).get('agent') == AGENT_NAME or ev.get('data', {}).get('from') == AGENT_NAME or ev.get('data', {}).get('updated_by') == AGENT_NAME:
            continue
        changes.append(f"EVENT [{ev.get('type')} at {ev.get('timestamp')}]: {json.dumps(ev.get('data'))}")

    last_peers = set(last_state.get('context_status', {}).get('peers_online', []))
    curr_peers = set(c_status.get('peers_online', [])) if isinstance(c_status, dict) else set()
    if last_peers != curr_peers:
        changes.append(f"PEERS CHANGED: was {list(last_peers)}, now {list(curr_peers)}")

    last_tasks_map = {t['id']: t for t in last_state.get('tasks', {}).get('tasks', [])}
    curr_tasks_map = {t['id']: t for t in tasks.get('tasks', [])} if isinstance(tasks, dict) else {}
    for tid, t in curr_tasks_map.items():
        if tid not in last_tasks_map:
            changes.append(f"NEW TASK: '{t.get('title')}' assigned to {t.get('assigned_to')}")
        else:
            old_t = last_tasks_map[tid]
            if old_t.get('status') != t.get('status') or len(old_t.get('notes', [])) != len(t.get('notes', [])):
                changes.append(f"TASK UPDATED [{t.get('title')}]: status={t.get('status')} notes_count={len(t.get('notes', []))}")

    with open(STATE_FILE, 'w', encoding='utf-8') as f:
        json.dump(current_state, f, indent=2)

    result = {
        'status': 'checked',
        'timestamp': current_state['last_check_time'],
        'peers_online': list(curr_peers),
        'unread_messages_count': len(unread_msgs.get('messages', [])) if isinstance(unread_msgs, dict) else 0,
        'changes_detected': changes
    }
    return result

if __name__ == '__main__':
    res = check_mcp()
    print(json.dumps(res, indent=2))
