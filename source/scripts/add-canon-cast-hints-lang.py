#!/usr/bin/env python3
"""Adds the ac.canon.refused.* / ac.canon.need.* action-bar texts used by CanonCastHints (idempotent)."""
from pathlib import Path
import json
LANG = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/academy/lang'
T = {  # key: (zh_cn, zh_tw, en_us, ja_jp)
 'refused.inactive': ('%s 未释放：请先激活能力（默认 V 键）', '%s 未施放：請先啟用能力（預設 V 鍵）', '%s not cast: activate your ability first (V by default)', '%s 不発：先に能力を有効化してください（既定は V キー）'),
 'refused.cooldown': ('%s 冷却中，还需 %s 秒', '%s 冷卻中，還需 %s 秒', '%s is cooling down: %s s left', '%s はクールダウン中：残り %s 秒'),
 'refused.unsupported': ('%s 未释放：%s', '%s 未施放：%s', '%s not cast: %s', '%s 不発：%s'),
 'refused.needs': ('%s 未释放 · 需要：%s', '%s 未施放 · 需要：%s', '%s not cast · needs: %s', '%s 不発・必要：%s'),
 'need.separator': ('、', '、', ', ', '、'),
 'need.cp': ('足够的 CP', '足夠的 CP', 'enough CP', '十分な CP'),
 'need.aim_hostile': ('准星对准视线内的敌对生物', '準星對準視線內的敵對生物', 'crosshair on a visible hostile', '照準を見えている敵対モブに合わせる'),
 'need.aim_mob': ('准星对准一只非玩家生物', '準星對準一隻非玩家生物', 'crosshair on a non-player creature', '照準をプレイヤー以外の生物に合わせる'),
 'need.hostiles_near': ('附近有可见的敌对生物', '附近有可見的敵對生物', 'visible hostiles nearby', '近くに見えている敵対モブ'),
 'need.hostiles_ahead': ('前方有可见的敌对生物', '前方有可見的敵對生物', 'visible hostiles in front', '前方に見えている敵対モブ'),
 'need.two_hostiles': ('视线内至少两个敌对生物', '視線內至少兩個敵對生物', 'at least two visible hostiles', '見えている敵対モブ2体以上'),
 'need.navigating': ('目标正在移动', '目標正在移動', 'a target that is moving', '移動中の対象'),
 'need.pursuing': ('有敌人正在追击你', '有敵人正在追擊你', 'a hostile chasing you', 'あなたを追っている敵'),
 'need.incoming_arrow': ('前方 6 格内有箭正朝你飞来', '前方 6 格內有箭正朝你飛來', 'an arrow within 6 blocks in front, flying at you', '前方 6 ブロック以内に自分へ飛んでくる矢'),
 'need.ground': ('站在地面上', '站在地面上', 'standing on the ground', '地面に立っている'),
 'need.airborne': ('处于空中', '處於空中', 'being in the air', '空中にいる'),
 'need.falling': ('正在下落', '正在下落', 'falling', '落下中'),
 'need.moving': ('正在快速移动', '正在快速移動', 'moving at speed', '速く移動中'),
 'need.dry': ('不在水或岩浆中', '不在水或岩漿中', 'not in water or lava', '水・溶岩の外にいる'),
 'need.survival': ('非创造/旁观模式且不能飞行', '非創造/旁觀模式且不能飛行', 'survival mode without flight', '飛行不可のサバイバルモード'),
 'need.shield': ('护盾值大于 0', '護盾值大於 0', 'shield above 0', 'シールドが 0 より多い'),
 'need.shell': ('氮气装甲已开启', '氮氣裝甲已開啟', 'Nitrogen Armor active', '窒素装甲を展開中'),
 'need.wings': ('未元之翼已展开', '未元之翼已展開', 'Dark Wings active', '未元の翼を展開中'),
 'need.ally_sneak': ('准星对准正在潜行的队友', '準星對準正在潛行的隊友', 'crosshair on a sneaking teammate', '照準をしゃがんでいる味方に合わせる'),
 'need.thrown_item': ('附近有你自己丢出的物品', '附近有你自己丟出的物品', 'an item you threw nearby', '自分が投げたアイテムが近くにある'),
 'need.floating_item': ('先用浮游托起物品', '先用浮游托起物品', 'an item you are already floating', '浮遊中のアイテム'),
 'need.crystal': ('背包有未元物质晶体、主手拿着要复制的物品', '背包有未元物質晶體、主手拿著要複製的物品', 'a Dark Matter Crystal in the inventory and the item to copy in your main hand', 'インベントリに未元物質結晶、メインハンドに複製するアイテム'),
 'need.water_near': ('5 格内有水源方块', '5 格內有水源方塊', 'a water source within 5 blocks', '5 ブロック以内の水源'),
 'need.water_aim': ('先对准水源再对准空位', '先對準水源再對準空位', 'aim at a water source, then at an empty spot', '水源を狙い、次に空きマスを狙う'),
 'need.target_in_water': ('目标在水中', '目標在水中', 'a target in water', '水中の対象'),
 'need.in_water': ('自己在水中', '自己在水中', 'being in water', '自分が水中にいる'),
 'need.fire_block': ('准星对准火、营火或蜡烛', '準星對準火、營火或蠟燭', 'crosshair on fire, a campfire or a candle', '照準を火・焚き火・ろうそくに合わせる'),
 'need.on_fire': ('自己正在着火', '自己正在著火', 'being on fire', '自分が燃えている'),
 'need.farmland': ('准星对准干燥的耕地', '準星對準乾燥的耕地', 'crosshair on dry farmland', '照準を乾いた耕地に合わせる'),
 'need.medium': ('手持致密介质', '手持緻密介質', 'Dense Medium in hand', '高密度媒体を手に持つ'),
 'need.liquid_body': ('先用液影侦察召出液影', '先用液影偵察召出液影', 'a liquid body from Liquid Scout', '液影偵察で出した液体の体'),
 'need.body_hostile': ('液影附近有敌对生物', '液影附近有敵對生物', 'hostiles near the liquid body', '液体の体の近くに敵対モブ'),
 'need.body_lever': ('液影沿你视线方向 2.5 格内有拉杆，且你自己够得到它', '液影沿你視線方向 2.5 格內有拉桿，且你自己搆得到它', 'a lever within 2.5 blocks of the liquid body along your view, within your own reach', '視線方向で液体の体から 2.5 ブロック以内、かつ自分の手が届くレバー'),
 'need.body_arrow': ('有箭飞到液影 2 格内时按键', '有箭飛到液影 2 格內時按鍵', 'press when an arrow comes within 2 blocks of the liquid body', '矢が液体の体の 2 ブロック以内に来たときに押す'),
 'need.block_face': ('准星对准 6 格内可放置的方块面', '準星對準 6 格內可放置的方塊面', 'crosshair on a block face within 6 blocks', '照準を 6 ブロック以内の設置可能な面に合わせる'),
 'need.construct': ('准星对准自己造的纸构造物', '準星對準自己造的紙構造物', 'crosshair on your own paper construct', '照準を自分の紙の構造物に合わせる'),
 'need.aim_hostile.range': ('准星对准 %s 格内视线可见的敌对生物', '準星對準 %s 格內視線可見的敵對生物', 'crosshair on a visible hostile within %s blocks', '照準を %s ブロック以内の見えている敵対モブに合わせる'),
 'need.hostiles_near.range': ('%s 格内有可见的敌对生物', '%s 格內有可見的敵對生物', 'visible hostiles within %s blocks', '%s ブロック以内に見えている敵対モブ'),
 'need.hostiles_ahead.range': ('前方 %s 格内有可见的敌对生物', '前方 %s 格內有可見的敵對生物', 'visible hostiles within %s blocks in front', '前方 %s ブロック以内に見えている敵対モブ'),
 'need.body_hostile.range': ('液影 %s 格内有敌对生物', '液影 %s 格內有敵對生物', 'hostiles within %s blocks of the liquid body', '液体の体から %s ブロック以内に敵対モブ'),
 'need.not_on_you': ('目标当前没有在追你', '目標目前沒有在追你', 'a target not already chasing you', 'まだ自分を追っていない対象'),
 'need.second_hostile': ('目标 8 格内还有另一个它看得见的敌对生物', '目標 8 格內還有另一個它看得見的敵對生物', 'another hostile within 8 blocks that the target can see', '対象から 8 ブロック以内に、対象が見える別の敵対モブ'),
 'need.three_hostiles': ('附近至少 3 个互相看得见、相距 8 格内的敌对生物（交叉火力需 4 个）', '附近至少 3 個互相看得見、相距 8 格內的敵對生物（交叉火力需 4 個）', 'at least 3 hostiles within 8 blocks that can see each other (4 for Crossfire)', '互いに見える 8 ブロック以内の敵対モブ 3 体以上（クロスファイアは 4 体）'),
 'need.two_pursuing': ('另有两个敌人正在追你', '另有兩個敵人正在追你', 'two other hostiles chasing you', '自分を追う別の敵 2 体'),
 'need.ally_pursued': ('有敌人正在追这名队友', '有敵人正在追這名隊友', 'hostiles chasing that teammate', 'その味方を追っている敵'),
 'need.shield_missing': ('护盾未满', '護盾未滿', 'a shield that is not full', '満タンでないシールド'),
 'need.ledge': ('准星对准脚下前方有落差的边缘', '準星對準腳下前方有落差的邊緣', 'crosshair on a ledge with a drop ahead', '照準を前方の段差の縁に合わせる'),
 'need.paper.range': ('手持至少 %s 张纸', '手持至少 %s 張紙', 'at least %s paper in hand', '紙を %s 枚以上手に持つ'),
}
OBSOLETE = ['need.paper', 'need.lever']
for i, loc in enumerate(('zh_cn', 'zh_tw', 'en_us', 'ja_jp')):
    path = LANG / f'{loc}.json'; data = json.loads(path.read_text())
    for k in OBSOLETE: data.pop('ac.canon.' + k, None)
    for k, v in T.items(): data['ac.canon.' + k] = v[i]
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')
print('hint keys', len(T))
