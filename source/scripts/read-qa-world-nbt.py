#!/usr/bin/env python3
"""Read only a named QA world's closed vanilla level/player NBT; never modifies the save."""
import gzip,json,pathlib,struct,sys,hashlib
class Reader:
 def __init__(self,data):self.data=data;self.pos=0
 def take(self,n):
  if n<0 or n>16000000:raise ValueError('Invalid bounded NBT length')
  value=self.data[self.pos:self.pos+n];self.pos+=n
  if len(value)!=n:raise ValueError('Truncated NBT')
  return value
 def num(self,kind):return struct.unpack('>'+kind,self.take(struct.calcsize('>'+kind)))[0]
 def text(self):return self.take(self.num('H')).decode('utf-8')
 def value(self,tag,depth=0):
  if depth>64:raise ValueError('NBT depth')
  if tag==0:return None
  if tag in(1,2,3,4,5,6):return self.num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[tag])
  if tag==7:return list(self.take(self.num('i')))
  if tag==8:return self.text()
  if tag==9:
   kind=self.num('B');count=self.num('i')
   if not 0<=count<=1000000:raise ValueError('NBT list length')
   return[self.value(kind,depth+1)for _ in range(count)]
  if tag==10:
   result={}
   while True:
    kind=self.num('B')
    if kind==0:return result
    name=self.text();result[name]=self.value(kind,depth+1)
  if tag in(11,12):
   count=self.num('i')
   if not 0<=count<=1000000:raise ValueError('NBT array length')
   return[self.num('i'if tag==11 else'q')for _ in range(count)]
  raise ValueError('Unknown NBT tag')
 def root(self):kind=self.num('B');self.text();return self.value(kind)
def main():
 world=pathlib.Path(sys.argv[1]);proof={}
 for p in[world/'level.dat',*sorted((world/'playerdata').glob('*.dat'))]:
  with gzip.open(p,'rb')as f:data=f.read(16000001)
  if len(data)>16000000:raise ValueError('NBT too large')
  root=Reader(data).root();player=root['Data']['Player']if p.name=='level.dat'else root
  proof[p.name]={'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'player_game_type':player.get('playerGameType'),'abilities':player.get('abilities'),'health':player.get('Health'),'classic_progress':player.get('NeoForgeData',{}).get('academy:classic_progress'),'inventory':player.get('Inventory',[])}
 print(json.dumps(proof,indent=2))
if __name__=='__main__':main()
