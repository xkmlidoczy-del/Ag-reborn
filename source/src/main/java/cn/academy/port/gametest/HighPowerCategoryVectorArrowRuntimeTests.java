package cn.academy.port.gametest;

import cn.academy.port.*;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.MultiAxisReflectionGeometry;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Normal category shields and actual native arrows. A base reversal alone
 * cannot satisfy any dedicated success; every refusal must retain base fallback. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerCategoryVectorArrowRuntimeTests {
    private static final String T="highpower_empty",B="highpower_category_vector_arrows";
    @GameTest(template=T,batch=B,timeoutTicks=100)public static void multi_native_contact_keeps_shooter_and_commits_its_own_quota(GameTestHelper h){exercise(h,"multi","paid");}
    @GameTest(template=T,batch=B,timeoutTicks=100)public static void parry_native_contact_commits_its_own_absorption_quota(GameTestHelper h){exercise(h,"parry","paid");}
    @GameTest(template=T,batch=B,timeoutTicks=100)public static void circle_native_contact_commits_its_own_absorption_quota(GameTestHelper h){exercise(h,"circle","paid");}
    @GameTest(template=T,batch=B,timeoutTicks=100)public static void multi_exhausted_quota_keeps_category_arrow_reversal(GameTestHelper h){exercise(h,"multi","quota");}
    @GameTest(template=T,batch=B,timeoutTicks=100)public static void parry_exhausted_quota_keeps_category_arrow_reversal(GameTestHelper h){exercise(h,"parry","quota");}
    @GameTest(template=T,batch=B,timeoutTicks=100)public static void circle_exhausted_quota_keeps_category_arrow_reversal(GameTestHelper h){exercise(h,"circle","quota");}
    @GameTest(template=T,batch=B,timeoutTicks=100)public static void multi_no_gravity_arrow_keeps_category_arrow_reversal(GameTestHelper h){exercise(h,"multi","unsupported");}
    @GameTest(template=T,batch=B,timeoutTicks=100)public static void parry_no_gravity_arrow_keeps_category_arrow_reversal(GameTestHelper h){exercise(h,"parry","unsupported");}
    @GameTest(template=T,batch=B,timeoutTicks=100)public static void circle_no_gravity_arrow_keeps_category_arrow_reversal(GameTestHelper h){exercise(h,"circle","unsupported");}
    @GameTest(template=T,batch=B,timeoutTicks=100)public static void multi_external_native_event_mutation_keeps_category_arrow_reversal(GameTestHelper h){exercise(h,"multi","mutated");}
    @GameTest(template=T,batch=B,timeoutTicks=100)public static void parry_external_native_event_mutation_keeps_category_arrow_reversal(GameTestHelper h){exercise(h,"parry","mutated");}
    @GameTest(template=T,batch=B,timeoutTicks=100)public static void circle_external_native_event_mutation_keeps_category_arrow_reversal(GameTestHelper h){exercise(h,"circle","mutated");}
    @GameTest(template=T,batch=B,timeoutTicks=100)public static void multi_rear_gap_keeps_category_arrow_reversal(GameTestHelper h){exercise(h,"multi","rear");}
    @GameTest(template=T,batch=B,timeoutTicks=100)public static void parry_rear_geometry_keeps_category_arrow_reversal(GameTestHelper h){exercise(h,"parry","rear");}
    @GameTest(template=T,batch=B,timeoutTicks=100)public static void circle_rear_geometry_keeps_category_arrow_reversal(GameTestHelper h){exercise(h,"circle","rear");}
    @GameTest(template=T,batch=B,timeoutTicks=100)public static void parry_real_arrow_type_fire_tag_keeps_category_arrow_reversal(GameTestHelper h){exercise(h,"parry","tags");}
    @GameTest(template=T,batch=B,timeoutTicks=100)public static void circle_real_arrow_type_fire_tag_keeps_category_arrow_reversal(GameTestHelper h){exercise(h,"circle","tags");}
    @GameTest(template=T,batch=B,timeoutTicks=100)public static void multi_true_center_gap_keeps_category_arrow_reversal(GameTestHelper h){exercise(h,"multi","gap");}

    private static void exercise(GameTestHelper h,String kind,String mode){
        Fixture f=new Fixture(h,kind);
        h.runAfterDelay(61,()->{
            var p=f.p;h.assertTrue(EsperShields.holding(p),"normal category shield is actually enabled");
            String wire=new HighPowerInput.Input(0,1,p.getId(),LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p))).wire();
            boolean started=switch(kind){case "multi"->MultiAxisReflection.start(p,wire);case "parry"->VectorParry.start(p,wire);default->AutonomousVectorCircle.start(p,wire);};
            h.assertTrue(started,"normal authenticated paid dedicated activation");
            if(mode.equals("quota"))f.exhaustQuota();
            Vec3 normal=kind.equals("multi")?vec(MultiAxisReflectionGeometry.facetNormal(MultiAxisReflectionGeometry.pose(vector(p.getBoundingBox().getCenter()),vector(p.getLookAngle())),1)):new Vec3(0,0,1);
            if(mode.equals("rear"))normal=new Vec3(0,0,-1);
            if(mode.equals("gap")){
                normal=new Vec3(0,0,1);var pose=MultiAxisReflectionGeometry.pose(vector(p.getBoundingBox().getCenter()),vector(p.getLookAngle()));
                h.assertTrue(MultiAxisReflectionGeometry.crossing(pose,vector(p.getBoundingBox().getCenter().add(normal.scale(2.2))),vector(p.getBoundingBox().getCenter().add(normal.scale(.2)))).isEmpty(),"the actual center ray lies in the finite gap between the minus/plus twenty-degree facets");
            }
            Arrow arrow=f.arrow(normal);
            if(mode.equals("unsupported"))arrow.setNoGravity(true);
            if(mode.equals("mutated"))f.hook(new Object(){@SubscribeEvent(priority=EventPriority.HIGHEST)public void mutate(ProjectileImpactEvent e){if(e.getProjectile()==arrow)arrow.setBaseDamage(3);}});
            BigDecimal before=HighPowerCombat.shield(p),hp=new BigDecimal(HighPowerCombat.healthText(p));
            String quotaBefore=String.valueOf(p.getPersistentData().get(f.quota));
            var nativeType=p.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.ARROW);
            var originalTags=new ArrayList<>(nativeType.tags().toList());
            try{
                if(mode.equals("tags")){
                    var changed=new ArrayList<>(originalTags);changed.add(net.minecraft.tags.DamageTypeTags.IS_FIRE);bindTags(nativeType,changed);
                    h.assertTrue(nativeType.is(net.minecraft.tags.DamageTypeTags.IS_FIRE),"actual registered ARROW holder now carries the incompatible datapack-style tag");
                }
                arrow.tick();
            }finally{if(mode.equals("tags"))bindTags(nativeType,originalTags);}
            BigDecimal spent=before.subtract(HighPowerCombat.shield(p));
            h.assertTrue(new BigDecimal(HighPowerCombat.healthText(p)).compareTo(hp)==0,"native dedicated or base defense preserves real HP");
            if(mode.equals("paid")){
                BigDecimal expected=new BigDecimal(kind.equals("parry")?"3":"4");
                h.assertTrue(spent.compareTo(expected)==0,"dedicated contact pays native 4 damage, including parry's explicit 25-percent category reduction");
                h.assertTrue(spent.compareTo(new BigDecimal(HighPowerCombat.maxShieldText(p)).multiply(EsperShields.PROJECTILE_COST))<0,"specialist saves real shield versus category projectile cost");
                h.assertTrue(!quotaBefore.equals(String.valueOf(p.getPersistentData().get(f.quota))),"only real dedicated commit changes its own quota");
                if(kind.equals("multi"))h.assertTrue(arrow.getOwner()==f.shooter&&arrow.getPersistentData().getBoolean(MultiAxisReflectionArrowBridge.SPENT),"dedicated physical reversal retains original shooter and its one-use spent mark");
                else h.assertTrue(arrow.isRemoved()&&arrow.getOwner()==f.shooter,"native absorption completed body hit without base owner rewrite");
            }else{
                h.assertTrue(spent.compareTo(new BigDecimal(HighPowerCombat.maxShieldText(p)).multiply(EsperShields.PROJECTILE_COST))==0,"failed narrow preflight retains exact existing category projectile cost");
                h.assertTrue(arrow.getOwner()==p&&!arrow.getPersistentData().contains(MultiAxisReflectionArrowBridge.SPENT),"actual category reflection owns fallback, never counterfeit specialist SPENT");
                h.assertTrue(quotaBefore.equals(String.valueOf(p.getPersistentData().get(f.quota))),"base fallback cannot consume dedicated quota");
            }
            h.succeed();
        });
    }
    private static final class Fixture{
        final GameTestHelper h;final String kind,quota;final ServerPlayer p;final Zombie shooter;final EmbeddedChannel channel;
        final List<Entity> entities=new ArrayList<>();final List<Object> hooks=new ArrayList<>();boolean closed,forced;
        Fixture(GameTestHelper h,String kind){
            this.h=h;this.kind=kind;quota=switch(kind){case "multi"->MultiAxisReflection.QUOTA_KEY;case "parry"->VectorParry.QUOTA_KEY;default->AutonomousVectorCircle.QUOTA_KEY;};
            var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"CV"+UUID.randomUUID().toString().substring(0,10)),false);
            p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);
            p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);var at=h.absoluteVec(new Vec3(8.5,18,8.5));p.moveTo(at.x,at.y,at.z,0,0);p.setYHeadRot(0);p.setDeltaMovement(Vec3.ZERO);p.connection.resetPosition();
            var s=AbilityStorage.get(p);s.selectCategory("vecmanip");s.setLevel(5);for(var skill:SkillCatalog.REGISTERED)if(skill.category().equals("vecmanip"))s.learn(skill.id());s.activated=true;s.overloadFine=true;
            h.assertTrue(HighPowerCombat.enroll(p,5),"declared normal connected LV5 category fixture");String id=switch(kind){case "multi"->MultiAxisReflection.ID;case "parry"->VectorParry.ID;default->AutonomousVectorCircle.ID;};h.assertTrue(s.presets.edit(0,0,id,s::learned),"actual usable dedicated preset");HighPowerResources.initializeBudget(p);
            shooter=new Zombie(EntityType.ZOMBIE,h.getLevel());shooter.setNoAi(true);shooter.setNoGravity(true);shooter.setSilent(true);shooter.setPos(p.position().add(5,0,6));h.assertTrue(h.getLevel().addFreshEntity(shooter),"real hostile source");entities.add(shooter);
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
            EsperLegacyBypass.force(true);forced=true;
            hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p,h.testInfo.getTimeoutTicks()+5));
        }
        Arrow arrow(Vec3 normal){
            Vec3 at=p.getBoundingBox().getCenter().add(normal.scale(2.2));Arrow a=new Arrow(h.getLevel(),shooter,new ItemStack(Items.ARROW),null);a.setPos(at);a.setDeltaMovement(normal.scale(.01));h.assertTrue(h.getLevel().addFreshEntity(a),"real native arrow added");entities.add(a);
            a.tick();h.assertTrue(!a.isRemoved()&&((NativeScatterArrowOwnerAccess)a).academy$scatterLeftOwner(),"genuine native launch establishes left-owner evidence");a.setPos(at);a.setDeltaMovement(normal.scale(-2));return a;
        }
        void exhaustQuota(){
            var tag=new CompoundTag();tag.putInt("version",1);
            if(kind.equals("parry")){tag.putLong("observed",p.getPersistentData().getCompound(VectorParry.PAYMENT_KEY).getLong("observed"));tag.putInt("hits",4);}
            else {int[] ages=new int[kind.equals("multi")?4:8];Arrays.fill(ages,20);tag.putIntArray("remaining",ages);if(kind.equals("multi"))tag.putInt("facets",15);}
            p.getPersistentData().put(quota,tag);
        }
        void hook(Object observer){hooks.add(observer);NeoForge.EVENT_BUS.register(observer);}
        void close(){if(closed)return;closed=true;try{hooks.forEach(NeoForge.EVENT_BUS::unregister);MultiAxisReflection.remove(p);VectorParry.remove(p);AutonomousVectorCircle.remove(p);AbilityStorage.remove(p);LegacySingleKeyOwnerEpoch.forget(p);p.server.getPlayerList().remove(p);entities.forEach(Entity::discard);channel.finishAndReleaseAll();}finally{if(forced){forced=false;EsperLegacyBypass.force(false);}}}
    }
    private static MultiAxisReflectionGeometry.Vector vector(Vec3 p){return new MultiAxisReflectionGeometry.Vector(p.x,p.y,p.z);}
    private static Vec3 vec(MultiAxisReflectionGeometry.Vector p){return new Vec3(p.x(),p.y(),p.z());}
    private static void bindTags(net.minecraft.core.Holder.Reference<net.minecraft.world.damagesource.DamageType> holder,Collection<net.minecraft.tags.TagKey<net.minecraft.world.damagesource.DamageType>> tags){
        try{var method=net.minecraft.core.Holder.Reference.class.getDeclaredMethod("bindTags",Collection.class);method.setAccessible(true);method.invoke(holder,tags);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
    }
}
