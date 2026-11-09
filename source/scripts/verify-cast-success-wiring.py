#!/usr/bin/env python3
"""Exact source contract for server-confirmed cast presentation. No game or Gradle launch."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]/'src/main/java/cn/academy/port'
def source(p):return (r/p).read_text()
s=source('client/CastPoseClient.java')
assert 'if (input == 0 && ZERO_INPUT_TAPS.contains(node.skill))' in s
assert 'PENDING_TAPS.put(node.skill' in s and 'PendingTap pending=PENDING_TAPS.remove(skill)' in s
assert 'pending.epoch()!=AcademyClient.inputSessionEpoch()' in s and 'now(mc)-pending.began()>60' in s
assert 'tag.getInt("entity")!=mc.player.getId()' in s
assert s.index('if (!RELEASES.accept(owner,skill,epoch,input,token)) return false') < s.index('CameraMotion.cast(skill,false)')
assert '(c.release < 0 || c.release > now)' in s
assert 'if (mc.screen != null || key == null || "railgun".equals(c.skill)) CameraMotion.cancel(c.skill)' in s
assert 'else CameraMotion.release(c.skill)' in s
ui=source('client/TacticalCoordinateClient.java')
assert 'TacticalCoordinateUiState.confirmedArrival(snapshot.phase,snapshot.reason,snapshot.moved)' in ui
assert 'snapshot.owner.equals(snapshot.movedOwner)' in ui
assert 'if (snapshot.input>0 && snapshot.epoch>0)' in ui
assert 'c.category=catalog.category(); c.level=catalog.level()' in source('client/CastPoseClient.java')
assert 'if (cast.category() != null && !cast.category().isEmpty()) return cast.category()' in source('client/CastSignatureDecor.java')
assert ui.index('screen.closeFromServer();',ui.index('private static void terminal')) < ui.index('if (arrived)',ui.index('private static void terminal'))
assert 'CastPoseClient.acceptedRelease("tactical_coordinate_formation",snapshot.actor,snapshot.owner' in ui
server=source('skill/LocationTeleport.java')
start=server.index('public static boolean perform(');end=server.index('public static boolean request(',start);body=server[start:end]
assert body.index('consumeWithForceSkill') < body.index('e.teleportTo(target') < body.index('var arrival=')
for key in ['arrival.putBoolean("success",true)','arrival.putString("dimension",p.serverLevel().dimension().location().toString())','PacketDistributor.sendToPlayersNear(p.serverLevel()']:
 assert key in body,key
assert body.count('var arrival=')==1
arrival=source('client/ConfirmedTeleportArrivalEffects.java')
for required in ['!t.getBoolean("success")','!mc.level.dimension().location().toString().equals(t.getString("dimension"))','!p.getUUID().equals(t.getUUID("entity_uuid"))','Double.isFinite(t.getDouble(k))','if(!CastPoseClient.acceptedRelease(']:assert required in arrival,required
for f in ['client/MagneticItemPullEffects.java','client/MagneticArrowSeparationEffects.java']:
 t=source(f);assert t.index('MARKERS.put(packet.owner(),new Marker(owner,packet));')<t.index('CastPoseClient.acceptedRelease(')
print('PASS server commit-before-arrival, owner/dimension/success/identity gates, exactly-once release, GUI confirm, tokenless pending, magnetic acceptance, activation retirement and normal key-up camera wiring')

# A failed native proof has a precise explanation; all safety gates remain in place.
import json
wind=source("client/WindPressureLiftClientMotion.java")
assert wind.count('if (!WindPressureLiftNativeTravel.ready(mc)) return prefix + "native_compat";')==2
assert 'if (!autoJumpReadable(player)) return prefix + "unavailable";' in wind
assert '!WindPressureLiftNativeSweep.unchanged(before,boundary(mc))' in wind
for language in ["zh_cn","zh_tw","en_us","ja_jp"]:
 values=json.loads((r.parents[3]/"resources/assets/academy/lang"/(language+".json")).read_text(encoding="utf-8"))
 assert len(values["academy.highpower.wind_pressure_lift.native_compat"])>20
print("PASS precise native compatibility refusal in all four languages; movement proof unchanged")
