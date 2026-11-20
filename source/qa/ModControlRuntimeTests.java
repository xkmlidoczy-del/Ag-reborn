package cn.academy.port.gametest;

import cn.aca.infinity.*;
import cn.aca.omnitrix.*;
import cn.academy.port.canon.CanonPuppets;
import cn.academy.port.canon.CanonSkillsCombatA;
import cn.academy.port.highpower.HighPowerCombat;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("academy_cap_break")
@PrefixGameTestTemplate(false)
public final class ModControlRuntimeTests {
    private static Entity spawn(GameTestHelper h, String id, Vec3 at) {
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(id)).orElseThrow();
        var entity = type.create(h.getLevel());
        entity.moveTo(at, 0, 0);
        entity.setNoGravity(true);
        h.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static Method method(Class<?> type, String name, Class<?>... args) throws ReflectiveOperationException {
        Method method = type.getDeclaredMethod(name, args);
        method.setAccessible(true);
        return method;
    }

    private static void fixtureFloor(GameTestHelper h) {
        for (int x = 1; x < 13; x++) for (int z = 1; z < 17; z++)
            h.setBlock(x, 2, z, net.minecraft.world.level.block.Blocks.STONE);
    }

    @GameTest(template="empty",batch="ignis_hp_scaling",timeoutTicks=60)
    public static void ignisAttacksAgainstShieldedAndUnshieldedPlayer(GameTestHelper h) throws Exception {
        var f = new CanonExpansionARuntimeTests.Fixture(h,"mental_out","mental_puppet");
        f.loadChunks();
        var p = f.p;
        var inv = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
        inv.setAccessible(true); inv.setInt(p, 0);
        var boss = (Mob)spawn(h, "cataclysm:ignis", p.position().add(0, 0, 3));
        class Raw {
            final List<Float> values = new ArrayList<>();
            @SubscribeEvent public void observe(LivingIncomingDamageEvent event) {
                if (event.getEntity() == p && event.getSource().getEntity() == boss) values.add(event.getAmount());
            }
        }
        var hits = new Raw();
        NeoForge.EVENT_BUS.register(hits);
        try {
            p.setHealth(p.getMaxHealth());
            h.assertTrue(p.getMaxHealth() == 20_000_000 && HighPowerCombat.shield(p).signum() > 0, "full LV5 pools");
            var clazz = boss.getClass();
            var animation = clazz.getField("POKED_ATTACK").get(null);
            clazz.getMethod("setAnimation", animation.getClass()).invoke(boss, animation);
            clazz.getMethod("setAnimationTick", int.class).invoke(boss, 14);
            h.assertTrue(p.startRiding(boss,true), "actual Ignis rider mounted");
            double rawGrip = 4f + boss.getMaxHealth() * .02f;
            float beforeHp = p.getHealth();
            double beforeShield = HighPowerCombat.shield(p).doubleValue();
            p.invulnerableTime = 0;
            hits.values.clear();
            boss.positionRider(p);
            double shieldCost = beforeShield - HighPowerCombat.shield(p).doubleValue();
            h.assertTrue(hits.values.size() == 1 && Math.abs(hits.values.get(0)-rawGrip)<.01
                    && shieldCost > 0 && shieldCost <= rawGrip + .1 && p.getHealth() == beforeHp, "grip debits one shield hit " + hits.values + " cost " + shieldCost);
            p.stopRiding();
            p.invulnerableTime=0;
            p.hurt(p.damageSources().mobAttack(boss), HighPowerCombat.shield(p).floatValue());
            h.assertTrue(HighPowerCombat.shield(p).signum()==0, "base shield drained");
            beforeHp = p.getHealth();
            hits.values.clear();
            p.invulnerableTime=0;
            var fireball = (Projectile)spawn(h,"cataclysm:ignis_fireball",p.position().add(0,5,12));
            fireball.setOwner(boss);
            fireball.getClass().getMethod("setFired",boolean.class).invoke(fireball,true);
            method(fireball.getClass(),"onHitEntity",EntityHitResult.class).invoke(fireball,new EntityHitResult(p));
            double fireRaw = 6f + boss.getMaxHealth() * .07f;
            float hpCost=beforeHp-p.getHealth();
            h.assertTrue(!hits.values.isEmpty() && Math.abs(hits.values.getFirst()-fireRaw)<.01 && Math.abs(hpCost-fireRaw)<=2,
                    "native HP delta for shieldless fireball " + hits.values + " HP " + hpCost);
            hits.values.clear(); p.invulnerableTime=0;
            var abyss = (Projectile)spawn(h,"cataclysm:ignis_abyss_fireball",p.position().add(0,5,12));
            abyss.setOwner(boss);
            abyss.getClass().getMethod("setFired",boolean.class).invoke(abyss,true);
            method(abyss.getClass(),"onHitEntity",EntityHitResult.class).invoke(abyss,new EntityHitResult(p));
            double abyssRaw = 10f + boss.getMaxHealth() * .2f;
            h.assertTrue(!hits.values.isEmpty() && Math.abs(hits.values.getFirst()-abyssRaw)<.01,"abyss projectile " + hits.values);
            var flame=spawn(h,"cataclysm:flame_strike",p.position());
            var fc=flame.getClass();
            fc.getMethod("setOwner",LivingEntity.class).invoke(flame,boss);
            fc.getMethod("setDamage",float.class).invoke(flame,5f);
            fc.getMethod("setHpDamage",float.class).invoke(flame,3f);
            p.invulnerableTime=0; hits.values.clear();
            method(fc,"damage",LivingEntity.class).invoke(flame,p);
            float flameRaw=5f + boss.getMaxHealth()*.01f*3f;
            h.assertTrue(!hits.values.isEmpty() && Math.abs(hits.values.getFirst()-flameRaw)<.01,"Ignis flame column " + hits.values);
            var ordinaryPlayer=new CanonExpansionARuntimeTests.Fixture(h);
            float ordinary;
            try {
                var normal=ordinaryPlayer.p;
                inv.setInt(normal,0);
                normal.moveTo(boss.position());
                h.assertTrue(normal.getMaxHealth()==20, "untrained player has 20 HP");
                normal.setHealth(20);
                clazz.getMethod("setAnimation",animation.getClass()).invoke(boss,animation);
                clazz.getMethod("setAnimationTick",int.class).invoke(boss,14);
                h.assertTrue(normal.startRiding(boss,true),"ordinary player grabbed");
                normal.setHealth(20); normal.invulnerableTime=0;
                boss.positionRider(normal);
                ordinary=20-normal.getHealth();
                h.assertTrue(Math.abs(ordinary-4.4)<.01,"ordinary 20 HP grip "+ordinary);
                normal.stopRiding();
            } finally { ordinaryPlayer.close(); flame.discard(); }
            System.out.printf("CONTROL_QA Ignis flameRaw=%.2f ordinaryGripLoss=%.2f%n",flameRaw,ordinary);
            System.out.printf("CONTROL_QA Ignis maxHP=%.1f playerMaxHP=%.1f gripRaw=%.2f shieldSpent=%.2f fireRaw=%.2f hpLost=%.2f abyssRaw=%.2f%n",
                    boss.getMaxHealth(),p.getMaxHealth(),rawGrip,shieldCost,fireRaw,hpCost,abyssRaw);
            h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(hits); boss.discard(); f.close(); }
    }

    @GameTest(template="empty",batch="academy_ignis_puppet",timeoutTicks=90)
    public static void academyCommandsActualIgnis(GameTestHelper h) throws Exception {
        fixtureFloor(h);
        var f = new CanonExpansionARuntimeTests.Fixture(h,"mental_out","mental_puppet");
        f.loadChunks();
        var p=f.p;
        var boss = (Mob)spawn(h,"cataclysm:ignis",p.position().add(0,0,6));
        var rival = (Mob)spawn(h,"cataclysm:ignited_revenant",boss.position().add(2,0,1));
        rival.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);
        rival.setHealth(rival.getMaxHealth());
        rival.getClass().getMethod("setShieldDurability",int.class).invoke(rival,4);
        class Strikes {
            final List<Float> hits=new ArrayList<>();
            @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) {
                if(event.getSource().getEntity()==boss && event.getEntity()==rival) hits.add(event.getAmount());
            }
        }
        var strikes=new Strikes(); NeoForge.EVENT_BUS.register(strikes);
        boss.setTarget(p);
        try {
            h.assertTrue(CanonSkillsCombatA.cast(p,"mental_puppet") && CanonPuppets.isPuppet(boss), "paid Academy cast picks live boss");
            boss.setTarget(p);
            h.assertTrue(boss.getTarget()!=p, "native AI cannot retarget caster");
            var before=HighPowerCombat.shield(p);
            p.hurt(p.damageSources().mobAttack(boss),600);
            h.assertTrue(HighPowerCombat.shield(p).equals(before),"boss cannot spend master's shield");
            h.runAfterDelay(38,()->{
                try {
                    h.assertTrue(boss.getTarget()==rival && rival.getHealth()<2000, "Ignis obeyed and dealt damage to modded rival, target=" + boss.getTarget() + " hits="+strikes.hits+" atk="+boss.getAttributeValue(Attributes.ATTACK_DAMAGE)+" shield="+HighPowerCombat.shield(rival)+" hp=" + rival.getHealth() + " distance=" + boss.distanceTo(rival) + " reach=" + boss.isWithinMeleeAttackRange(rival) + " LOS=" + boss.hasLineOfSight(rival) + " owner=" + CanonPuppets.isPuppet(boss) + " positions=" + boss.position() + " / " + rival.position());
                    System.out.println("CONTROL_QA Academy Ignis actual rival=" + rival.getType() + " rivalHP=" + rival.getHealth());
                    h.succeed();
                } finally { NeoForge.EVENT_BUS.unregister(strikes); rival.discard(); boss.discard(); f.close(); }
            });
        } catch (Throwable t) { NeoForge.EVENT_BUS.unregister(strikes); rival.discard(); boss.discard(); f.close(); throw t; }
    }

    @GameTest(template="empty",batch="stone_boss_control",timeoutTicks=80)
    public static void stoneConvertsAndStopsAnimatedBoss(GameTestHelper h) {
        fixtureFloor(h);
        var f=new CanonExpansionARuntimeTests.Fixture(h,"mental_out","mental_puppet");
        f.loadChunks(); var p=f.p;
        var boss=(Mob)spawn(h,"cataclysm:ignis",p.position().add(0,0,7));
        var wearer=AcaInfinity.wearer(p);
        wearer.gauntlet=InfinityGauntletItem.createFullStack();
        wearer.updateData(GauntletData.FULL.withSelected(Stone.MIND));
        var rival=(Mob)spawn(h,"cataclysm:ignited_revenant",boss.position().add(2,0,1));
        rival.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);
        rival.setHealth(2000);
        StoneAbilities.castPrimary(p);
        var state=AcaInfinity.status(boss);
        h.assertTrue(state.isMindControlled(h.getLevel().getGameTime()) && p.getUUID().equals(state.mindMaster), "Mind beam controls boss");
        boss.setTarget(p);
        h.assertTrue(boss.getTarget()==rival,"rejected retarget and selected real rival");
        h.runAfterDelay(5,()->{
            h.assertTrue(rival.getHealth()<2000,"stone puppet dealt damage");
            boss.moveTo(p.position().add(0,0,8));
            // Allow a second cast of roll-call on the same owner's puppet as well as fresh bosses.
            var remnant=(Mob)spawn(h,"cataclysm:ancient_remnant",p.position().add(0,0,5));
            StoneAbilities.castSecondary(p);
            h.assertTrue(AcaInfinity.status(remnant).isStandingAttention(h.getLevel().getGameTime()),"roll-call marks boss");
            var pos=remnant.position();
            class Ticks {
                int count;
                @SubscribeEvent public void post(net.neoforged.neoforge.event.tick.EntityTickEvent.Post e) {
                    if (e.getEntity()==remnant) count++;
                }
            }
            var ticks=new Ticks(); NeoForge.EVENT_BUS.register(ticks);
            var shield=HighPowerCombat.shield(p);
            p.hurt(p.damageSources().mobAttack(remnant),500);
            h.assertTrue(HighPowerCombat.shield(p).equals(shield),"scripted boss hits blocked during stand");
            h.runAfterDelay(6,()->{
                h.assertTrue(remnant.position().equals(pos) && ticks.count==0,"custom boss tick held; executed ticks=" + ticks.count);
                AcaInfinity.status(remnant).mindStandUntil=h.getLevel().getGameTime();
                h.runAfterDelay(3,()->{
                    try {
                        h.assertTrue(ticks.count>0,"boss ticking resumed on expiry");
                        var action=new InfinityNetwork.ActionPayload(InfinityNetwork.ACT_TOGGLE_GAUNTLET,0,0);
                        GauntletService.handleAction(p,action);
                        h.assertTrue(!wearer.equipped(),"gauntlet removed");
                        GauntletService.handleAction(p,action);
                        h.assertTrue(wearer.equipped() && wearer.data().equals(GauntletData.FULL.withSelected(Stone.MIND)),"all sockets and selection survived");
                        System.out.println("CONTROL_QA stone Ignis puppet damaged rival; ancient_remnant held and resumed; full gauntlet removed and re-equipped");
                        h.succeed();
                    } finally { NeoForge.EVENT_BUS.unregister(ticks); remnant.discard(); rival.discard(); boss.discard(); f.close(); }
                });
            });
        });
    }

    @GameTest(template="empty",batch="separate_watch_charge",timeoutTicks=40)
    public static void xWatchDoesNotConsumeAnotherWatchCharge(GameTestHelper h) {
        var f=new CanonExpansionARuntimeTests.Fixture(h);
        try {
            var p=f.p;
            var wrist=Omnitrix.wrist(p);
            var old=wrist.serializeNBT(p.registryAccess());
            long classicReady=WristService.time(p)+900;
            old.putLong("equipReadyAt",classicReady);
            wrist.deserializeNBT(p.registryAccess(),old);
            h.assertTrue(!WristService.equip(p,new ItemStack(Omnitrix.watch(Kind.CLASSIC))),"classic still charging");
            h.assertTrue(WristService.equip(p,new ItemStack(Omnitrix.watch(Kind.CELESTIAL))),"can equip X during classic cooldown");
            var saved=wrist.serializeNBT(p.registryAccess());
            saved.putString("stage","IDLE"); saved.putLong("until",0);
            wrist.deserializeNBT(p.registryAccess(),saved);
            h.assertTrue(WristService.start(p,Form.GHOSTFREAK,-1),"X can transform while old classic charge runs");
            WristService.forceHumanForAscalon(p);
            h.assertTrue(WristService.unequip(p,false),"X can be removed immediately after a forced revert");
            h.assertTrue(!WristService.equip(p,new ItemStack(Omnitrix.watch(Kind.CLASSIC)))
                    && wrist.serializeNBT(p.registryAccess()).getLong("equipReadyAt")==classicReady,"classic keeps original deadline after X removal");
            h.succeed();
        } finally { f.close(); }
    }

    @GameTest(template="empty",batch="x_watch_control",timeoutTicks=180)
    public static void unlimitedXWatchAndSolitaryPossession(GameTestHelper h) throws Exception {
        fixtureFloor(h);
        var f=new CanonExpansionARuntimeTests.Fixture(h,"mental_out","mental_puppet");
        f.loadChunks(); var p=f.p;
        var wrist=Omnitrix.wrist(p);
        // Headless mock channels send no movement packets and do not drive ServerPlayer.doTick.
        h.onEachTick(()->NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p)));
        h.assertTrue(WristService.equip(p,new ItemStack(Omnitrix.watch(Kind.CELESTIAL))),"X equip");
        var old=wrist.serializeNBT(p.registryAccess());
        old.putInt("version",1);
        old.putString("stage","COOLING");
        old.putLong("until",WristService.time(p)+1200);
        old.putLong("equipReadyAt",WristService.time(p)+1200);
        wrist.deserializeNBT(p.registryAccess(),old);
        h.assertTrue(wrist.stage()==Wrist.Stage.IDLE && WristService.start(p,Form.GHOSTFREAK,-1),"old charge clears immediately");
        h.runAfterDelay(52,()->{
            h.assertTrue(wrist.stage()==Wrist.Stage.ACTIVE && wrist.until()==0,"X active without deadline: stage=" + wrist.stage() + " until=" + wrist.until() + " now=" + WristService.time(p) + " pos=" + p.position() + " kind=" + wrist.kind());
            long now=WristService.time(p);
            var saved=wrist.serializeNBT(p.registryAccess());
            saved.putLong("began",now-3700); saved.putLong("until",now-100);
            saved.putDouble("dnaShield",12345); saved.putLong("dnaLastHit",now);
            wrist.deserializeNBT(p.registryAccess(),saved);
            h.assertTrue(wrist.until()==0 && wrist.serializeNBT(p.registryAccess()).getDouble("dnaShield")==12345,"legacy expired form migrates, partial shield retained");
            p.setYRot(0); p.setXRot(0); p.setYHeadRot(0);
            var boss=(Mob)spawn(h,"cataclysm:ignis",p.position().add(0,0,7));
            h.assertTrue(Abilities.cast(p,wrist,false,now),"X skill accepted past the original deadline");
            h.runAfterDelay(23,()->{
                h.assertTrue(wrist.stage()==Wrist.Stage.ACTIVE && OmnitrixAiControl.isPuppetOf(boss,p.getUUID()),
                        "Ghostfreak actually possessed the solitary mod boss and stayed in form");
                boss.setTarget(p);
                h.assertTrue(boss.getTarget()!=p,"solitary puppet guards owner");
                var rival=(Mob)spawn(h,"cataclysm:ignited_revenant",boss.position().add(1,0,2));
                rival.getAttribute(Attributes.MAX_HEALTH).setBaseValue(6000); rival.setHealth(6000);
                h.runAfterDelay(18,()->{
                    h.assertTrue(boss.getTarget()==rival || !rival.isAlive(),"solitary puppet engages newly arrived rival");
                    h.assertTrue(rival.getHealth()<6000,"real rival lost health");
                    try {
                        method(WristService.class,"request",ServerPlayer.class,WristNetwork.Request.class)
                                .invoke(null,p,new WristNetwork.Request(WristNetwork.REVERT,0));
                    } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                    h.assertTrue(wrist.stage()==Wrist.Stage.REVERTING,"normal manual revert");
                    h.runAfterDelay(12,()->{
                        try {
                            h.assertTrue(wrist.stage()==Wrist.Stage.IDLE && WristService.start(p,Form.ALIEN_X,-1),
                                    "immediate next X transform without charge");
                            System.out.println("CONTROL_QA X old cooldown and expired save migrated; Ghostfreak possessed lone Ignis, protected master and attacked new rival; X retransform accepted");
                            h.succeed();
                        } finally { rival.discard(); boss.discard(); f.close(); }
                    });
                });
            });
        });
    }
}
