"""Client for the user's existing local Blockbench MCP plugin."""
import json,urllib.request,base64
from pathlib import Path
O=Path(__file__).resolve().parent
class BB:
    def __init__(self):
        self.session=None;self.count=0
        self.rpc('initialize',{'protocolVersion':'2024-11-05','capabilities':{},'clientInfo':{'name':'aircrate-engineering','version':'2'}})
    def rpc(self,method,params):
        self.count+=1;h={'Content-Type':'application/json','Accept':'application/json, text/event-stream'}
        if self.session:h['Mcp-Session-Id']=self.session
        req=urllib.request.Request('http://localhost:3000/bb-mcp',data=json.dumps({'jsonrpc':'2.0','id':self.count,'method':method,'params':params}).encode(),headers=h)
        with urllib.request.urlopen(req,timeout=60) as r:
            self.session=r.headers.get('Mcp-Session-Id') or self.session;raw=r.read().decode()
        if raw.startswith('event:'):raw=[l[6:] for l in raw.splitlines() if l.startswith('data: ')][-1]
        d=json.loads(raw)
        if 'error' in d:raise RuntimeError(d['error'])
        return d['result']
    def call(self,name,args):return self.rpc('tools/call',{'name':name,'arguments':args})
    def load(self,path):
        d=json.loads(Path(path).read_text(encoding='utf-8'))
        payload=json.dumps(d,ensure_ascii=False).replace('/', '\\u002f')
        return self.call('risky_eval',{'code':'(() => { Codecs.project.load('+payload+', {path: '+json.dumps(str(Path(path).resolve()),ensure_ascii=False)+', name: '+json.dumps(Path(path).name)+'}); return {name: Project.name, meshes: Mesh.all.length, textures: Texture.all.length, animations: Animation.all.length}; })()'})
    def shot(self,path):
        r=self.call('capture_screenshot',{})
        for c in r.get('content',[]):
            if c['type']=='image':Path(path).write_bytes(base64.b64decode(c['data']))
        return [c.get('text','') for c in r.get('content',[]) if c['type']=='text']
if __name__=='__main__':
    import sys
    b=BB()
    if sys.argv[1]=='load':print(b.load(sys.argv[2]))
    elif sys.argv[1]=='info':print(b.call('get_project_info',{}))
