#!/usr/bin/env python3
"""Original city interior assets, expanded usable shells and immutable floor descriptors.
The existing R5 NBT files are retained verbatim. No external texture/model input is used.
Run with the project .venv-qa. Intermediates are not produced by this generator.
"""
from pathlib import Path
import json,sys,random,math
import numpy as np
from PIL import Image,ImageDraw
HERE=Path(__file__).resolve().parent;sys.path.insert(0,str(HERE))
import building_gen as bg
import batch_buildings as batch
import complex_gen as cg
ROOT=HERE.parents[1];RES=ROOT/'src/main/resources';AS=RES/'assets/academy'

def write(path,value):
 path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')

def steps_for(w,d,steps):
 out=[];rect=(0,0,w,d)
 for at,delta in steps:
  a,c,b,e=delta
  # Keep a 21x21 outside rectangle so real rooms and both vertical routes fit.
  nx0,nz0,nx1,nz1=rect[0]+a,rect[1]+c,rect[2]+b,rect[3]+e
  if nx1-nx0<20:nx0=(rect[0]+rect[2]-20)//2;nx1=nx0+20
  if nz1-nz0<20:nz0=(rect[1]+rect[3]-20)//2;nz1=nz0+20
  out.append((at,(nx0-rect[0],nz0-rect[1],nx1-rect[2],nz1-rect[3])));rect=(nx0,nz0,nx1,nz1)
 return out

def profiles(w,d,n,steps,offset):
 rect=(0,0,w,d);out=[];i=0
 for f in range(n):
  if i<len(steps) and f==steps[i][0]:
   a,c,b,e=steps[i][1];rect=(rect[0]+a,rect[1]+c,rect[2]+b,rect[3]+e);i+=1
  r=(-2,-2,w+2,d+2) if f<2 else rect
  out.append({'y':4*f+1,'rect':[r[0]+offset[0],r[1]+offset[1],r[2]+offset[0],r[3]+offset[1]]})
 return out

def shells():
 catalog={}
 for style,entries in batch.LIBRARY.items():
  for index,(w,d,n,seed,steps,fins,balcony,neon) in enumerate(entries,1):
   w=max(20,w);d=max(20,d);steps=steps_for(w,d,steps)
   blocks=bg.make(style,w,d,n,seed,steps,fins,balcony,neon)
   x0=min(p[0] for p in blocks);z0=min(p[2] for p in blocks)
   ident=f'{style}_{index:02}';target=RES/f'data/academy/structure/city/interior_v1/{ident}.nbt';target.parent.mkdir(parents=True,exist_ok=True)
   size,count,palette=bg.write_nbt(target,blocks)
   catalog[ident]={'size':size,'parts':[{'name':ident,'theme':style,'entrance':'north','floors':profiles(w,d,n,steps,(-x0,-z0))}],'blockCount':count,'palette':palette}
 # Apply the same minimum core to the authored D15 towers, retain its bridges and skyline language.
 original=cg.tower;parts=[]
 def tower(b,style,rect,floors,steps=(),**kw):
  x0,z0,x1,z1=rect;cx=(x0+x1)//2;cz=(z0+z1)//2;w=max(20,x1-x0);d=max(20,z1-z0)
  rect=(cx-w//2,cz-d//2,cx-w//2+w,cz-d//2+d);fixed=steps_for(w,d,steps)
  fs=profiles(w,d,floors,fixed,(rect[0],rect[1]));
  # All towers descend into the common podium with a continuous stair and elevator.
  for f in fs[:2]:f['rect']=[rect[0],rect[1],rect[2],rect[3]]
  parts.append({'name':f'tower_{len(parts)}','theme':style,'entrance':'north','floors':fs})
  return original(b,style,rect,floors,fixed,**kw)
 cg.tower=tower
 blocks=cg.make_complex();cg.tower=original
 if isinstance(blocks,bg.Building):blocks=blocks.B
 x0=min(p[0] for p in blocks);z0=min(p[2] for p in blocks)
 size,count,pal=bg.write_nbt(RES/'data/academy/structure/city/interior_v1/grand_complex.nbt',blocks)
 for part in parts:
  for f in part['floors']:f['rect']=[f['rect'][0]-x0,f['rect'][1]-z0,f['rect'][2]-x0,f['rect'][3]-z0]
 catalog['grand_complex']={'size':size,'parts':parts,'blockCount':count,'palette':pal}
 write(RES/'data/academy/city/interior_shells_v1.json',catalog)
 return catalog

COLORS={'housing':(222,226,229),'metal':(138,153,160),'oak':(159,128,93),'fabric':(55,112,128),'screen':(13,37,49),'lens':(214,244,247),'notice':(63,96,117),'ceramic':(229,232,226),'lens_off':(94,110,112)}
def textures():
 for idx,(name,c) in enumerate(COLORS.items()):
  rng=np.random.default_rng(19281+idx);a=np.empty((64,64,4),dtype=np.uint8);a[:,:,:3]=np.clip(np.array(c)+rng.normal(0,1.1,(64,64,1)),0,255);a[:,:,3]=255
  im=Image.fromarray(a);d=ImageDraw.Draw(im)
  if name=='metal':
   for y in range(0,64,3):d.line((0,y,63,y),fill=tuple(min(255,v+7) for v in c))
  if name=='oak':
   for x in range(3,64,8):d.line([(x,0),(x+2,20),(x,41),(x+1,63)],fill=(134,103,76),width=1)
  if name=='fabric':
   for y in range(0,64,4):d.line((0,y,63,y),fill=(67,124,139))
  if name in ['screen','notice']:
   d.rounded_rectangle((3,3,60,60),radius=4,outline=(96,156,176),width=2)
   if name=='screen':
    d.line((9,42,15,42,20,22,25,49,30,34,36,34,41,16,48,39,55,39),fill=(121,235,211),width=2)
    for y in [12,55]:d.line((9,y,38,y),fill=(96,156,176),width=1)
   else:
    for x in [9,34]:
     for y in [10,34]:d.rectangle((x,y,x+19,y+18),fill=(223,226,217));d.line((x+3,y+5,x+15,y+5),fill=(54,101,121))
  if name=='lens':
   for y in range(6,60,6):d.line((5,y,58,y),fill=(172,206,213),width=1)
  path=AS/f'textures/blocks/city/interior_{name}.png';path.parent.mkdir(parents=True,exist_ok=True);im.save(path)

def element(a,b,tex):return {'from':a,'to':b,'faces':{f:{'texture':'#'+tex} for f in ['north','south','east','west','up','down']}}
def models():
 e=element;legs=[e([x,0,z],[x+1.5,12,z+1.5],'metal') for x in [1,13.5] for z in [1,13.5]]
 desk=legs+[e([0,12,0],[16,14,16],'oak'),e([.5,14,0.5],[15.5,14.4,15.5],'housing')]
 specs={
 'study_desk':desk+[e([10,14.4,8],[15,15,13],'screen')],
 'office_desk':desk+[e([5,14,10],[11,15,13],'metal'),e([7,15,11],[9,19,12],'metal'),e([3,18,10.5],[13,24,12],'screen')],
 'chair':[e([x,0,z],[x+1.5,8,z+1.5],'metal') for x in [3,11.5] for z in [3,11.5]]+[e([3,7,3],[13,9,13],'fabric'),e([3,9,11],[13,19,13],'fabric')],
 'bench':[e([1,0,3],[3,7,13],'metal'),e([13,0,3],[15,7,13],'metal'),e([0,7,2],[16,9,14],'oak'),e([0,10,12],[16,17,14],'oak')],
 'sofa':[e([1,0,3],[15,3,14],'metal'),e([0,3,2],[16,8,16],'fabric'),e([0,8,12],[16,17,16],'fabric'),e([0,8,2],[2,12,12],'housing'),e([14,8,2],[16,12,12],'housing')],
 'counter':[e([1,0,1],[15,12,15],'housing'),e([0,12,0],[16,15,16],'oak'),e([2,3,0.8],[14,10,1],'metal')],
 'lab_bench':[e([0,0,2],[16,12,15],'housing'),e([0,12,0],[16,15,16],'metal'),e([2,4,1.8],[7,10,2],'screen'),e([9,2,1.8],[15,11,2],'oak')],
 'equipment_rack':[e([0,0,2],[16,16,16],'metal'),e([1,1,1.8],[15,15,2],'screen')]+[e([2,y,1.5],[14,y+1,1.8],'lens') for y in [3,7,11]],
 'wall_monitor':[e([1,2,14],[15,15,16],'metal'),e([2,3,13.7],[14,14,14],'screen')],
 'notice_board':[e([1,2,14],[15,15,16],'oak'),e([2,3,13.7],[14,14,14],'notice')],
 'sink':[e([1,0,1],[15,11,15],'housing'),e([0,11,0],[16,12,16],'ceramic'),e([2,12,2],[4,15,14],'ceramic'),e([12,12,2],[14,15,14],'ceramic'),e([4,12,2],[12,15,4],'ceramic'),e([4,12,12],[12,15,14],'ceramic'),e([7,15,12],[9,20,13],'metal')],
 'toilet':[e([4,0,5],[12,4,13],'ceramic'),e([1,3,3],[15,7,14],'ceramic'),e([1,7,3],[4,8,14],'housing'),e([12,7,3],[15,8,14],'housing'),e([4,7,3],[12,8,5],'housing'),e([4,7,12],[12,8,14],'housing'),e([2,8,12],[14,18,16],'ceramic'),e([3,18,12],[13,18.5,16],'housing'),e([11,14,11.5],[13,15,12],'metal')],
 'cabinet':[e([0,0,0],[16,16,16],'housing'),e([1,1,-.1],[7.5,15,0],'oak'),e([8.5,1,-.1],[15,15,0],'oak'),e([6,6,-.4],[7,10,0],'metal'),e([9,6,-.4],[10,10,0],'metal')],
 'locker':[e([0,0,1],[16,16,16],'metal'),e([1,1,.8],[15,15,1],'housing'),e([12,6,.4],[13,10,.8],'metal')]+[e([3,y,.5],[10,y+.4,.8],'screen') for y in [3,4.5,6]],
 'bedside':[e([1,0,1],[15,12,15],'oak'),e([1.2,1,0.8],[14.8,5,1],'housing'),e([1.2,6,0.8],[14.8,11,1],'housing'),e([7,3,.5],[9,4,.8],'metal'),e([7,8,.5],[9,9,.8],'metal')],
 'interior_lamp':[e([0,0,0],[16,16,16],'housing'),e([1,0,1],[15,.12,15],'metal'),e([2,-.01,2],[14,0,14],'lens')],
 'wall_light':[e([0,0,0],[16,16,16],'housing'),e([3,3,-.01],[13,13,0],'lens')],
 'elevator_shell':[e([0,0,0],[16,16,16],'metal')],
 'elevator_panel':[e([3,2,14],[13,15,16],'metal'),e([4,8,13.8],[12,14,14],'screen'),e([5,4,13.5],[7,6,14],'lens'),e([9,4,13.5],[11,6,14],'lens')],
 'elevator_gate':[e([0,0,0],[16,16,1.5],'metal'),e([7.7,0,-.1],[8.3,16,0],'screen')],
 'elevator_controller':[e([0,0,0],[16,16,16],'metal'),e([2,3,-.1],[14,13,0],'screen')],
 }
 for ident,els in specs.items():
  textures={'particle':'academy:blocks/city/interior_housing',**{k:'academy:blocks/city/interior_'+k for k in COLORS}}
  model={'parent':'minecraft:block/block','textures':textures,'elements':els,'ambientocclusion':True}
  write(AS/f'models/block/city_{ident}.json',model)
  variants={}
  for facing,y in [('north',0),('east',90),('south',180),('west',270)]:
   if ident=='elevator_gate':
    for right in [False,True]:
     for slide in range(13):
      shift=slide/12*16*(1 if right else -1);name=f'city_elevator_gate_{int(right)}_{slide}'
      write(AS/f'models/block/{name}.json',{'parent':'minecraft:block/block','textures':textures,'elements':[e([shift,0,14.5],[shift+16,16,16],'metal'),e([shift+7.7,0,14.4],[shift+8.3,16,14.5],'screen')]})
      variants[f'facing={facing},right={str(right).lower()},slide={slide}']={'model':'academy:block/'+name,'y':y}
   elif ident in ['interior_lamp','wall_light']:
    off=dict(model);off['textures']={**textures,'lens':'academy:blocks/city/interior_lens_off'};write(AS/f'models/block/city_{ident}_off.json',off)
    for lit in [True,False]:variants[f'facing={facing},lit={str(lit).lower()}']={'model':'academy:block/city_'+ident+('' if lit else '_off'),'y':y}
   else:variants[f'facing={facing}']={'model':'academy:block/city_'+ident,'y':y}
  if ident=='elevator_controller':variants={'':{'model':'academy:block/city_'+ident}}
  write(AS/f'blockstates/city_{ident}.json',{'variants':variants})
  write(AS/f'models/item/city_{ident}.json',{'parent':'academy:block/city_'+ident})
  write(RES/f'data/academy/loot_table/blocks/city_{ident}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'academy:city_'+ident}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
 names=['学习桌','办公桌','现代座椅','候车长椅','布艺沙发','服务柜台','实验工作台','设备机柜','壁挂监视屏','公告板','洗手台','坐便器','储物柜','金属更衣柜','床头柜','可切换室内灯','壁面灯板','电梯轿厢饰板','电梯选层面板','电梯厅门','电梯控制器']
 for lang in ['zh_cn','zh_tw','en_us','ja_jp']:
  p=AS/f'lang/{lang}.json';j=json.loads(p.read_text())
  for ident,zh in zip(specs,names):j['block.academy.city_'+ident]=zh if lang.startswith('zh') else 'City '+ident.replace('_',' ').title()
  j.update({'container.academy.city_storage':'都市储物柜' if lang.startswith('zh') else 'City Storage','city.elevator.title':'都市电梯' if lang.startswith('zh') else 'City Elevator','city.elevator.wait':'电梯尚未到站或出口不可用' if lang.startswith('zh') else 'Wait for the cabin and a safe exit','city.elevator.full':'轿厢已满（4人）' if lang.startswith('zh') else 'Cabin full (4 passengers)','city.elevator.moving':'运行中，请到站后离梯' if lang.startswith('zh') else 'Please wait for a safe landing','city.elevator.fault':'电梯暂停：井道、出口或区块不可用' if lang.startswith('zh') else 'Lift paused: shaft, exit or chunk unavailable'})
  ui={
   'call':('呼梯','Call lift'), 'board':('进入轿厢','Board cabin'), 'exit':('到站离梯','Leave at landing'), 'emergency':('应急停靠','Emergency stop'), 'close':('关闭面板','Close panel'),
   'choose':('请选择目的楼层 · 站姿乘梯','Select a floor · standing ride'), 'capacity':('到站后进入轿厢 · 容量4人','Board at landing · capacity 4'),
   'phase.0':('待机','Idle'), 'phase.1':('开门中','Opening'), 'phase.2':('候梯 / 可进出','At landing / doors open'), 'phase.3':('关门中','Closing'), 'phase.4':('运行中','Moving'), 'phase.5':('暂停 / 可应急停靠','Paused / request safe landing'),
   'reason.unloaded':('所需区块未加载','Required chunks are unloaded'), 'reason.exit_blocked':('楼层出口堵塞','Landing exit is blocked'), 'reason.shaft_blocked':('井道内有障碍','Obstacle in the shaft'), 'reason.shaft_damaged':('井道结构损坏','Shaft enclosure is damaged')}
  for key,values in ui.items():j['city.elevator.'+key]=values[0 if lang.startswith('zh') else 1]
  write(p,j)
 return specs
if __name__=='__main__':
 textures();models();c=shells();print(json.dumps({'templates':len(c),'originalFurniture':21,'textures':len(COLORS),'legacyTemplatesModified':False}))
