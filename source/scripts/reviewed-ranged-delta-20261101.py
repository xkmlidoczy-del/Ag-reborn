"""Strict, user-authorized 2026-11-01 gameplay reach delta over the genuine pinned predecessor.
Frozen render runners and historical hashes remain unchanged. No reconstructed Git objects.
"""
import hashlib
from pathlib import Path
TARGET='src/main/java/cn/academy/port/skill/RailgunDamage.java'
FIXTURE='src/test/resources/ranged-20261101/RailgunDamage.before.java.txt'
RULES='src/main/java/cn/academy/port/core/RangedSkillRanges.java'
PREVIOUS_RULES='src/test/resources/ranged-20261101/RangedSkillRanges.before-a1.java.txt'
TUNING='src/main/resources/academy-skill-tuning.tsv'
REBALANCED_RULE_HASH='8a7c216b1dc9d891857040fecf21d3d9a585c987db7bfd0fa3d93c70258cb783'
BEFORE='d57ef49a7941b2325d399726867267df2dc2b5f1c80aecf262640111c81d6a32'
ENVIRONMENT_CURRENT='464167813f2109fcd4406207618526b783da458535e73bff35ff4beb3d4f0b1b'
CURRENT='de55fd94c1313ff3acbff629f5ea2e614dd9e31f29e393a54bdf2a4cc865a65d'
RULE_HASH='26cfb63f295ee4d05c818d5b6928c0e7586cade49d3e7bd6dc867c4bc1999a3c'
def sha(data):return hashlib.sha256(data).hexdigest()
def verify(root,expected):
    root=Path(root);original=(root/FIXTURE).read_bytes()
    assert expected==BEFORE and sha(original)==BEFORE,'reviewed range predecessor changed'
    current_rule_hash = sha((root/RULES).read_bytes())
    reach = 64
    if current_rule_hash != RULE_HASH:
        assert sha((root/PREVIOUS_RULES).read_bytes()) == RULE_HASH, 'reviewed historic range table changed'
        assert current_rule_hash == REBALANCED_RULE_HASH, 'reviewed range table changed'
        rows = [line.split('\t') for line in (root/TUNING).read_text().splitlines()
                if line and not line.startswith('#')]
        railgun = [row for row in rows if row[0] == 'railgun']
        assert len(railgun) == 1 and railgun[0].count('reach=80') == 1, 'reviewed railgun reach changed'
        reach = 80
    text=original.decode();changes=[
      ('slope.scale(50)','slope.scale(cn.academy.port.core.RangedSkillRanges.fixed("railgun"))'),
      ('beamLength=45','beamLength=cn.academy.port.core.RangedSkillRanges.fixed("railgun")'),
      ('projection>50','projection>cn.academy.port.core.RangedSkillRanges.fixed("railgun")'),
      ('step<=50&&remaining>0','step<(int)Math.ceil(cn.academy.port.core.RangedSkillRanges.fixed("railgun")*(Math.abs(slope.x)+Math.abs(slope.y)+Math.abs(slope.z)))+4&&remaining>0'),
      ('player.getEyePosition().add(slope.scale(.1))','player.getEyePosition()'),
      ('hit.distSqr(pos)>maxDistance||!world.hasChunkAt(hit)','hit.distSqr(pos)>maxDistance||Vec3.atCenterOf(hit).subtract(start).dot(slope)>cn.academy.port.core.RangedSkillRanges.fixed("railgun")||!world.hasChunkAt(hit)')]
    for before,after in changes:
        assert text.count(before)==1,'reviewed range patch preimage mismatch: '+before
        text=text.replace(before,after)
    assert sha(text.encode()) == CURRENT, 'reviewed A1 railgun baseline changed'
    actual=(root/TARGET).read_bytes()
    environment_changes = [
        ('if(state.isAir())continue;', 'if(state.isAir()||SkillEnvironment.isVacancy(state))continue;'),
        ('import cn.academy.port.AcademyConfig;\n', ''),
        ('import net.neoforged.neoforge.event.level.BlockEvent;\n', ''),
        ('import cn.academy.port.api.SkillReflectEvent;\n', 'import cn.academy.port.api.SkillReflectEvent;\nimport cn.academy.port.highpower.environment.SkillEnvironment;\n'),
        ('        if(!AcademyConfig.contextTerrain(world,"electromaster","railgun")||!world.mayInteract(player,player.blockPosition()))return new Result(beamLength,reflectedHit);\n', '        if(player.serverLevel()!=world||!world.mayInteract(player,player.blockPosition()))return new Result(beamLength,reflectedHit);\n        var terrain=SkillEnvironment.begin(player,"electromaster.railgun",4);\n        if(!terrain.allowsBlockChanges())return new Result(beamLength,reflectedHit);\n'),
        ('                var event=new BlockEvent.BreakEvent(world,hit,state,player);if(NeoForge.EVENT_BUS.post(event).isCanceled())break;\n                world.destroyBlock(hit,world.random.nextFloat()<.05,player);remaining-=hardness;\n', '                if(!terrain.breakBlock(hit,()->world.random.nextFloat()<.05))break;\n                remaining-=hardness;\n'),
    ]
    environment_delta = actual != text.encode()
    if environment_delta:
        for before, after in environment_changes:
            assert text.count(before) == 1, 'reviewed environment adapter preimage changed'
            text = text.replace(before, after)
    expected_current = ENVIRONMENT_CURRENT if environment_delta else CURRENT
    assert actual==text.encode() and sha(actual)==expected_current,'reviewed 20261101 ranged source changed'
    return {'source':TARGET,'genuinePredecessor':FIXTURE,'predecessorSha256':BEFORE,'currentSha256':expected_current,
            'rangeTableSha256':current_rule_hash,'historicRangeTableSha256':RULE_HASH,'exactReplacements':len(changes),
            'environmentReplacements':len(environment_changes) if environment_delta else 0,
            'behavior':f'{reach}-block trace and visual; common eye origin; damage formula, reflection and payment preserved; energy charged only for committed terrain writes',
            'authorization':'User approved A1 range and the A2 environment policy adapter'}
