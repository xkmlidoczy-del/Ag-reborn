#!/usr/bin/env python3
"""Read-only modern Anvil/NBT census. No block/player/world files are changed."""
import argparse, collections, gzip, hashlib, json, math, struct, zlib
from pathlib import Path

class Nbt:
    def __init__(self, data): self.data, self.offset = data, 0
    def read(self, length):
        value=self.data[self.offset:self.offset+length]; self.offset+=length
        if len(value)!=length: raise ValueError('truncated NBT')
        return value
    def number(self, kind): return struct.unpack('>'+kind, self.read(struct.calcsize('>'+kind)))[0]
    def string(self): return self.read(self.number('H')).decode('utf-8')
    def value(self, kind):
        if kind in (1,2,3,4,5,6): return self.number({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[kind])
        if kind==7: return list(self.read(self.number('i')))
        if kind==8: return self.string()
        if kind==9:
            member=self.number('b'); count=self.number('i'); return [self.value(member) for _ in range(count)]
        if kind==10:
            result={}
            while True:
                member=self.number('b')
                if not member: return result
                name=self.string(); result[name]=self.value(member)
        if kind in (11,12): return [self.number('i' if kind==11 else 'q') for _ in range(self.number('i'))]
        raise ValueError('unsupported NBT type '+str(kind))
    def named(self):
        kind=self.number('b'); self.string(); return self.value(kind)

def compressed(path): return Nbt(gzip.decompress(Path(path).read_bytes())).named()
def chunks(path):
    data=path.read_bytes()
    for slot in range(1024):
        entry=int.from_bytes(data[slot*4:slot*4+4],'big'); sector,count=entry>>8,entry&255
        if not sector: continue
        offset=sector*4096; size=int.from_bytes(data[offset:offset+4],'big'); compression=data[offset+4]
        if compression&128: raise ValueError('external chunk requires explicit reader: '+str(path))
        payload=data[offset+5:offset+4+size]
        if compression==1: payload=gzip.decompress(payload)
        elif compression==2: payload=zlib.decompress(payload)
        elif compression!=3: raise ValueError('unsupported region compression '+str(compression))
        yield Nbt(payload).named()

def main():
    parser=argparse.ArgumentParser();parser.add_argument('world',type=Path);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    world=args.world;level=compressed(world/'level.dat')['Data'];player=level.get('Player',{});states=collections.Counter();positions=collections.defaultdict(list);status=collections.Counter();full=0
    ore_names={'academy:constraint_metal_ore','academy:imag_silicon_ore','academy:crystal_ore','academy:reso_crystal_ore'}
    for region in sorted((world/'region').glob('*.mca')):
        for chunk in chunks(region):
            status[chunk.get('Status','unknown')]+=1
            if chunk.get('Status') not in ('full','minecraft:full'): continue
            full+=1;cx,cz=chunk['xPos'],chunk['zPos']
            for section in chunk.get('sections',[]):
                container=section.get('block_states',{});palette=container.get('palette',[])
                wanted={i:e['Name'] for i,e in enumerate(palette) if e['Name'].startswith('academy:')}
                if not wanted: continue
                packed=container.get('data',[]);bits=max(4,(len(palette)-1).bit_length());per_long=64//bits;mask=(1<<bits)-1
                if len(palette)>1 and len(packed)<math.ceil(4096/per_long): raise ValueError('incomplete block-state array')
                for index in range(4096):
                    entry=0 if len(palette)==1 else ((packed[index//per_long]&((1<<64)-1))>>((index%per_long)*bits))&mask
                    if entry not in wanted: continue
                    name=wanted[entry];states[name]+=1
                    if name in ore_names:positions[name].append([cx*16+(index&15),section['Y']*16+(index>>8),cz*16+((index>>4)&15)])
    anchor=player.get('Pos',[106.5,71,166.5]);census={}
    for name,places in sorted(positions.items()):
        places.sort(key=lambda p:(p[0]-anchor[0])**2+(p[2]-anchor[2])**2)
        census[name]={'blocks':len(places),'minY':min(p[1] for p in places),'maxY':max(p[1] for p in places),'nearest16':places[:16]}
    stats_files=sorted((world/'stats').glob('*.json'));stats=json.loads(stats_files[0].read_text()).get('stats',{}) if stats_files else {}
    result={'scope':'Actual closed singleplayer generated Anvil data; read-only census, not mining/crafting/progression completion','seed':level['WorldGenSettings']['seed'],'generator':level['WorldGenSettings']['dimensions']['minecraft:overworld']['generator'],'allowCommands':bool(level['allowCommands']),'gameType':level['GameType'],'difficulty':level['Difficulty'],'fullChunks':full,'statuses':dict(status),'academyGeneratedBlocks':dict(states),'oreCensus':census,'playerAfterOrdinaryRespawn':{'pos':anchor,'health':player.get('Health'),'inventory':player.get('Inventory',[]),'abilities':player.get('abilities'),'progress':player.get('NeoForgeData',{}).get('academy:classic_progress',{})},'actualVanillaStats':{k:stats.get(k,{}) for k in ('minecraft:mined','minecraft:picked_up','minecraft:custom')},'witnesses':{str(p.relative_to(world)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [world/'level.dat',*sorted((world/'region').glob('*.mca'))]}}
    args.out.parent.mkdir(parents=True,exist_ok=True);args.out.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({'fullChunks':full,'generatedAcademyBlocks':dict(states),'ores':{k:{i:v[i] for i in ('blocks','minY','maxY')} for k,v in census.items()},'commands':result['allowCommands'],'seed':result['seed'],'mined':stats.get('minecraft:mined',{}),'pickedUp':stats.get('minecraft:picked_up',{})}))

if __name__=='__main__':main()
