package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.canon.CanonSkillsCombatB;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.highpower.MagneticItemProvenance;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Native connected-player fixture. This does not verify a separately running client. */
@GameTestHolder("academy_canon_float")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class CanonFloatRuntimeTests {
    private CanonFloatRuntimeTests() {}
    @SubscribeEvent public static void template(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        var tag = new CompoundTag(); var size = new ListTag(); var position = new ListTag();
        for (int value : new int[]{12, 12, 12}) size.add(IntTag.valueOf(value));
        for (int i = 0; i < 3; i++) position.add(IntTag.valueOf(0));
        var air = new CompoundTag(); air.putString("Name", "minecraft:air");
        var cell = new CompoundTag(); cell.put("pos", position); cell.putInt("state", 0);
        var blocks = new ListTag(); blocks.add(cell); var palette = new ListTag(); palette.add(air);
        tag.put("size", size); tag.put("blocks", blocks); tag.put("palette", palette); tag.put("entities", new ListTag());
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_canon_float", "empty"))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template="empty", batch="canon_float", timeoutTicks=100)
    public static void targeted_float_pays_once_moves_mob_and_stops_when_deactivated(GameTestHelper helper) {
        var fixture = new Fixture(helper);
        try {
            fixture.select("float_lift");
            Zombie hostile = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5, 3, 7.5));
            fixture.player.moveTo(fixture.absolute(new Vec3(4.5, 3, 2.5)), 0, 0);
            fixture.player.setYHeadRot(0); fixture.loadChunks();
            double before = HighPowerResources.cp(fixture.player);
            helper.assertTrue(CanonSkillsCombatB.cast(fixture.player, "float_lift"), "visible hostile accepted");
            double after = HighPowerResources.cp(fixture.player);
            helper.assertTrue(after < before, "real shared CP debit");
            helper.assertFalse(CanonSkillsCombatB.cast(fixture.player, "float_lift"), "duplicate cast denied");
            helper.assertTrue(HighPowerResources.cp(fixture.player) == after, "denied duplicate pays nothing");
            double height = hostile.getY();
            helper.runAtTickTime(4, () -> {
                helper.assertTrue(hostile.getY() > height, "actual native mob rises over server ticks");
                AbilityStorage.get(fixture.player).setActivateState(false);
                helper.runAtTickTime(6, () -> {
                    double velocity = hostile.getDeltaMovement().y;
                    helper.runAtTickTime(8, () -> {
                        helper.assertTrue(hostile.getDeltaMovement().y < velocity, "deactivation stops buoyancy");
                        hostile.discard(); fixture.close(); helper.succeed();
                    });
                });
            });
        } catch (Throwable failure) { fixture.close(); throw failure; }
    }

    @GameTest(template="empty", batch="canon_float", timeoutTicks=100)
    public static void rescue_requires_airborne_fall_and_reduces_downward_velocity(GameTestHelper helper) {
        var fixture = new Fixture(helper);
        try {
            fixture.select("float_rescue"); fixture.loadChunks();
            fixture.player.moveTo(fixture.absolute(new Vec3(4.5, 6, 2.5)), 0, 0);
            fixture.player.setOnGround(true); fixture.player.setDeltaMovement(0, -.8, 0);
            double before = HighPowerResources.cp(fixture.player);
            helper.assertFalse(CanonSkillsCombatB.cast(fixture.player, "float_rescue"), "grounded rescue rejected");
            helper.assertTrue(HighPowerResources.cp(fixture.player) == before, "refusal pays nothing");
            fixture.player.setOnGround(false);
            helper.assertTrue(CanonSkillsCombatB.cast(fixture.player, "float_rescue"), "falling rescue accepted");
            helper.runAtTickTime(3, () -> {
                helper.assertTrue(fixture.player.getDeltaMovement().y >= -.13, "server velocity is reduced");
                helper.assertTrue(fixture.player.fallDistance == 0, "active buoyancy accounts for fall distance");
                fixture.close(); helper.succeed();
            });
        } catch (Throwable failure) { fixture.close(); throw failure; }
    }

    @GameTest(template="empty", batch="canon_float", timeoutTicks=100)
    public static void rescue_other_player_requires_continuous_sneaking_consent(GameTestHelper helper) {
        var caster = new Fixture(helper); var recipient = new Fixture(helper);
        try {
            caster.select("float_rescue"); caster.loadChunks();
            caster.player.moveTo(caster.absolute(new Vec3(4.5, 6, 2.5)), 0, 5); caster.player.setYHeadRot(0);
            recipient.player.moveTo(recipient.absolute(new Vec3(4.5, 6, 7.5)), 0, 0);
            recipient.player.setOnGround(false); recipient.player.setDeltaMovement(0,-.8,0);
            double cp=HighPowerResources.cp(caster.player);
            helper.assertFalse(CanonSkillsCombatB.cast(caster.player,"float_rescue"),"no consent means no rescue");
            helper.assertTrue(HighPowerResources.cp(caster.player)==cp,"refused rescue has no cost");
            recipient.player.setShiftKeyDown(true);
            helper.assertTrue(CanonSkillsCombatB.cast(caster.player,"float_rescue"),"sneaking falling player accepts rescue");
            helper.assertTrue(HighPowerResources.cp(caster.player)<cp,"accepted rescue pays actual CP");
            helper.runAtTickTime(3,()->{
                helper.assertTrue(recipient.player.getDeltaMovement().y>=-.13,"recipient receives actual reduced fall speed");
                helper.assertTrue(recipient.player.fallDistance==0,"active rescue clears recipient fall distance");
                recipient.player.setShiftKeyDown(false); recipient.player.setDeltaMovement(0,-.8,0);
                helper.runAtTickTime(5,()->{
                    helper.assertTrue(recipient.player.getDeltaMovement().y<-.2,"release of consent stops controlling the recipient");
                    caster.close();recipient.close();helper.succeed();
                });
            });
        } catch(Throwable failure) {caster.close();recipient.close();throw failure;}
    }

    @GameTest(template="empty", batch="canon_float", timeoutTicks=100)
    public static void buoyancy_controls_only_verified_owned_dropped_item(GameTestHelper h) {
        var f=new Fixture(h);var other=new Fixture(h);ItemEntity own=null,foreign=null;
        try {
            f.loadChunks();var state=AbilityStorage.get(f.player);state.learn("float_item_lift");state.learn("float_item_lower");state.learn("float_item_release");
            f.select("float_item_lift");
            foreign=other.player.drop(new ItemStack(Items.STONE,3),false);
            h.assertTrue(foreign!=null&&MagneticItemProvenance.ownedBy(foreign,other.player.getUUID()),"actual foreign player toss");
            foreign.setPos(f.player.getEyePosition().add(0,-.1,2));foreign.setDeltaMovement(Vec3.ZERO);
            double cp=HighPowerResources.cp(f.player);
            h.assertFalse(CanonSkillsCombatB.cast(f.player,"float_item_lift"),"other player's toss refused");
            h.assertTrue(HighPowerResources.cp(f.player)==cp,"foreign refusal costs nothing");foreign.discard();foreign=null;
            own=f.player.drop(new ItemStack(Items.STONE,4),false);
            h.assertTrue(own!=null&&MagneticItemProvenance.ownedBy(own,f.player.getUUID()),"actual own Q toss verified");
            own.setPos(f.player.getEyePosition().add(0,-.1,2));own.setDeltaMovement(Vec3.ZERO);
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"float_item_lift"),"owned item lift starts");
            ItemEntity item=own;
            h.runAtTickTime(3,()->{
                h.assertTrue(item.getDeltaMovement().y>.02,"actual item receives upward buoyancy");
                f.player.lookAt(EntityAnchorArgument.Anchor.EYES,item.getBoundingBox().getCenter());f.player.setYHeadRot(f.player.getYRot());
                f.select("float_item_lower");
                h.assertTrue(CanonSkillsCombatB.cast(f.player,"float_item_lower"),"owned active item lowered");
                h.runAtTickTime(6,()->{
                    h.assertTrue(item.getDeltaMovement().y<0,"actual item receives downward control");
                    f.player.lookAt(EntityAnchorArgument.Anchor.EYES,item.getBoundingBox().getCenter());f.player.setYHeadRot(f.player.getYRot());
                    f.select("float_item_release");
                    h.assertTrue(CanonSkillsCombatB.cast(f.player,"float_item_release"),"release ends ownership lease");
                    h.assertTrue(item.getItem().getCount()==4&&!item.isRemoved(),"no item duplicated or consumed");
                    item.discard();f.close();other.close();h.succeed();
                });
            });
        } catch(Throwable failure) {if(own!=null)own.discard();if(foreign!=null)foreign.discard();f.close();other.close();throw failure;}
    }

    @GameTest(template="empty", batch="canon_float", timeoutTicks=100)
    public static void buoyancy_field_lifts_owned_items_and_releases_as_a_group(GameTestHelper h) {
        var f=new Fixture(h);var other=new Fixture(h);ItemEntity a=null,b=null,foreign=null;
        try {
            f.loadChunks();var state=AbilityStorage.get(f.player);state.learn("float_item_lift");state.learn("float_item_field");state.learn("float_release_field");
            f.select("float_item_field");
            a=f.player.drop(new ItemStack(Items.STONE,3),false);b=f.player.drop(new ItemStack(Items.DIRT,4),false);
            foreign=other.player.drop(new ItemStack(Items.COBBLESTONE,5),false);
            h.assertTrue(a!=null&&b!=null&&foreign!=null&&MagneticItemProvenance.ownedBy(a,f.player.getUUID())
                    &&MagneticItemProvenance.ownedBy(b,f.player.getUUID())&&MagneticItemProvenance.ownedBy(foreign,other.player.getUUID()),"three native throws have separate owner proofs");
            a.setPos(f.player.getEyePosition().add(-.7,-.3,2));b.setPos(f.player.getEyePosition().add(.7,-.3,2));
            foreign.setPos(f.player.getEyePosition().add(0,-.3,3));a.setDeltaMovement(Vec3.ZERO);b.setDeltaMovement(Vec3.ZERO);foreign.setDeltaMovement(Vec3.ZERO);
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"float_item_field"),"field starts for own native throws");
            ItemEntity ownA=a,ownB=b,otherItem=foreign;
            h.runAtTickTime(3,()->{
                h.assertTrue(ownA.getDeltaMovement().y>.02&&ownB.getDeltaMovement().y>.02,"two own items receive real buoyancy");
                h.assertTrue(otherItem.getDeltaMovement().y<.01,"foreign throw is untouched by field");
                f.select("float_release_field");h.assertTrue(CanonSkillsCombatB.cast(f.player,"float_release_field"),"group release ends leases");
                ownA.setDeltaMovement(Vec3.ZERO);ownB.setDeltaMovement(Vec3.ZERO);
                h.runAtTickTime(6,()->{
                    h.assertTrue(ownA.getDeltaMovement().y<.01&&ownB.getDeltaMovement().y<.01,"released items no longer receive lift");
                    h.assertTrue(ownA.getItem().getCount()==3&&ownB.getItem().getCount()==4&&otherItem.getItem().getCount()==5,"no stacks duplicated or consumed");
                    ownA.discard();ownB.discard();otherItem.discard();f.close();other.close();h.succeed();
                });
            });
        } catch(Throwable failure) {if(a!=null)a.discard();if(b!=null)b.discard();if(foreign!=null)foreign.discard();f.close();other.close();throw failure;}
    }

    @GameTest(template="empty", batch="canon_float", timeoutTicks=30)
    public static void releasing_pending_buoyancy_field_cancels_queue_without_requiring_an_installed_item(GameTestHelper h) {
        var f=new Fixture(h);ItemEntity item=null;
        try {
            f.loadChunks();var state=AbilityStorage.get(f.player);
            state.learn("float_item_lift");state.learn("float_item_field");state.learn("float_release_field");
            f.select("float_item_field");item=f.player.drop(new ItemStack(Items.STONE,3),false);
            h.assertTrue(item!=null&&MagneticItemProvenance.ownedBy(item,f.player.getUUID()),"real native owned drop");
            item.setPos(f.player.getEyePosition().add(0,-.3,2));item.setDeltaMovement(Vec3.ZERO);
            // Simulate other LV5 work exhausting this server tick's actual shared queue budget.
            for(int i=0;i<64&&cn.academy.port.macro.MacroSettlement.reserve(f.player.server);i++){}
            h.assertFalse(cn.academy.port.macro.MacroSettlement.reserve(f.player.server),"shared target budget exhausted before field submission");
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"float_item_field"),"real field submits pending settlement");
            f.select("float_release_field");
            h.assertTrue(CanonSkillsCombatB.cast(f.player,"float_release_field"),"release accepts pending field before any item lease is installed");
            ItemEntity released=item;
            h.runAtTickTime(3,()->{
                h.assertTrue(released.getDeltaMovement().y<.01,"canceled settlement never installs buoyancy later");
                h.assertTrue(released.getItem().getCount()==3,"release neither consumes nor duplicates the real stack");
                released.discard();f.close();h.succeed();
            });
        }catch(Throwable error){if(item!=null)item.discard();f.close();throw error;}
    }

    private static final class Fixture {
        final GameTestHelper helper; final ServerPlayer player; final EmbeddedChannel channel; boolean closed;
        Fixture(GameTestHelper helper) {
            this.helper = helper; var level = helper.getLevel(); UUID id = UUID.randomUUID();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(id, "CF" + id.toString().substring(0, 8)), false);
            player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection); level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL); player.getAbilities().instabuild = false;
            player.moveTo(absolute(new Vec3(4.5, 3, 2.5)), 0, 0);
            var state = AbilityStorage.get(player); state.changeCategoryClassic("float_dial"); state.setLevel(5);
            state.learn("float_lift"); state.learn("float_rescue"); state.learn("float_field"); state.setActivateState(true);
            helper.assertTrue(HighPowerCombat.enroll(player, 5), "fixture enrolled in actual exact combat ledger");
            HighPowerResources.initializeBudget(player);
            helper.testInfo.addListener(new GameTestListener(){
                public void testStructureLoaded(GameTestInfo i){} public void testPassed(GameTestInfo i,GameTestRunner r){close();}
                public void testFailed(GameTestInfo i,GameTestRunner r){close();} public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}
            });
        }
        Vec3 absolute(Vec3 local) { return helper.absoluteVec(local); }
        void select(String id) { AbilityStorage.get(player).presets.edit(0, 0, id, AbilityStorage.get(player)::learned); }
        void loadChunks() {
            BlockPos p = player.blockPosition();
            for (int x = (p.getX() - 32) >> 4; x <= (p.getX() + 32) >> 4; x++)
                for (int z = (p.getZ() - 32) >> 4; z <= (p.getZ() + 32) >> 4; z++) player.serverLevel().getChunk(x, z);
        }
        void close() {
            if (closed) return;
            closed = true;
            try { player.server.getPlayerList().remove(player); } finally { channel.finishAndReleaseAll(); }
        }
    }
}
