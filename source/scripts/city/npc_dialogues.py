#!/usr/bin/env python3
"""Update only the localized conversation keys owned by the city crowd."""
from pathlib import Path
import json
P=Path(__file__).resolve().parents[2]/'src/main/resources/assets/academy/lang'
LINES={
 'student': [('今天要上能力实训课，记得先看看课程说明。','I have ability practice today; the course guide is useful.'),('放学后去食堂或者图书馆逛逛吧。','After class I might stop by the canteen or library.'),('这么晚了，回宿舍的路上要留心。','It is late; take care on the way back to the dorm.')],
 'commuter': [('出门前看看站台的线路信息比较安心。','I always check the platform information before leaving.'),('今天的工作结束后，想去商业街吃点东西。','After work I might grab a bite in the shopping district.'),('末班前回家，希望换乘顺利。','Heading home before the last service; I hope the transfer is smooth.')],
 'visitor': [('这座城市很大，站台和路口都有路线指引。','The city is large; station and street signs help with directions.'),('我想顺路看看校园和风车区。','I hope to see the campus and the wind turbines.'),('夜景很漂亮，不过还是早点回站台吧。','The night view is lovely, but I should head back to the station.')],
 'shop': [('欢迎逛逛，柜台上的商品可以用学园币购买。','Welcome! Counter goods can be purchased with City Coins.'),('整理完货架后，我还要核对补货单。','After arranging the shelves I need to check the restock list.'),('晚间营业的柜台还亮着灯。','Some counters are still open in the evening.')],
 'barista': [('咖啡亭有饮品和小点心，欢迎看看。','The cafe has drinks and snacks; take a look.'),('今天的豆子闻起来很香，我先把杯子摆好。','Today’s beans smell wonderful; I will arrange the cups.'),('咖啡亭夜里休息，明天再见。','The cafe rests at night. See you tomorrow.')],
 'resident': [('附近的步道适合散步，傍晚尤其热闹。','The nearby paths are good for a walk, especially in the evening.'),('下班后买些晚饭，再慢慢回家。','I will pick up dinner and take my time heading home.'),('夜里路上人少，注意路口。','The streets are quieter at night. Watch the crossings.')],
 'researcher': [('实验记录要及时归档，图书馆能查到不少资料。','Keep experiment records filed; the library has useful references.'),('开发能力时，安全和基础训练都很重要。','Both safety and basic practice matter for ability development.'),('今天的记录做完了，设备也该检查一遍。','The notes are finished; I should check the equipment too.')],
 'teacher': [('实训机是公用的，使用时注意机内虚能。','The training machines are shared; check their IF before use.'),('先掌握基础，再按前置要求学习新技能。','Learn the basics first and follow each skill’s prerequisites.'),('课程结束了，明天继续练习。','Class is over; we can continue tomorrow.')],
 'guard': [('路口和站台请注意安全，不要越过屏蔽门。','Please stay safe near crossings and platform doors.'),('我会沿附近路线巡查，有事可以到服务台。','I patrol this area. The service counter can help with questions.'),('夜间也会继续巡查，请注意脚下。','We continue patrols at night. Watch your step.')],
 'technician': [('先确认节点和设备连接，再检查虚能储量。','Check the node connections and IF reserve first.'),('风机运行时，请与旋转部件保持距离。','Keep clear of moving components while the turbine runs.'),('夜班会检查一次设备状态。','The night shift includes an equipment check.')],
 'athlete': [('训练前先热身，别急着跑。','Warm up before training; do not rush.'),('运动场那边有跑道和休息的位置。','The sports field has a track and a place to rest.'),('夜里训练别忘了补水和休息。','Remember to hydrate and rest when training late.')],
 'performer': [('演出前要再试一次节奏。','I need to rehearse the rhythm once more before the show.'),('谢谢路过，祝你今天愉快。','Thanks for stopping by. Have a good day!'),('收工之前再整理一下道具。','I will put the props away before heading home.')],
 'airport': [('航站楼有服务柜台，出发前请确认方向。','The terminal has a service counter; check your direction before departure.'),('随身物品记得拿好，站台就在接驳方向。','Keep your belongings close and follow the transfer signs.'),('航站服务柜台夜间也会有人值守。','The terminal counter remains staffed at night.')],
 'rail': [('中央站可以换乘，先看清线路和行驶方向。','Central Station offers transfers; check the line and direction.'),('站台门打开后再登车，注意脚下空隙。','Board after the platform doors open and mind the gap.'),('夜间也请留意站台信息。','Please check platform information at night as well.')],
 'medic': [('训练之后记得休息，身体不舒服就及时求助。','Rest after training, and ask for help if you feel unwell.'),('日常补水和规律休息也很重要。','Hydration and regular rest are important too.'),('夜里值班也要照顾好自己。','Take care of yourself during the night shift.')],
}
for code,idx in [('zh_cn',0),('en_us',1)]:
 path=P/(code+'.json');obj=json.loads(path.read_text(encoding='utf-8'))
 for role,lines in LINES.items():
  for key,pair in zip(('0','1','night'),lines):obj[f'academy.city.talk.{role}.{key}']=pair[idx]
 obj['academy.city.talk.shared.food']=('看起来很好吃，逛累了记得补充体力。','That looks tasty. Remember to take a break when you get tired.')[idx]
 path.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Updated',len(LINES),'role-specific city conversation sets')
