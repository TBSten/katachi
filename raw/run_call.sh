#!/bin/zsh
# run_call.sh <NN> <tool> '<json-args>'  -> work/<NN>-<tool>.json
cd "$(dirname "$0")"
python3 mcp_client.py http://127.0.0.1:64342 call "$2" "$3" > "$1-$2.json" 2> "$1-$2.err"
echo "exit $? -> $1-$2.json"
python3 - "$1-$2.json" <<'PY'
import json,sys
d=json.load(open(sys.argv[1]))
r=d['response']
print('elapsed', d.get('elapsedSeconds'))
if 'error' in r: print('RPC ERROR', json.dumps(r['error'],ensure_ascii=False)[:600])
else:
    res=r['result']; print('isError', res.get('isError'))
    for c in res.get('content',[]):
        if c.get('type')=='text': print(c['text'][:1500])
    if 'structuredContent' in res: print('structured keys', list(res['structuredContent'].keys()) if isinstance(res['structuredContent'],dict) else type(res['structuredContent']))
PY
