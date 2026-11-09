package cn.academy.port.gametest;

import cn.academy.port.hook.*;
import cn.academy.port.skill.ClassicMetalTargets;
import cn.academy.port.survival.ClassicMaterials;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.*;

/** Compiled native fixtures; execute only in the owner's serial runtime lane after promotion.
 * These use the real item, registered entity, world collision, recipe manager, NBT and synced data. */
@GameTestHolder("academy") @PrefixGameTestTemplate(false) @EventBusSubscriber(modid="academy")
public final class AcademyHookRuntimeTests {
    private static final String TEMPLATE="hook_runtime_empty",BATCH="academy_hook";
    @SubscribeEvent public static void template(net.neoforged.neoforge.event.level.LevelEvent.Load event){
        if(!GameTestHooks.isGametestEnabled()||!(event.getLevel() instanceof ServerLevel level))return;
        var tag=new CompoundTag();var size=new ListTag();for(int n:new int[]{30,15,30})size.add(IntTag.valueOf(n));tag.put("size",size);
        var blocks=new ListTag();var air=new CompoundTag();var pos=new ListTag();for(int i=0;i<3;i++)pos.add(IntTag.valueOf(0));air.put("pos",pos);air.putInt("state",0);blocks.add(air);tag.put("blocks",blocks);tag.put("entities",new ListTag());
        var palette=new ListTag();var state=new CompoundTag();state.putString("Name","minecraft:air");palette.add(state);tag.put("palette",palette);
        level.getStructureManager().getOrCreate(id(TEMPLATE)).load(level.registryAccess().lookupOrThrow(Registries.BLOCK),tag);
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void hook_recipe41_actual_five_plate_cross_yields_three(GameTestHelper h){
        var recipe=(CraftingRecipe)h.getLevel().getRecipeManager().byKey(id("classic/maghook_41")).orElseThrow().value();
        var items=new ArrayList<ItemStack>();for(int slot=0;slot<9;slot++)items.add(slot==1||slot==3||slot==4||slot==5||slot==7?new ItemStack(ClassicMaterials.REINFORCED_IRON_PLATE.get()):ItemStack.EMPTY);
        var input=CraftingInput.of(3,3,items);h.assertTrue(recipe.matches(input,h.getLevel()),"five real survival plates match source cross");var output=recipe.assemble(input,h.getLevel().registryAccess());
        h.assertTrue(output.is(ClassicHooks.MAGHOOK.get())&&output.getCount()==3,"actual recipe yields three usable hook items");
        h.assertTrue(recipe.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty),"no plate container remainder");items.set(4,ItemStack.EMPTY);h.assertFalse(recipe.matches(CraftingInput.of(3,3,items),h.getLevel()),"four plates rejected");items.set(4,new ItemStack(Items.IRON_INGOT));h.assertFalse(recipe.matches(CraftingInput.of(3,3,items),h.getLevel()),"ingot does not replace plate");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void hook_survival_and_offhand_use_consumes_exactly_one_without_cooldown(GameTestHelper h){var f=new Fixture(h);var p=f.player();for(var hand:InteractionHand.values()){p.setItemInHand(hand,new ItemStack(ClassicHooks.MAGHOOK.get(),4));ClassicHooks.MAGHOOK.get().use(f.level,p,hand);h.assertValueEqual(p.getItemInHand(hand).getCount(),3,"one item consumed in actual hand");}h.assertValueEqual(f.hooks().size(),2,"two physical throws");h.assertFalse(p.getCooldowns().isOnCooldown(ClassicHooks.MAGHOOK.get()),"source no invented cooldown");h.succeed();}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void hook_creative_throw_keeps_stack_and_source_return_remains_possible(GameTestHelper h){var f=new Fixture(h);var p=f.player();p.getAbilities().instabuild=true;p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ClassicHooks.MAGHOOK.get(),2));ClassicHooks.MAGHOOK.get().use(f.level,p,InteractionHand.MAIN_HAND);h.assertValueEqual(p.getMainHandItem().getCount(),2,"creative keeps item");h.assertValueEqual(f.hooks().size(),1,"creative still creates entity");h.succeed();}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void hook_cancelled_spawn_preserves_stack_and_repeated_use_can_retry(GameTestHelper h){var f=new Fixture(h);var p=f.player();p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ClassicHooks.MAGHOOK.get(),2));var cancel=f.listen(new CancelSpawn(true));ClassicHooks.MAGHOOK.get().use(f.level,p,InteractionHand.MAIN_HAND);h.assertValueEqual(p.getMainHandItem().getCount(),2,"cancelled spawn debits nothing");h.assertTrue(f.hooks().isEmpty(),"cancelled entity absent");cancel.active=false;ClassicHooks.MAGHOOK.get().use(f.level,p,InteractionHand.MAIN_HAND);h.assertValueEqual(p.getMainHandItem().getCount(),1,"successful retry consumes once");h.assertValueEqual(f.hooks().size(),1,"retry has one entity");h.succeed();}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void hook_actual_flight_uses_old_trace_new_gravity_and_fixed_initial_rotation(GameTestHelper h){var f=new Fixture(h);var p=f.player();p.setXRot(-23);p.setYHeadRot(31);var hook=f.track(new MagHookEntity(p));Vec3 old=hook.position(),velocity=hook.getDeltaMovement();float yaw=hook.getYRot(),pitch=hook.getXRot();close(h,velocity.length(),2,"launch speed2");close(h,old.y,p.getEyeY(),"eye origin");hook.tick();close(h,hook.getY()-old.y,velocity.y-.05,"gravity before displacement");close(h,hook.getX()-old.x,velocity.x,"no drag");close(h,hook.getYRot(),yaw,"flight yaw unchanged");close(h,hook.getXRot(),pitch,"flight pitch unchanged");h.assertFalse(hook.isPickable(),"flying hook not pickable");h.succeed();}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void hook_real_block_hit_delays_anchor_until_next_tick(GameTestHelper h){var f=new Fixture(h);var p=f.player();h.setBlock(new BlockPos(3,2,6),Blocks.STONE);var hook=f.track(new MagHookEntity(p));hook.tick();hook.tick();h.assertTrue(hook.isHit(),"native clip collided with wall");h.assertValueEqual(hook.hitSide(),2,"north face");close(h,hook.getBbWidth(),.5,"collision tick retains small bounds");h.assertTrue(hook.getZ()>f.pos(3.5,2.5,6).z,"hit tick advances old velocity after collision");hook.tick();close(h,hook.getBbWidth(),1,"next tick expanded");close(h,hook.getZ(),f.pos(3.5,2.5,5.99).z,"source .51 face center");h.assertTrue(hook.getDeltaMovement().equals(Vec3.ZERO),"anchored stopped");h.succeed();}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void hook_all_six_faces_keep_exact_positions_and_vertical_yaw(GameTestHelper h){var f=new Fixture(h);for(int side=0;side<6;side++){var hook=f.anchored(side,new BlockPos(10,7,10),31);var a=hook.anchor();close(h,hook.getX(),a.position().x(),"anchor x");close(h,hook.getY(),a.position().y(),"anchor y");close(h,hook.getZ(),a.position().z(),"anchor z");close(h,hook.getYRot(),side<2?31:side==2?0:side==3?180:side==4?-90:90,"source face yaw");}h.succeed();}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void hook_any_player_punch_returns_once_environment_damage_does_not(GameTestHelper h){var f=new Fixture(h);var hook=f.anchored(1,new BlockPos(10,7,10),0);hook.hurt(f.level.damageSources().generic(),100);h.assertFalse(hook.isRemoved(),"nonplayer damage ignored");var other=f.player();hook.hurt(f.level.damageSources().playerAttack(other),0);hook.hurt(f.level.damageSources().playerAttack(other),4);h.assertTrue(hook.isRemoved(),"zero damage player punch retrieves");h.assertValueEqual(f.drops().size(),1,"repeated attack cannot duplicate");h.assertValueEqual(f.drops().getFirst().getItem().getCount(),1,"one returned item");h.succeed();}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void hook_host_air_returns_once_non_air_replacement_retains_source_hook(GameTestHelper h){var f=new Fixture(h);var relative=new BlockPos(10,7,10);var hook=f.anchored(1,relative,0);h.setBlock(relative,Blocks.DIRT);hook.tick();h.assertFalse(hook.isRemoved(),"source only checks air, not host identity");h.setBlock(relative,Blocks.AIR);hook.tick();hook.tick();h.assertTrue(hook.isRemoved(),"air host removes hook");h.assertValueEqual(f.drops().size(),1,"air host refunds once");h.succeed();}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void hook_nonhook_collision_deals_four_and_returns_old_position(GameTestHelper h){var f=new Fixture(h);var p=f.onlinePlayer();var victim=f.track(new Pig(EntityType.PIG,f.level));victim.setNoAi(true);victim.setPos(f.pos(3.5,2,4.5));f.level.addFreshEntity(victim);var hook=f.track(new MagHookEntity(p));Vec3 old=hook.position();float health=victim.getHealth();hook.tick();close(h,health-victim.getHealth(),4,"real impact4damage");h.assertTrue(victim.getKillCredit()==p,"actual damage is player attributed");h.assertTrue(hook.isRemoved(),"real impact returns");h.assertValueEqual(f.drops().size(),1,"one item drop");close(h,f.drops().getFirst().getX(),old.x,"source old drop x");close(h,f.drops().getFirst().getZ(),old.z,"source old drop z");h.succeed();}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void hook_cancelled_return_retries_without_second_damage_or_second_drop(GameTestHelper h){var f=new Fixture(h);var p=f.player();var victim=f.track(new Pig(EntityType.PIG,f.level));victim.setNoAi(true);victim.setPos(f.pos(3.5,2,4.5));f.level.addFreshEntity(victim);var cancel=f.listen(new CancelSpawn(false));var hook=f.track(new MagHookEntity(p));float health=victim.getHealth();hook.tick();h.assertFalse(hook.isRemoved(),"cancelled refund retains entity");close(h,health-victim.getHealth(),4,"one impact damage");hook.tick();close(h,health-victim.getHealth(),4,"pending refund does not attack again");cancel.active=false;hook.tick();hook.tick();h.assertTrue(hook.isRemoved(),"retry removes entity only after drop accepted");h.assertValueEqual(f.drops().size(),1,"one accepted refund");h.succeed();}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void hook_anchored_hook_collision_returns_incoming_only(GameTestHelper h){var f=new Fixture(h);var p=f.player();var anchored=f.anchored(2,new BlockPos(3,2,5),0);var incoming=f.track(new MagHookEntity(p));incoming.tick();h.assertTrue(incoming.isRemoved()&&!anchored.isRemoved(),"anchored hook stays while incoming refunds");h.assertValueEqual(f.drops().size(),1,"one incoming refund");h.succeed();}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void hook_flying_hooks_are_non_pickable_and_pass_through_each_other(GameTestHelper h){var f=new Fixture(h);var p=f.player();var other=f.track(new MagHookEntity(ClassicHooks.ENTITY.get(),f.level));other.setPos(f.pos(3.5,2.5,4.5));f.level.addFreshEntity(other);var incoming=f.track(new MagHookEntity(p));incoming.tick();h.assertFalse(incoming.isRemoved()||other.isRemoved(),"flying hooks neither damage nor return each other");h.assertTrue(f.drops().isEmpty(),"no ignored-impact return");h.succeed();}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void hook_live_owner_and_cold_owner_exclusion_survive_logout(GameTestHelper h){var f=new Fixture(h);var p=f.onlinePlayer();var hook=f.track(new MagHookEntity(p));h.assertTrue(hook.owner()==p,"owner resolves from live server world");var tag=new CompoundTag();hook.saveWithoutId(tag);var loaded=f.track(new MagHookEntity(ClassicHooks.ENTITY.get(),f.level));loaded.load(tag);p.setPos(f.pos(3.5,2,4.5));loaded.tick();h.assertFalse(loaded.isRemoved(),"saved owner UUID still excluded from actual flight collision");p.discard();h.assertTrue(loaded.owner()==null&&loaded.ownerUUID().equals(Optional.of(p.getUUID())),"logout loses live reference while persistent owner UUID survives");Vec3 old=loaded.position();loaded.tick();h.assertTrue(loaded.position().distanceToSqr(old)>0,"offline owner does not destroy support item");h.succeed();}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void hook_pending_refund_survives_cold_save_without_duplicate_damage(GameTestHelper h){var f=new Fixture(h);var p=f.player();var hook=f.anchored(1,new BlockPos(10,7,10),0);var cancel=f.listen(new CancelSpawn(false));hook.hurt(f.level.damageSources().playerAttack(p),4);var tag=new CompoundTag();hook.saveWithoutId(tag);h.assertTrue(tag.getBoolean("ReturnPending"),"cancelled return is saved");hook.discard();var loaded=f.track(new MagHookEntity(ClassicHooks.ENTITY.get(),f.level));loaded.load(tag);loaded.tick();h.assertFalse(loaded.isRemoved(),"cold pending refund remains conserved during cancellation");cancel.active=false;loaded.tick();loaded.tick();h.assertTrue(loaded.isRemoved(),"cold pending refund completes");h.assertValueEqual(f.drops().size(),1,"exactly one eventual accepted refund");h.succeed();}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void hook_cold_nbt_and_synced_data_preserve_face_host_owner_and_restore_flight(GameTestHelper h){var f=new Fixture(h);var p=f.player();var original=f.track(new MagHookEntity(p));var tag=new CompoundTag();original.saveWithoutId(tag);var flight=f.track(new MagHookEntity(ClassicHooks.ENTITY.get(),f.level));flight.load(tag);h.assertTrue(flight.ownerUUID().equals(Optional.of(p.getUUID())),"owner UUID survives save");Vec3 old=flight.position();flight.tick();h.assertTrue(flight.position().distanceToSqr(old)>0,"reloaded flight remains physical");
        var anchored=f.anchored(5,new BlockPos(10,7,10),0);tag=new CompoundTag();anchored.saveWithoutId(tag);var loaded=f.track(new MagHookEntity(ClassicHooks.ENTITY.get(),f.level));loaded.load(tag);loaded.tick();h.assertTrue(loaded.isHit()&&loaded.hitSide()==5&&loaded.host().equals(anchored.host()),"source NBT face/host restored");close(h,loaded.getX(),anchored.getX(),"source restored face position");
        var sync=f.track(new MagHookEntity(ClassicHooks.ENTITY.get(),f.level));sync.getEntityData().assignValues(original.getEntityData().getNonDefaultValues());h.assertTrue(sync.ownerUUID().equals(original.ownerUUID()),"owner synchronized");sync.getEntityData().assignValues(anchored.getEntityData().getNonDefaultValues());h.assertTrue(sync.isHit()&&sync.hitSide()==5&&sync.host().equals(anchored.host()),"source packed hit/host synchronization");sync.tick();close(h,sync.getBbWidth(),1,"sync callback restores anchored dimensions");h.succeed();}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=12)
    public static void hook_is_real_configurable_metal_target_and_invalid_saved_face_is_rejected(GameTestHelper h){var f=new Fixture(h);var hook=f.track(new MagHookEntity(f.player()));h.assertTrue(ClassicMetalTargets.entity(hook),"hook registered in classic metal target defaults");var tag=new CompoundTag();hook.saveWithoutId(tag);tag.putBoolean("isHit",true);tag.putInt("hitSide",99);var bad=f.track(new MagHookEntity(ClassicHooks.ENTITY.get(),f.level));bad.load(tag);h.assertTrue(bad.isRemoved(),"malformed face cannot become invalid anchor");h.succeed();}
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("academy",path);}
    private static void close(GameTestHelper h,double a,double b,String message){h.assertTrue(Math.abs(a-b)<1E-5,message+": "+a+" != "+b);}
    private static final class Fixture {
        final GameTestHelper h;final ServerLevel level;final List<Entity> entities=new ArrayList<>();final List<Object> listeners=new ArrayList<>();
        Fixture(GameTestHelper h){this.h=h;level=h.getLevel();h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){cleanup();}public void testFailed(GameTestInfo i,GameTestRunner r){cleanup();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){cleanup();}});}
        FakePlayer player(){var p=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"[AC-Hook]"));p.setPos(pos(3.5,1,3));p.setYRot(0);p.setYHeadRot(0);p.setXRot(0);p.setNoGravity(true);p.getAbilities().instabuild=false;return track(p);}
        Vec3 pos(double x,double y,double z){var origin=h.absolutePos(BlockPos.ZERO);return new Vec3(origin.getX()+x,origin.getY()+y,origin.getZ()+z);}
        <T extends Entity>T track(T e){entities.add(e);return e;}
        <T>T listen(T listener){listeners.add(listener);NeoForge.EVENT_BUS.register(listener);return listener;}
        MagHookEntity anchored(int side,BlockPos relative,float yaw){h.setBlock(relative,Blocks.STONE);var hook=track(new MagHookEntity(ClassicHooks.ENTITY.get(),level));var tag=new CompoundTag();hook.saveWithoutId(tag);var host=h.absolutePos(relative);tag.putBoolean("isHit",true);tag.putInt("hitSide",side);tag.putInt("hookX",host.getX());tag.putInt("hookY",host.getY());tag.putInt("hookZ",host.getZ());hook.load(tag);hook.setYRot(yaw);hook.tick();level.addFreshEntity(hook);return hook;}
        List<MagHookEntity> hooks(){return level.getEntitiesOfClass(MagHookEntity.class,bounds());}
        List<ItemEntity> drops(){return level.getEntitiesOfClass(ItemEntity.class,bounds(),e->e.getItem().is(ClassicHooks.MAGHOOK.get()));}
        AABB bounds(){var lower=Vec3.atLowerCornerOf(h.absolutePos(BlockPos.ZERO));return new AABB(lower,lower.add(30,15,30));}
        FakePlayer onlinePlayer(){var p=player();level.addNewPlayer(p);return p;}
        void cleanup(){for(Object listener:listeners)NeoForge.EVENT_BUS.unregister(listener);for(var hook:hooks())hook.discard();for(var drop:drops())drop.discard();for(Entity entity:entities)entity.discard();}
    }
    public static final class CancelSpawn {final boolean hooks;boolean active=true;CancelSpawn(boolean hooks){this.hooks=hooks;}@SubscribeEvent public void joining(EntityJoinLevelEvent e){if(active&&(hooks?e.getEntity() instanceof MagHookEntity:e.getEntity() instanceof ItemEntity item&&item.getItem().is(ClassicHooks.MAGHOOK.get())))e.setCanceled(true);}}
    private AcademyHookRuntimeTests(){}
}
