#!/usr/bin/env python3
"""Localize city conversation, professional services and district work orders. Standard library only."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/academy/lang'
TEXT = {
 'combat.spar': ('切磋训练（非致命）', 'Sparring (nonlethal)'),
 'combat.spar_confirm': ('确认开始切磋', 'Begin sparring'),
 'combat.spar_rules': ('切磋会实际使用已学技能，消耗我的 CP、过载和材料。双方保留最低生命；生命偏低、离开约 28 格或一分钟后结束。再次右键我可随时结束。请在生存模式下开始，留出足够的空地。', 'Sparring uses learned skills and spends my CP, overload and materials. Both sides retain a health floor. It ends at low health, about 28 blocks separation or one minute. Right-click me again to stop. Start in survival with room to move.'),
 'combat.duel_started': ('切磋开始：会实际消耗能力资源，右键对手可随时结束。', 'Sparring started. Real ability resources are spent. Right-click the opponent to stop.'),
 'combat.duel_finished': ('切磋结束，我先回岗位恢复。', 'Sparring ended. I will return to my post and recover.'),
 'combat.duel_npc_yield': ('我认输，先休息恢复。', 'I yield. Time to rest and recover.'),
 'combat.duel_player_yield': ('这轮到这里，先恢复生命再练。', 'Let us stop this round and recover health.'),
 'combat.not_ready': ('现在无法开始：请检查生命、CP、过载、技能冷却和所需材料。', 'Not ready. Check health, CP, overload, cooldowns and required materials.'),
 'combat.stats': ('生命 %s / %s\nCP %s / %s\n过载 %s / %s', 'Health %s / %s\nCP %s / %s\nOverload %s / %s'),
 'combat.ready': ('能力可用；每项技能独立冷却。', 'Abilities ready; skills have individual cooldowns.'),
 'combat.overloaded': ('过载封锁：需等过载完全恢复。', 'Overloaded: wait until strain fully recovers.'),
 'combat.skill_stats': ('解锁 Lv%s；CP约%s，过载约%s，基础伤害%s；冷却%s tick', 'Unlock Lv%s; CP ~%s, strain ~%s, base damage %s; cooldown %s ticks'),
 'combat.mode.daily': ('日常活动', 'Daily routine'),
 'combat.mode.alert': ('警戒', 'Alert'),
 'combat.mode.casting': ('能力施放', 'Casting'),
 'combat.mode.retreating': ('拉开距离', 'Retreating'),
 'combat.mode.recovering': ('资源恢复', 'Recovering'),
 'combat.mode.returning': ('返回岗位', 'Returning'),
 'combat.mode.downed': ('暂时休息', 'Resting'),
 'dialogue.channel': ('学园通讯', 'City link'),
 'dialogue.wallet': ('学园币  %s', 'City coins  %s'),
 'dialogue.district': ('第 %s 学区', 'District %s'),
 'dialogue.face_to_face': ('面对面交谈', 'Face-to-face conversation'),
 'dialogue.profile': ('人物档案', 'Profile'),
 'dialogue.topics': ('可选话题', 'Topics'),
 'dialogue.citizen': ('城市居民', 'City resident'),
 'dialogue.service_status': ('日常工作与生活', 'Work and daily life'),
 'dialogue.task_details': ('差事详情', 'Work order details'),
 'dialogue.leave': ('结束交谈', 'Goodbye'),
 'dialogue.balance': ('D%s · 随身学园币 %s', 'D%s · City coins: %s'),
 'dialogue.scroll': ('滚轮阅读正文', 'Scroll to read'),
 'dialogue.exchange': ('兑换学园币', 'Exchange city coins'),
 'dialogue.trade': ('看看商品', 'Browse goods'),
 'dialogue.heal': ('医护治疗 · %s 币', 'Medical treatment · %s coins'),
 'dialogue.repair': ('修复主手装备 · %s 币', 'Repair held item · %s coins'),
 'dialogue.inspect_item': ('检查主手装备', 'Inspect held item'),
 'dialogue.guide': ('请教能力开发', 'Ability development advice'),
 'dialogue.tasks': ('这里有什么差事？', 'Local work orders'),
 'dialogue.esper': ('能力档案与演示', 'Esper profile / demonstration'),
 'dialogue.talk': ('聊聊你的日常', 'How is your day?'),
 'dialogue.local': ('了解这个地区', 'About this district'),
 'dialogue.journal': ('查看我的差事进度', 'My work order journal'),
 'dialogue.back': ('返回交谈', 'Back to conversation'),
 'dialogue.choose': ('请选择右侧话题；右侧滚轮或箭头翻页。交谈结束后，我会继续自己的工作。', 'Choose a topic on the right. Use its wheel or arrows to change pages. I will resume my day afterwards.'),
 'dialogue.local_help': ('右键服务人员可选购物、差事或职业服务。差事详情会显示交接人和坐标；我的差事中可随时回看。', 'Service staff offer shopping, work orders and professional help. Order details show the recipient and coordinates; your journal keeps active orders.'),
 'dialogue.heal_info': ('治疗 4 币：恢复生命并处理中毒、虚弱。健康时不收费，能力 CP 和过载仍按原机制恢复。', 'Treatment costs 4 coins: restores health and removes poison / weakness. Healthy patients pay nothing. Ability CP and overload use their existing recovery.'),
 'dialogue.repair_info': ('把损坏的工具或护甲放在主手。按损坏比例收 3—8 币修满耐久，保留附魔；能量充电请使用原供电设施。', 'Hold a damaged tool or armor item. Full durability repair costs 3–8 coins based on wear and preserves enchantments. Energy charging uses the original power facilities.'),
 'dialogue.awaken': ('如何觉醒？', 'How do I awaken?'),
 'dialogue.learn': ('如何学习技能？', 'How do I learn skills?'),
 'dialogue.power': ('机器如何补能？', 'How are the machines powered?'),
 'dialogue.guide_intro': ('D7 第二校舍设四台公用高级开发机，按原有能力系统进行觉醒和学习。请选择你想了解的步骤。图书馆也能交接资料类差事。', 'D7’s second school has four public advanced developers using the original awakening and learning system. Choose a topic. The library also receives document work orders.'),
 'dialogue.guide_awaken': ('右键原生高级开发机，依原有界面进行觉醒。若要指定能力系，需要对应诱导因子；否则依原机制抽取。公用机台免费，开发过程会消耗机内 IF。', 'Right-click a native advanced developer and follow its awakening interface. A matching induction factor selects a category; otherwise the original random selection applies. Public machine use is free, while development consumes its IF.'),
 'dialogue.guide_learn': ('右键机台进入原生技能树；按自己的能力系、等级、前置技能和经验选择学习。城市 NPC 的能力演示不会授予玩家技能，也不能代替机台开发。', 'Open the machine’s native skill tree. Learning follows your category, level, prerequisite skills and experience. NPC demonstrations do not grant player skills or replace development.'),
 'dialogue.guide_power': ('屋顶原生风机经中层高级无线虚能节点给四台机组供电。公用机组初始预充 100,000 IF，开发后由风电回充；请勿拆除公共节点和风机。', 'Native roof turbines feed the four developers through the advanced wireless IF node. Public machines start with 100,000 IF and refill through wind generation. Keep the public network connected.'),
 'dialogue.demo': ('能力练习（消耗 CP）', 'Practise (spends CP)'),
 'service.healthy': ('检查完成：你目前无需治疗，本次不收费。', 'You do not need treatment. No charge.'),
 'service.no_coins': ('这项服务需要 %s 枚学园币，随身数量不足，尚未扣费。可以去中央站兑换，或完成地区差事赚币。', 'This service needs %s city coins. You do not have enough; nothing was charged. Exchange materials at Central Station or complete work orders.'),
 'service.healed': ('治疗完成，收取 %s 币。生命已恢复，中毒与虚弱已处理。', 'Treatment complete for %s coins. Health restored; poison and weakness removed.'),
 'service.no_repair': ('主手没有需要修复耐久的装备，本次不收费。请先把损坏的工具、武器或护甲拿在主手。', 'No damaged durability item is held. No charge. Hold a damaged tool, weapon or armor item first.'),
 'service.repaired': ('已修复 %s，收取 %s 币。耐久恢复，原有附魔和物品数据保留。', 'Repaired %s for %s coins. Durability restored, enchantments and item data preserved.'),
 'quest.destination': ('交接：%s · D%s (%s, %s, %s)', 'Recipient: %s · D%s (%s, %s, %s)'),
 'quest.survey': ('先到巡检点 (%s, %s, %s) 附近 8 格内，再回来交接。', 'First visit within 8 blocks of (%s, %s, %s), then return.'),
 'quest.supply': ('交接时携带 %s 小麦，会扣除这批物资。', 'Bring %s wheat for handover; the supplies will be consumed.'),
 'quest.reward': ('报酬：%s 枚学园币 · 每位玩家限一次', 'Reward: %s city coins · once per player'),
 'quest.offer': ('%s · 报酬 %s 币', '%s · %s coins'),
 'quest.turn_in': ('可以交接：%s', 'Ready: %s'),
 'quest.check': ('查看：%s', 'Details: %s'),
 'quest.accept': ('接取差事 · 报酬 %s 币', 'Accept · reward %s coins'),
 'quest.finish': ('交接并领取 %s 币', 'Hand over / claim %s coins'),
 'quest.accepted': ('已接取「%s」。', 'Accepted: %s.'),
 'quest.completed': ('「%s」交接完成，获得 %s 枚学园币。', '%s completed. Received %s city coins.'),
 'quest.changed': ('这条差事的状态已更新，请查看我的差事。', 'This order has changed. Check your journal.'),
 'quest.not_ready': ('交接条件尚未满足。请按详情完成巡检或备齐物资。', 'The handover requirements are not met. Complete the inspection or bring the supplies.'),
 'quest.visited': ('「%s」巡检点已记录，可以返回交接人领取报酬。', '%s inspection recorded. Return to the recipient for payment.'),
 'quest.board': ('地区差事', 'District work orders'),
 'quest.none': ('这里暂时没有新的待办。已接取任务可以在「我的差事」回看。每条任务按玩家记录，只能完成一次。', 'No new orders here. Check your journal for active work. Each order can be completed once per player.'),
 'quest.board_help': ('先选一条查看内容，再决定接取或交接。送件采用城市电子工作单，不占背包；需要物资或实地巡检的任务会明确标注。', 'Choose an order to review before accepting or handing over. Documents use digital work orders. Supply and on-site inspection requirements are stated explicitly.'),
 'quest.journal': ('我的地区差事', 'My district journal'),
 'quest.no_active': ('目前没有进行中的差事。可向 D7 导师和医护、中央站服务台，以及各地区服务岗询问。', 'No active orders. Ask the D7 mentor / medic, Central Station service desk, or district service posts.'),
 'quest.survey_done': ('✓ 巡检已记录', '✓ Inspection recorded'),
 'quest.survey_wait': ('○ 仍需先到巡检点（详见任务）', '○ Visit the inspection point first (see details)'),
 'quest.summary': ('进行中 %s · 已完成 %s / %s', 'Active: %s · Completed: %s / %s'),
 'quest.state.0': ('状态：尚未接取', 'Status: not accepted'),
 'quest.state.1': ('状态：进行中，按要求到交接人处办理。', 'Status: active. Meet the requirements and visit the recipient.'),
 'quest.state.2': ('状态：已完成', 'Status: completed'),
 'esper.profile': ('%s · Level %s', '%s · Level %s'),
 'esper.rules': ('练习会真实消耗 CP、增加过载，并按原技能独立冷却。攻击练习只空放，不锁定居民。切磋或自卫时才会对有效目标产生伤害与控制；纸、针和液影介质有限，水流操作需要附近水源。', 'Practice spends CP, adds overload and uses native skill cooldowns. Offensive practice fires into empty space. Sparring and defense can damage or control valid targets. Paper, needles and shadow medium are finite; water skills need a nearby source.'),
 'esper.cooldown': ('距离下次演示还有 %s 秒。', 'Next demonstration in %s seconds.'),
 'esper.started': ('正在演示：%s。', 'Demonstrating: %s.'),
}
ROLES = {
 'campus_medic': ('D7 校园医护', 'D7 campus medic'), 'field_medic': ('D18 社区医护', 'D18 community medic'),
 'engineer': ('D22 风场工程师', 'D22 wind-farm engineer'), 'research_contact': ('D2 实训研究员', 'D2 training researcher'),
 'curator': ('D3 科技馆联络员', 'D3 science-museum liaison'), 'logistics': ('D17 物流调度员', 'D17 logistics coordinator'),
 'coach': ('D20 场馆教练', 'D20 venue coach'), 'reservoir': ('D21 水库巡检员', 'D21 reservoir inspector'),
}
PROFILES = {
 'electro': ('电击使·电路实训生', 'Electromaster / circuit trainee', '使用原生电弧发生；高等级研究人员可解锁雷击之槌。命中造成技能伤害，施放消耗 CP 并积累过载。', 'Uses native Arc Generation, with Thunder Bolt unlocked at higher levels. Hits deal skill damage and spend CP / overload.'),
 'melt': ('原子崩坏·束流标定员', 'Meltdowner / beam technician', '使用原生电子球；三级起可充能发射原子崩坏束流。充能逐 tick 耗能，原有宽束线命中算法生效，城市中不破坏方块。', 'Uses Electron Bomb; level 3 unlocks charged Meltdowner. Native charge ticks, energy drain and wide-beam hits apply without destroying city blocks.'),
 'teleport': ('空间移动·物流协作员', 'Teleporter / logistics aide', '消耗库存针进行威慑传送，二级解锁标记传送以调整位置，三级可用空间撕裂。移动需要可站立且无碰撞的落点。', 'Spends needles on Threatening Teleport. Level 2 adds Mark Teleport for positioning, and level 3 Flesh Ripping. Movement requires a supported collision-free landing.'),
 'vector': ('矢量操作·缓冲实训生', 'Vector control / cushioning trainee', '使用原生定向冲击，命中带真实击退；三级解锁定向爆破。近身施放前有准备动作，资源不足时会退开恢复。', 'Uses Directed Shock with native knockback; level 3 unlocks Directed Blastwave. Winds up visibly and retreats when resources run low.'),
 'mental': ('心理掌握·节律引导师', 'Mental Out / rhythm guide', '以精神探测观察目标；三级可对敌对生物进行短时精神干扰，打断追击和攻击。不会接管玩家的操作。', 'Uses Mental Probe and, at level 3, Mental Disrupt to interrupt hostile mobs. It does not take over player input.'),
 'air': ('空力使·风压实训生', 'Aero Hand / airflow trainee', '利用风压推掌造成伤害并推开对手，也会用空气喷射调整位置。实际气流速度和技能范围沿用原技能参数。', 'Uses Aero Push for damage / displacement and Aero Jet to reposition, using the original skill ranges and motion parameters.'),
 'float': ('流体反发·轻物实训生', 'Float Dial / light-load trainee', '以浮升让敌对生物真实离地，三级可维持悬浮并使用浮力救援。依靠高度与重力产生效果，不是简单附加缓降药水。', 'Uses Float Lift to physically raise hostile mobs. Higher levels add suspension and rescue. Effects use real movement and gravity.'),
 'nitrogen': ('氮气装甲·场馆安全员', 'Nitrogen armor / venue safety aide', '形成有持续时间和有限容量的氮气护层，受到攻击会消耗护层；三级可在护层存在时施放氮气重拳。', 'Creates a timed, finite nitrogen shell depleted by incoming attacks. Level 3 can perform Nitrogen Punch while the shell is active.'),
 'dark': ('未元物质·结构研究员', 'Dark matter / structure researcher', '可近身形成未元之刃或远距释放未元碎片；三级解锁翼的机动。技能会消耗资源并造成实际伤害，不留下可采集材料。', 'Forms Dark Blades or fires Dark Shards. Level 3 adds wing mobility. Skills spend resources and deal real damage without creating collectible matter.'),
 'water': ('水流操作·补给协作员', 'Hydro Hand / supply aide', '调用附近六格内真实水源施放水鞭；三级可形成水幕。没有水源时不能凭空攻击，会先调整位置或避险。', 'Uses a real water source within six blocks for Hydro Whip; level 3 adds Hydro Shield. Without a source, it must reposition or evade.'),
 'paper': ('纸媒念动·纸艺实训生', 'Paper psychokinesis / paper-art trainee', '消耗随身纸张施放纸刃；三级可消耗三张纸形成有限纸甲。材料在战斗中不会自动补充，回岗位后按班次补给。', 'Spends carried paper on Paper Blade; level 3 can spend three sheets on finite Paper Armor. Combat does not refill materials.'),
 'shadow': ('液化人影·舞台协作员', 'Liquid shadow / stage aide', '消耗浓缩介质生成原生液影身体，由它接近目标并进行接触攻击；本体仍需保持距离、视野和资源。', 'Consumes dense medium to form a real liquid shadow body, which approaches targets for contact attacks while the owner manages range and resources.'),
}
QUESTS = {
 'course': ('课程归档', 'Course archive', '将实训课程电子记录交给 D7 图书管理员，帮助整理开发课程。', 'Bring the electronic training records to the D7 librarian.'),
 'stock': ('商亭补货单', 'Kiosk restock order', '将中央站的电子补货单交给 D15 西亭店员。', 'Bring Central Station’s electronic restock order to the D15 west kiosk.'),
 'clinic': ('社区随访资料', 'Community follow-up notes', '把校园健康随访资料送交 D18 社区医护，协助安排后续随访。', 'Deliver campus follow-up notes to the D18 community medic.'),
 'health': ('健康讲义归档', 'Health handout archive', '把社区健康讲义送交 D7 图书管理员，供学生查阅。', 'Bring community health handouts to the D7 librarian.'),
 'wind': ('风场维护工单', 'Wind-farm work order', '中央站整理了供能维护工单，请交给 D22 风场工程师。', 'Deliver Central Station’s power-maintenance work order to the D22 engineer.'),
 'calibration': ('风压标定记录', 'Airflow calibration records', '将风场采集的标定记录送给 D2 实训研究员，用于比较低输出风压控制。', 'Bring wind-farm calibration records to the D2 training researcher.'),
 'exhibit': ('能力展陈资料', 'Ability exhibit notes', '将研究员审定的民用能力说明送交 D3 科技馆联络员。', 'Bring reviewed civilian-ability notes to the D3 museum liaison.'),
 'sports': ('场馆演示排期', 'Venue demonstration schedule', '把科技馆的安全演示排期送交 D20 场馆教练，协调运动与能力实训。', 'Bring the museum’s safe-demonstration schedule to the D20 coach.'),
 'rations': ('训练餐补给', 'Training meal supplies', '教练需要餐饮街为训练餐备料。带 8 小麦到 D4 柜台交接，获得补给报酬。', 'Bring 8 wheat to the D4 food-street counter for training-meal preparation.'),
 'cargo': ('航站行李核对单', 'Terminal baggage manifest', '将 D17 的电子核对单送给 D23 航站柜台，协助旅客行李交接。', 'Bring D17’s electronic baggage manifest to the D23 terminal counter.'),
 'water': ('水库步道巡检', 'Reservoir walkway inspection', '沿大坝步道到南侧观测点检查通行情况，再回巡检员处提交观察记录。', 'Inspect the southern dam-walkway observation point, then report back to the inspector.'),
 'inspection': ('风场接入步道巡检', 'Wind-farm access inspection', '到风场接入步道的巡检点实地查看，再回工程师处登记；无需攀爬高塔或拆卸设备。', 'Visit the wind-farm access checkpoint, then report back. No tower climbing or equipment dismantling is required.'),
}
LOOKS = {
 'student_bag':('背包学生','Student'), 'student_sport':('运动装学生','Student athlete'), 'student_books':('读书学生','Student reader'),
 'commuter':('通勤者','Commuter'), 'visitor':('游客','Visitor'), 'shop_clerk':('店员','Clerk'), 'resident':('居民','Resident'),
 'casual_visitor':('闲逛的访客','Visitor'), 'office_staff':('职员','Office worker'), 'vendor':('摊主','Vendor'), 'researcher':('研究员','Researcher'),
 'guard':('警备员','Guard'), 'technician':('技术员','Technician'), 'athlete':('运动员','Athlete'), 'performer':('街头艺人','Performer'),
 'airport_staff':('航站工作人员','Airport staff'), 'student_blazer':('校服学生','Uniformed student'), 'student_casual':('便装学生','Student'),
 'teacher':('教师','Teacher'), 'barista':('咖啡师','Barista'), 'rail_staff':('站务员','Rail staff'), 'medic':('医护人员','Medic'),
 'engineer':('工程师','Engineer'), 'traveler':('旅客','Traveler'), 'esper_uniform':('能力实训生','Esper student'),
 'esper_track':('运动实训生','Esper athlete'), 'esper_lab':('能力研究助理','Esper lab assistant'), 'esper_work':('能力协作职员','Esper office aide'),
 'esper_art':('能力表演者','Esper performer'), 'esper_field':('社区能力协作员','Community esper aide'),
}
DISTRICTS = [
 ('城市边缘','City edge'),('D1 行政办公区，通勤与公共事务为主。','D1 civic and office district.'),
 ('D2 实训研究区，研究员在地标前庭接收标定资料。','D2 training district; a researcher receives calibration records at the landmark forecourt.'),
 ('D3 科技展陈区，科技馆联络员负责民用能力展陈和演示排期。','D3 science exhibition district; the museum liaison coordinates civilian-ability exhibits.'),
 ('D4 餐饮街，柜台可购买餐食，也接收训练餐物资。','D4 food street; the counter sells meals and accepts training supplies.'),
 ('D5 大学校园区，学生、研究人员与居民共用街区。','D5 university district shared by students, researchers and residents.'),
 ('D6 休闲演艺区，街头表演者在傍晚更活跃。','D6 entertainment district; street performers are more active in the evening.'),
 ('D7 校园区，第二校舍有四台公用高级开发机，南侧图书馆设资料接待与校园医护。','D7 campus: four public advanced developers in the second school, library reception and campus medic to its south.'),
 ('D8 居住区，以步行、休息和日常交流为主。','D8 residential district for walking, rest and daily conversation.'),
 ('D9 商住街区，职员、居民与商户共用街道。','D9 mixed retail and residential streets.'),
 ('D10 居住街区，学生和通勤者在日间较活跃。','D10 housing with students and commuters active during the day.'),
 ('D11 安静生活街区，以社区居民和商户为主。','D11 quiet community streets.'),
 ('D12 学习生活街区，学生与居民共享公共空间。','D12 learning and residential district.'),
 ('D13 综合生活区，居民和职员的日常路线交织。','D13 mixed neighborhood for residents and office workers.'),
 ('D14 城市边缘住区，夜间人流较少。','D14 outer residential district with fewer people at night.'),
 ('D15 商业与中央换乘区，中央站可兑换学园币；广场东西亭提供食品和城市商品。','D15 commerce and Central interchange; exchange coins at the station and shop at the east / west kiosks.'),
 ('D16 社区商业区，以生活性商店与居民活动为主。','D16 neighborhood retail district.'),
 ('D17 工业物流区，前庭物流调度员负责航站核对单。','D17 industrial logistics district; its coordinator handles terminal manifests.'),
 ('D18 社区与研究混合区，前庭医护提供治疗和随访资料交接。','D18 mixed community and research district; its medic offers treatment and follow-up handovers.'),
 ('D19 社区公共活动区，居民与学生共享公共空间。','D19 community public-space district.'),
 ('D20 运动区，教练负责安全演示与训练餐补给。','D20 sports district; its coach coordinates safe demonstrations and meal supplies.'),
 ('D21 水库区，大坝观景平台有巡检员；沿步道巡检时注意栏杆和落差。','D21 reservoir; an inspector staffs the dam overlook. Follow the fenced walkway.'),
 ('D22 风场区，工程师在接入步道旁接待，负责维修与维护资料交接。','D22 wind farm; an engineer beside the access path offers repair and maintenance work orders.'),
 ('D23 航站区，航站柜台全天营业，并接收物流核对单。','D23 terminal; its counter is open all day and receives logistics manifests.'),
]

# City-life sample: station workplaces, genuine commuter journeys and repeatable contracts.
TEXT.update({
 'life.new_chunks': ('此处没有新版本的生活样品，请在新存档或新生成区块预览。','This location lacks the new city-life sample. Use a fresh world or newly generated chunks.'),
 'life.welcome': ('D7 通勤阅览室：右键服务员接可重复委托。南侧通道连接站内楼梯；NPC 会候车、乘车、到站后走向工作点。','D7 commuter reading room: ask the desk for repeatable contracts. The south path reaches the station stairs; commuters walk, wait, ride and continue to work.'),
 'commute.intro': ('通勤样品使用既有南北线：校园站—中央换乘—南商业区。白天前往工作点，傍晚返程；按列车真实时刻候车，每车最多两名通勤 NPC，并为玩家保留空位。','The commuter sample uses the existing north-south railway: Campus, Central Transfer and South Commercial. Daytime work trips and evening returns follow the real timetable. Each car admits at most two commuters and leaves player capacity.'),
 'commute.ask': ('你的通勤安排？','Ask about the commute'),
 'commute.plan': ('我的日常往返地点是「%s」和「%s」。\n当前：%s。\n\n我会走站内楼梯、等正确方向的列车，到站后下车去工作点；晚上再返回。','My daily route links %s and %s.\nCurrent activity: %s.\n\nI walk the station stairs, wait for the correct direction, ride the train and continue to work. I return in the evening.'),
 'commute.phase.to_platform': ('步行前往站台','Walking to the platform'),
 'commute.phase.waiting': ('在站台等候列车','Waiting for the train'),
 'commute.phase.riding': ('正在乘车','Riding the train'),
 'commute.phase.to_work': ('已下车，步行去工作点','Walking from the train to work'),
 'commute.phase.working': ('工作或休息中','Working or resting'),
 'quest.item_supply': ('交付物资：%s × %s','Deliver %s × %s'),
 'quest.ride_required': ('乘车要求：接取后实际乘南北线「%s → %s」，到站下车。','Transport requirement: after accepting, ride the north-south service from %s to %s and alight.'),
 'quest.repeat_interval': ('可重复：完成后冷却 %s 分钟。','Repeatable: available again %s minutes after completion.'),
 'quest.repeat_wait': ('%s（%s 秒后可再接）','%s (ready again in %s seconds)'),
 'quest.repeat_cooldown': ('下次可接取：%s 秒后；离开或重新交谈不会清空冷却。','Available again in %s seconds; leaving or reopening dialogue does not reset the cooldown.'),
 'quest.repeat_summary': ('可重复委托累计完成：%s 次','Repeatable contracts completed: %s'),
 'quest.ride_done': ('● 实际乘车与到站下车已登记','● Real ride and alighting recorded'),
 'quest.ride_wait': ('○ 尚未完成指定乘车与下车','○ Complete the required journey and alight'),
 'quest.ride_recorded': ('乘车记录已更新，可前往目的地服务员交接。','Journey recorded. Report to the destination desk.'),
})
ROLES.update({
 'campus_desk': ('校园通勤阅览员','Campus commuter librarian'),
 'central_desk': ('中央站通勤服务员','Central commuter agent'),
 'office_desk': ('南商业区事务员','South Commercial office agent'),
})
QUESTS.update({
 'repeat_archive': ('循环委托 · 阅览补纸','Repeat / reading-room paper','图书馆需要向校园通勤阅览室送去 4 张纸。交接后可赚取学园币；两分钟后可以再次接取。','Deliver four paper from the library to the campus commuter reading room. Earn city coins; this contract returns after two minutes.'),
 'repeat_central': ('循环委托 · 通勤资料袋','Repeat / commuter documents','带 2 张纸，从校园站实际乘南北线到中央换乘站并下车，再到站外通勤服务室交接。','Bring two paper, ride the north-south train from Campus to Central Transfer, alight and report to the commuter service room outside the station.'),
 'repeat_office': ('循环委托 · 共创室文件','Repeat / coworking documents','带 2 张纸，实际乘南北线从中央换乘站到南商业区并下车，交给站口共创室事务员。','Bring two paper, ride from Central Transfer to South Commercial and alight, then report to the coworking room agent.'),
 'repeat_return': ('循环委托 · 返校联络','Repeat / return to campus','从南商业区实际乘车回校园站并下车，向校园通勤阅览员登记返校联络。','Ride from South Commercial back to Campus, alight and report to the campus commuter librarian.'),
 'repeat_cafe': ('循环委托 · 咖啡原料','Repeat / cafe supplies','从西亭接取补货单，向东亭咖啡店交付 1 个可可豆。无需重复触发一次性差事。','Take a restocking request from West Kiosk and deliver one cocoa bean to East Cafe.'),
 'repeat_wind': ('循环委托 · 风场日常巡检','Repeat / wind-farm inspection','到风场接入步道的巡检点查看，再回工程师处交接。每次接取都需重新到访。','Visit the wind-farm access inspection point and return to the engineer. Each new contract requires another visit.'),
})
for key,zh,en in [
 ('student_intern','通勤学生 · 见习','Student intern'),('campus_reader','通勤学生 · 阅览','Commuting reader'),
 ('station_technician','通勤技术员','Commuting technician'),('visiting_teacher','通勤教师','Visiting teacher'),
 ('office_commuter','通勤职员','Office commuter'),('campus_assistant','通勤助教','Campus assistant')]:
    TEXT['commute.person.'+key]=(zh,en)

def main():
    text=dict(TEXT)
    for key,pair in ROLES.items(): text['npc.'+key]=pair
    for key,(zh,en,zhd,end) in PROFILES.items():
        text['esper.'+key+'.name']=(zh,en);text['esper.'+key+'.description']=(zhd,end)
    for key,(zh,en,zhd,end) in QUESTS.items():
        text['quest.'+key+'.name']=(zh,en);text['quest.'+key+'.brief']=(zhd,end)
    for key,pair in LOOKS.items(): text['resident.'+key]=pair
    for index,pair in enumerate(DISTRICTS): text['district.'+str(index)]=pair
    for index,locale in enumerate(('zh_cn','en_us')):
        path=ROOT/(locale+'.json');data=json.loads(path.read_text())
        data.update({'academy.city.'+key:pair[index] for key,pair in text.items()})
        path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
    print(f'Localized {len(text)} city interaction keys in zh_cn / en_us')

if __name__=='__main__': main()
