package cn.academy.port.gametest;

import cn.aca.infinity.AcaInfinity;
import cn.aca.infinity.GauntletData;
import cn.aca.infinity.InfinityGauntletItem;
import cn.aca.infinity.Stone;
import cn.aca.infinity.StoneAbilities;
import cn.academy.port.canon.CanonPuppets;
import cn.academy.port.canon.CanonSkillsCombatA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("academy_modpack_control")
@PrefixGameTestTemplate(false)
public final class ModpackMindRuntimeTests {
    @GameTest(templateNamespace="academy_cap_break",template="empty",batch="legends_control")
    public static void lavaEater(GameTestHelper h) { command(h,"legendary_monsters:lava_eater"); }

    @GameTest(templateNamespace="academy_cap_break",template="empty",batch="titans_control")
    public static void zombieTitan(GameTestHelper h) { command(h,"thetitansneo:zombie_titan"); }

    private static void command(GameTestHelper h,String id) {
        var f = new CanonExpansionARuntimeTests.Fixture(h,"mental_out","mental_puppet");
        f.loadChunks();
        var owner=f.p;
        var boss=(Mob)BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(id)).orElseThrow().create(h.getLevel());
        try {
            boss.moveTo(owner.position().add(0,0,10),180,0);
            h.getLevel().addFreshEntity(boss);
            f.select("mental_probe");
            h.assertTrue(CanonSkillsCombatA.cast(owner,"mental_probe"),id+" deliberate mental probe before aggro");
            f.select("mental_puppet");
            boss.setTarget(owner);
            h.assertTrue(CanonSkillsCombatA.cast(owner,"mental_puppet") && CanonPuppets.isPuppet(boss),id+" paid Academy control");
            boss.setTarget(owner);
            h.assertTrue(boss.getTarget()!=owner,id+" cannot retarget controller");
            // The Titans Neo ignores discard(). Remove this isolated fixture before aiming at the next one.
            boss.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            var stoneBoss=(Mob)boss.getType().create(h.getLevel());
            try {
                stoneBoss.moveTo(owner.position().add(0,0,10),180,0);
                h.getLevel().addFreshEntity(stoneBoss);
                var glove=AcaInfinity.wearer(owner);
                glove.gauntlet=InfinityGauntletItem.createFullStack();
                glove.updateData(GauntletData.FULL.withSelected(Stone.MIND));
                StoneAbilities.castPrimary(owner);
                var state=AcaInfinity.status(stoneBoss);
                h.assertTrue(state.isMindControlled(h.getLevel().getGameTime()) && owner.getUUID().equals(state.mindMaster),id+" converted via beam");
                stoneBoss.setTarget(owner);
                h.assertTrue(stoneBoss.getTarget()!=owner,id+" stone stops targeting controller");
                StoneAbilities.castSecondary(owner);
                h.assertTrue(state.isStandingAttention(h.getLevel().getGameTime()) && !state.isMindControlled(h.getLevel().getGameTime()),id+" can order own puppet to stand");
                var watchBoss=(Mob)boss.getType().create(h.getLevel());
                try {
                    watchBoss.moveTo(owner.position().add(5,0,8),180,0);
                    h.getLevel().addFreshEntity(watchBoss);
                    h.assertTrue(cn.aca.omnitrix.OmnitrixAiControl.hijackOrSubjugate(owner,watchBoss,180,false,false)
                            && cn.aca.omnitrix.OmnitrixAiControl.isPuppetOf(watchBoss,owner.getUUID()),id+" wrist possession");
                    watchBoss.setTarget(owner);
                    h.assertTrue(watchBoss.getTarget()!=owner,id+" cannot attack wrist owner");
                } finally { watchBoss.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED); }
                System.out.println("MODPACK_CONTROL "+id+" neutral probe, Academy puppet, target veto, Mind Stone conversion and stand, watch possession confirmed");
                h.succeed();
            } finally { stoneBoss.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED); }
        } finally { boss.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED); f.close(); }
    }
}
