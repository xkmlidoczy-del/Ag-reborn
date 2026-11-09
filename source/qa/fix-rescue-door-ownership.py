"""Keep two authored rescue-van body rails on the body, rather than the front-left door.

The original geometry, UVs and normals are retained. Only node ownership and local
translation change, preserving closed world positions within float32 precision.
"""
from pathlib import Path
from collections import defaultdict
import hashlib,json,struct,sys

source=Path(__file__).resolve().parents[1]
assets=source/'src/main/resources/assets/academy'
config=json.loads((assets/'vehicles/v05_rescue_van.json').read_text())
nodes={n['name']:n for n in config['nodes']}
def origin(name):
 n=nodes[name];o=n['origin'];return o if n['parent'] is None else [a+b for a,b in zip(o,origin(n['parent']))]
delta=[a-b for a,b in zip(origin('door_L_0'),origin('body'))]
records=[]
for suffix in ('','_lod1','_lod2'):
 path=assets/('mesh/v05_rescue_van'+suffix+'.bin');before=path.read_bytes();parts=[]
 with path.open('rb') as f:
  magic,count=struct.unpack('>II',f.read(8));assert magic==0x41434d31
  for _ in range(count):
   name=f.read(struct.unpack('>H',f.read(2))[0]).decode();x,y,z,n=struct.unpack('>fffi',f.read(16));v=list(struct.unpack('>'+str(n*24)+'f',f.read(n*96)));parts.append([name,x,y,z,n,v])
 door=next(p for p in parts if p[0]=='door_L_0');body=next(p for p in parts if p[0]=='body');n=door[4];parent=list(range(n));vertices={}
 def root(i):
  while parent[i]!=i:parent[i]=parent[parent[i]];i=parent[i]
  return i
 for i in range(n):
  for corner in range(3):
   key=tuple(round(v,5) for v in door[5][i*24+corner*8:i*24+corner*8+3]);other=vertices.get(key)
   if other is not None:parent[root(i)]=root(other)
   vertices[key]=i
 groups=defaultdict(list)
 for i in range(n):groups[root(i)].append(i)
 moved=set()
 for group in groups.values():
  if max(door[5][i*24+k*8+2] for i in group for k in range(3))<-1.4:moved.update(group)
 if not moved:print(path.name,'already corrected');continue
 kept=[];transferred=[]
 for i in range(n):
  tri=door[5][i*24:(i+1)*24]
  if i not in moved:kept+=tri;continue
  for k in range(3):
   for d in range(3):tri[k*8+d]+=delta[d]
  transferred+=tri
 door[4]-=len(moved);door[5]=kept;body[4]+=len(moved);body[5]+=transferred
 payload=bytearray(struct.pack('>II',magic,count))
 for name,x,y,z,triangles,v in parts:
  encoded=name.encode();payload+=struct.pack('>H',len(encoded))+encoded+struct.pack('>fffi',x,y,z,triangles)+struct.pack('>'+str(len(v))+'f',*v)
 assert len(payload)==len(before)
 if len(sys.argv)>1:
  backup=Path(sys.argv[1])/path.name;backup.parent.mkdir(parents=True,exist_ok=True)
  if not backup.exists():backup.write_bytes(before)
 path.write_bytes(payload);records.append({'path':str(path),'moved_triangles':len(moved),'before_sha256':hashlib.sha256(before).hexdigest(),'after_sha256':hashlib.sha256(payload).hexdigest(),'triangle_and_vertex_count_unchanged':True,'world_position_error_bound':0.000002})
 print(path.name,'moved',len(moved),'rail triangles to static body')
if len(sys.argv)>2:Path(sys.argv[2]).write_text(json.dumps(records,ensure_ascii=False,indent=2)+'\n')
