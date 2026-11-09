package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.story.UrbanStoryProgress;
import cn.academy.port.story.UrbanStoryStorage;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static cn.academy.port.story.UrbanStoryProgress.*;

/** Native server fixture; no client transport, actual quests or restart are simulated. */
@GameTestHolder("academy_urban_story")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class UrbanStoryRuntimeTests {
    private UrbanStoryRuntimeTests() {}
    @SubscribeEvent public static void template(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        var tag = new CompoundTag(); var size = new ListTag(); var position = new ListTag();
        for (int value : new int[]{32, 16, 32}) size.add(IntTag.valueOf(value));
        for (int i = 0; i < 3; i++) position.add(IntTag.valueOf(0));
        var air = new CompoundTag(); air.putString("Name", "minecraft:air");
        var cell = new CompoundTag(); cell.put("pos", position); cell.putInt("state", 0);
        var blocks = new ListTag(); blocks.add(cell); var palette = new ListTag(); palette.add(air);
        tag.put("size", size); tag.put("blocks", blocks); tag.put("palette", palette); tag.put("entities", new ListTag());
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_urban_story", "empty"))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template = "empty", batch = "urban_story", timeoutTicks = 100)
    public static void storage_isolation_reload_clone_and_unknown_schema(GameTestHelper helper) {
        var id = UUID.randomUUID();
        var player = new FakePlayer(helper.getLevel(), new GameProfile(id, "[UrbanStory]"));
        var other = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[UrbanOther]"));
        var replacement = new FakePlayer(helper.getLevel(), new GameProfile(id, "[UrbanStory]"));
        try {
            helper.assertTrue(UrbanStoryStorage.read(player).orElseThrow().chapter() == Chapter.BROADCAST, "fresh player");
            helper.assertTrue(UrbanStoryStorage.recordVerified(player, Objective.BROADCAST_RESCUE, Approach.ABILITY)
                    .orElseThrow() == Result.RECORDED, "server evidence recorded");
            helper.assertTrue(!UrbanStoryStorage.read(other).orElseThrow().completed(Objective.BROADCAST_RESCUE), "players isolated");
            var saved = player.getPersistentData().copy();
            var bytes = new ByteArrayOutputStream(); NbtIo.writeCompressed(saved, bytes);
            var decoded = NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray()), NbtAccounter.unlimitedHeap());
            helper.assertTrue(UrbanStoryStorage.decode(decoded.getCompound(UrbanStoryStorage.KEY)).orElseThrow()
                    .completed(Objective.BROADCAST_RESCUE), "NBT survives compressed round trip");
            // Post a real event with fake players; no claim of an actual death lifecycle.
            NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement, player, true));
            helper.assertTrue(UrbanStoryStorage.read(replacement).orElseThrow().completed(Objective.BROADCAST_RESCUE), "registered clone event copies progress");
            var future = new CompoundTag(); future.putInt("schema", 99); future.put("evidence", new CompoundTag());
            replacement.getPersistentData().put(UrbanStoryStorage.KEY, future.copy());
            helper.assertTrue(UrbanStoryStorage.recordVerified(replacement, Objective.BROADCAST_LEAD, Approach.COMBAT).isEmpty(), "unknown schema not edited");
            helper.assertTrue(replacement.getPersistentData().getCompound(UrbanStoryStorage.KEY).equals(future), "unknown schema preserved");
            helper.succeed();
        } catch (Exception error) {
            throw new AssertionError("urban story native fixture", error);
        } finally {
            player.discard(); other.discard(); replacement.discard();
        }
    }

    @GameTest(template = "empty", batch = "urban_story", timeoutTicks = 100)
    public static void npc_guide_rescue_and_witness_are_server_verified(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[UrbanActions]"));
        var teammate = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[UrbanTeam]"));
        var pos = helper.absoluteVec(new Vec3(1.5, 1, 2.5));
        player.moveTo(pos.x, pos.y, pos.z, 0, 0); teammate.moveTo(pos.x, pos.y, pos.z, 0, 0);
        var guide = helper.spawn(EntityType.VILLAGER, new Vec3(1.5, 1, 1.5));
        var injured = helper.spawn(EntityType.VILLAGER, new Vec3(2.5, 1, 1.5));
        var witness = helper.spawn(EntityType.VILLAGER, new Vec3(2.5, 1, 2.5));
        guide.setNoAi(true); injured.setNoAi(true); witness.setNoAi(true);
        guide.getPersistentData().putString(cn.academy.port.story.UrbanStoryClues.WITNESS_KEY,"broadcast_guide");
        injured.getPersistentData().putString(cn.academy.port.story.UrbanStoryClues.WITNESS_KEY,"injured_student");
        witness.getPersistentData().putString(cn.academy.port.story.UrbanStoryClues.WITNESS_KEY,"student_witness");
        injured.setHealth(6);
        try {
            var talk = new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, guide);
            NeoForge.EVENT_BUS.post(talk);
            helper.assertTrue(talk.isCanceled() && UrbanStoryStorage.read(player).orElseThrow().completed(Objective.BROADCAST_LEAD), "guide produces real evidence");
            var noMedicine = new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, injured);
            NeoForge.EVENT_BUS.post(noMedicine);
            helper.assertTrue(!noMedicine.isCanceled() && injured.getHealth() == 6, "no medicine gives no rescue");
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.HONEY_BOTTLE,2));
            var rescue = new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, injured);
            NeoForge.EVENT_BUS.post(rescue);
            helper.assertTrue(rescue.isCanceled() && injured.getHealth() == injured.getMaxHealth(), "honey restores actual NPC health");
            helper.assertTrue(player.getMainHandItem().getCount() == 1 && UrbanStoryStorage.read(player).orElseThrow().completed(Objective.BROADCAST_RESCUE), "one bottle charged, rescue saved");
            var shared = new PlayerInteractEvent.EntityInteract(teammate, InteractionHand.MAIN_HAND, injured);
            NeoForge.EVENT_BUS.post(shared);
            helper.assertTrue(shared.isCanceled() && UrbanStoryStorage.read(teammate).orElseThrow().completed(Objective.BROADCAST_RESCUE), "teammate can acknowledge same-world rescue");
            helper.assertTrue(!cn.academy.port.story.UrbanStoryClues.canProbe(player,witness), "witness unavailable before chapter");
            // The source interaction is supplied as a fixture here; its structure-location check is separate.
            UrbanStoryStorage.recordVerified(player,Objective.BROADCAST_SOURCE,Approach.EQUIPMENT);
            helper.assertTrue(cn.academy.port.story.UrbanStoryClues.canProbe(player,witness), "marked visible witness accepted at the correct chapter");
            helper.assertTrue(cn.academy.port.story.UrbanStoryClues.probe(player,witness), "probe records the actual NPC clue");
            helper.assertTrue(!cn.academy.port.story.UrbanStoryClues.probe(player,witness), "clue cannot be recorded twice");
            helper.succeed();
        } finally { player.discard(); teammate.discard(); guide.discard(); injured.discard(); witness.discard(); }
    }

    @GameTest(template = "empty", batch = "urban_story", timeoutTicks = 100)
    public static void authored_station_template_loads_and_spawns_marked_students(GameTestHelper helper) {
        var level=helper.getLevel();
        var template=level.getStructureManager().get(ResourceLocation.fromNamespaceAndPath("academy","urban/station")).orElseThrow();
        helper.assertTrue(template.getSize().getX()==15 && template.getSize().getY()==7 && template.getSize().getZ()==13,
                "real generated template dimensions");
        var anchor=helper.absolutePos(new net.minecraft.core.BlockPos(2,1,2));
        var settings=new StructurePlaceSettings().setIgnoreEntities(false).setFinalizeEntities(true);
        helper.assertTrue(template.placeInWorld(level,anchor,anchor,settings,level.random,2), "station template placed");
        var residents=level.getEntitiesOfClass(Villager.class,new AABB(anchor).inflate(20), entity ->
                entity.getPersistentData().contains(cn.academy.port.story.UrbanStoryClues.WITNESS_KEY));
        var injured=residents.stream().filter(v->v.getPersistentData().getString(cn.academy.port.story.UrbanStoryClues.WITNESS_KEY)
                .equals("injured_student")).findFirst();
        var witness=residents.stream().filter(v->v.getPersistentData().getString(cn.academy.port.story.UrbanStoryClues.WITNESS_KEY)
                .equals("student_witness")).findFirst();
        helper.assertTrue(injured.isPresent() && injured.get().getHealth()<injured.get().getMaxHealth(), "real station spawns injured student");
        helper.assertTrue(witness.isPresent(), "real station spawns marked witness");
        residents.forEach(Villager::discard);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "urban_story_stairs", timeoutTicks = 400)
    public static void facility_student_can_navigate_authored_stairs(GameTestHelper helper) {
        navigateFacilityStairs(helper,net.minecraft.world.level.block.Rotation.NONE);
    }
    @GameTest(template = "empty", batch = "urban_story_stairs", timeoutTicks = 400)
    public static void facility_student_can_navigate_stairs_rotated_90(GameTestHelper helper) {
        navigateFacilityStairs(helper,net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
    }
    @GameTest(template = "empty", batch = "urban_story_stairs", timeoutTicks = 400)
    public static void facility_student_can_navigate_stairs_rotated_180(GameTestHelper helper) {
        navigateFacilityStairs(helper,net.minecraft.world.level.block.Rotation.CLOCKWISE_180);
    }
    @GameTest(template = "empty", batch = "urban_story_stairs", timeoutTicks = 400)
    public static void facility_student_can_navigate_stairs_rotated_270(GameTestHelper helper) {
        navigateFacilityStairs(helper,net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90);
    }
    private static void navigateFacilityStairs(GameTestHelper helper,net.minecraft.world.level.block.Rotation rotation) {
        var level=helper.getLevel();
        var template=level.getStructureManager().get(ResourceLocation.fromNamespaceAndPath("academy","urban/facility")).orElseThrow();
        helper.assertTrue(template.getSize().equals(new net.minecraft.core.Vec3i(15,10,15)),"facility includes the full half-block stair run");
        var anchor=helper.absolutePos(new BlockPos(16,1,16));
        var placement=new StructurePlaceSettings().setRotation(rotation).setIgnoreEntities(false);
        helper.assertTrue(template.placeInWorld(level,anchor,anchor,placement,level.random,2),"facility template placed");
        var student=level.getEntitiesOfClass(Villager.class,new AABB(anchor).inflate(20),entity ->
                "protocol_student".equals(entity.getPersistentData().getString(cn.academy.port.story.UrbanStoryClues.WITNESS_KEY))).stream().findFirst().orElseThrow();
        var destination=anchor.offset(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
                .calculateRelativePosition(placement,new BlockPos(2,7,13)));
        var inverse=switch(rotation) {
            case CLOCKWISE_90 -> net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> net.minecraft.world.level.block.Rotation.CLOCKWISE_90;
            default -> rotation;
        };
        // Structure entities are spawned in mid-tick with onGround false. Vanilla
        // ground navigation cannot plan until an actual physics tick lands them.
        student.setNoAi(false);
        boolean[] started = {false}; int[] refreshes = {0};
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(student.isAlive() && student.onGround(), "spawned student has landed on the chamber floor");
            var path=student.getNavigation().createPath(destination,0);
            helper.assertTrue(path!=null && path.canReach(),"villager can find a complete route up the actual staircase");
            helper.assertTrue(student.getNavigation().moveTo(path,0.9), "native villager navigation starts");
            started[0] = true;
        });
        // Register the whole schedule before the final clause. The production
        // escort similarly refreshes its target every ten ticks.
        for (int delay=10; delay<=390; delay+=10) helper.runAfterDelay(delay, () -> {
            if (started[0] && student.isAlive() && !student.isRemoved()
                    && student.getNavigation().moveTo(destination.getX()+.5, destination.getY(), destination.getZ()+.5, 0.9)) refreshes[0]++;
        });
        helper.succeedWhen(() -> {
            var feet=student.blockPosition();
            var local=net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
                    .calculateRelativePosition(new StructurePlaceSettings().setRotation(inverse),feet.subtract(anchor));
            var current=student.getNavigation().getPath();
            String details="; relative="+student.position().subtract(Vec3.atLowerCornerOf(anchor))
                    +", velocity="+student.getDeltaMovement()+", onGround="+student.onGround()
                    +", started="+started[0]+", navigationDone="+student.getNavigation().isDone()+", refreshes="+refreshes[0]
                    +", path="+(current==null ? "null" : current.getNextNodeIndex()+"/"+current.getNodeCount()+", reachable="+current.canReach());
            helper.assertTrue(started[0] && student.isAlive() && feet.getY()>=destination.getY()
                            && local.getX()>=1 && local.getX()<=3 && local.getZ()>=13
                            && student.distanceToSqr(Vec3.atBottomCenterOf(destination))<4,
                    "student must physically arrive on the authored roof exit"+details);
            student.discard();
        });
    }

    @GameTest(template = "empty", batch = "urban_story", timeoutTicks = 100)
    public static void disconnected_owner_can_be_replaced_after_student_leaves_facility(GameTestHelper helper) {
        var owner = new EscortPlayer(helper); var successor = new EscortPlayer(helper);
        var student = helper.spawn(EntityType.VILLAGER, new Vec3(4.5, 3, 4.5));
        boolean ownerClosed = false;
        try {
            var position = helper.absoluteVec(new Vec3(4.5, 3, 5.5));
            owner.player.moveTo(position.x, position.y, position.z, 0, 0);
            successor.player.moveTo(position.x, position.y, position.z, 0, 0);
            successor.player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.LEAD));
            for (Objective objective : Objective.values()) {
                if (objective.chapter() == Chapter.CALIBRATOR) break;
                helper.assertTrue(UrbanStoryStorage.recordVerified(successor.player, objective, Approach.EQUIPMENT)
                        .orElse(null)==UrbanStoryProgress.Result.RECORDED, "successor has earned the final chapter");
            }
            student.setNoGravity(true); student.setNoAi(true);
            student.getPersistentData().putString(cn.academy.port.story.UrbanStoryClues.WITNESS_KEY,"protocol_student");
            // A rescued NPC outside a facility without a previous server-owned
            // escort must not be converted into arbitrary evacuation evidence.
            student.getPersistentData().putBoolean("academy:urban_rescued",true);
            var noHistory = new PlayerInteractEvent.EntityInteract(successor.player,InteractionHand.MAIN_HAND,student);
            NeoForge.EVENT_BUS.post(noHistory);
            helper.assertFalse(noHistory.isCanceled(), "unstarted outside student cannot be claimed");
            // A genuine pre-upgrade escort has an owner but not yet the new
            // persistent started bit. An online owner cannot be stolen.
            student.getPersistentData().putUUID("academy:urban_escort_owner",owner.player.getUUID());
            var contested = new PlayerInteractEvent.EntityInteract(successor.player,InteractionHand.MAIN_HAND,student);
            NeoForge.EVENT_BUS.post(contested);
            helper.assertFalse(contested.isCanceled(), "live same-world owner retains the escort");
            owner.close(); ownerClosed = true;
            student.tickCount=0;
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(student));
            helper.assertTrue(student.getPersistentData().getBoolean("academy:urban_escort_started")
                    && !student.getPersistentData().hasUUID("academy:urban_escort_owner"),
                    "disconnect releases owner but preserves prior escort proof");
            successor.player.moveTo(position.x+20, position.y, position.z, 0, 0);
            var far = new PlayerInteractEvent.EntityInteract(successor.player,InteractionHand.MAIN_HAND,student);
            NeoForge.EVENT_BUS.post(far);
            helper.assertFalse(far.isCanceled(), "a distant successor cannot claim the student");
            successor.player.moveTo(position.x, position.y, position.z, 0, 0);
            student.getPersistentData().putBoolean("academy:urban_rescued",false);
            var unrescued = new PlayerInteractEvent.EntityInteract(successor.player,InteractionHand.MAIN_HAND,student);
            NeoForge.EVENT_BUS.post(unrescued);
            helper.assertFalse(unrescued.isCanceled(), "a student without a verified rescue cannot be claimed");
            student.getPersistentData().putBoolean("academy:urban_rescued",true);
            student.getPersistentData().putString(cn.academy.port.story.UrbanStoryClues.WITNESS_KEY,"student_witness");
            var wrongRole = new PlayerInteractEvent.EntityInteract(successor.player,InteractionHand.MAIN_HAND,student);
            NeoForge.EVENT_BUS.post(wrongRole);
            helper.assertFalse(wrongRole.isCanceled(), "another witness cannot be claimed as the rescued student");
            student.getPersistentData().putString(cn.academy.port.story.UrbanStoryClues.WITNESS_KEY,"protocol_student");
            var resumed = new PlayerInteractEvent.EntityInteract(successor.player,InteractionHand.MAIN_HAND,student);
            NeoForge.EVENT_BUS.post(resumed);
            helper.assertTrue(resumed.isCanceled() && student.getPersistentData().getUUID("academy:urban_escort_owner")
                    .equals(successor.player.getUUID()) && !student.isNoAi(), "nearby successor resumes the actual escort");
            helper.assertFalse(UrbanStoryStorage.read(successor.player).orElseThrow().completed(Objective.CALIBRATOR_EVACUATION),
                    "claiming alone does not grant evacuation");
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(student));
            helper.assertTrue(UrbanStoryStorage.read(successor.player).orElseThrow().completed(Objective.CALIBRATOR_EVACUATION)
                    && student.getPersistentData().getBoolean("academy:urban_evacuated"),
                    "real tick confirms player and student are together outside the facility");
            helper.succeed();
        } finally { student.discard(); successor.close(); if (!ownerClosed) owner.close(); }
    }

    private static final class EscortPlayer {
        final ServerPlayer player; final EmbeddedChannel channel;
        EscortPlayer(GameTestHelper helper) {
            UUID id=UUID.randomUUID(); var level=helper.getLevel();
            var cookie=CommonListenerCookie.createInitial(new GameProfile(id,"UE"+id.toString().substring(0,8)),false);
            player=new ServerPlayer(level.getServer(),level,cookie.gameProfile(),cookie.clientInformation());
            var connection=new Connection(PacketFlow.SERVERBOUND); channel=new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            level.getServer().getPlayerList().placeNewPlayer(connection,player,cookie);
        }
        void close() { try { player.server.getPlayerList().remove(player); } finally { channel.finishAndReleaseAll(); } }
    }

    @GameTest(template = "empty", batch = "urban_story", timeoutTicks = 100)
    public static void electromagnetic_arc_operates_real_facility_switch_once(GameTestHelper h) {
        var owner = new EscortPlayer(h); var equipment = new EscortPlayer(h); var p=owner.player; var level=h.getLevel();
        BlockPos pos=h.absolutePos(new BlockPos(4,3,4)); var chunk=level.getChunk(pos);
        var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(ResourceLocation.fromNamespaceAndPath("academy","urban_facility"));
        var oldStarts=new java.util.HashMap<>(chunk.getAllStarts());
        var oldRefs=new java.util.HashMap<Structure,it.unimi.dsi.fastutil.longs.LongSet>();
        chunk.getAllReferences().forEach((key,value)->oldRefs.put(key,new it.unimi.dsi.fastutil.longs.LongOpenHashSet(value)));
        try {
            h.assertTrue(structure!=null,"registered facility type exists");
            var piece=new StoryTestPiece(new BoundingBox(pos.getX(),pos.getY()-1,pos.getZ(),pos.getX(),pos.getY()+1,pos.getZ()));
            var chunkPos=new ChunkPos(pos); var start=new StructureStart(structure,chunkPos,0,new PiecesContainer(java.util.List.of(piece)));
            chunk.setStartForStructure(structure,start); chunk.addReferenceForStructure(structure,chunkPos.toLong());
            h.assertTrue(level.structureManager().getStructureWithPieceAt(pos,TagKey.create(Registries.STRUCTURE,
                    ResourceLocation.fromNamespaceAndPath("academy","urban_facility"))).isValid(),"real structure membership fixture");
            level.setBlock(pos.below(),Blocks.MAGENTA_TERRACOTTA.defaultBlockState(),3);
            level.setBlock(pos,Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE,net.minecraft.world.level.block.state.properties.AttachFace.FLOOR).setValue(LeverBlock.POWERED,true),3);
            for(Objective objective:Objective.values()) {
                if(objective.chapter()==Chapter.PROTOCOL) break;
                h.assertTrue(UrbanStoryStorage.recordVerified(p,objective,Approach.EQUIPMENT).orElse(null)==UrbanStoryProgress.Result.RECORDED,
                        "earlier objectives recorded");
            }
            p.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+2.5,0,0); p.setShiftKeyDown(true);
            var ability=AbilityStorage.get(p);ability.changeCategoryClassic("electromaster");ability.setLevel(5);
            ability.learn("forked_arc");ability.setActivateState(true);
            h.assertTrue(ability.presets.edit(0,0,"forked_arc",ability::learned),"arc bound to current preset");
            h.assertTrue(HighPowerCombat.enroll(p,5),"server exact ledger enrolled");HighPowerResources.initializeBudget(p);
            double before=HighPowerResources.cp(p);
            p.lookAt(EntityAnchorArgument.Anchor.EYES,new Vec3(pos.getX()+.5,pos.getY()+.25,pos.getZ()-3));
            var wrongAim=leverEvent(p,pos); NeoForge.EVENT_BUS.post(wrongAim);
            h.assertTrue(wrongAim.isCanceled() && HighPowerResources.cp(p)==before
                    && level.getBlockState(pos).getValue(LeverBlock.POWERED),"wrong aim refuses without paying or operating node");
            p.lookAt(EntityAnchorArgument.Anchor.EYES,new Vec3(pos.getX()+.5,pos.getY()+.25,pos.getZ()+.5));
            var hit=leverEvent(p,pos); NeoForge.EVENT_BUS.post(hit);
            h.assertTrue(hit.isCanceled() && !level.getBlockState(pos).getValue(LeverBlock.POWERED),"paid arc physically turns off lever");
            h.assertTrue(HighPowerResources.cp(p)<before,"actual arc debits shared CP");
            h.assertTrue(UrbanStoryStorage.read(p).orElseThrow().approach(Objective.PROTOCOL_ARRAY).orElse(null)==Approach.ABILITY,
                    "recorded only after real ability operation");
            double paid=HighPowerResources.cp(p);NeoForge.EVENT_BUS.post(leverEvent(p,pos));
            h.assertTrue(HighPowerResources.cp(p)==paid,"same objective cannot debit twice");
            var teammate=equipment.player;
            for(Objective objective:Objective.values()) {
                if(objective.chapter()==Chapter.PROTOCOL) break;
                h.assertTrue(UrbanStoryStorage.recordVerified(teammate,objective,Approach.EQUIPMENT).orElse(null)
                        ==UrbanStoryProgress.Result.RECORDED,"equipment player has earned protocol chapter");
            }
            teammate.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+2.5,0,0);
            var acknowledge=leverEvent(teammate,pos);NeoForge.EVENT_BUS.post(acknowledge);
            h.assertTrue(acknowledge.isCanceled() && UrbanStoryStorage.read(teammate).orElseThrow()
                    .approach(Objective.PROTOCOL_ARRAY).orElse(null)==Approach.EQUIPMENT,
                    "teammate can acknowledge already disabled node using normal equipment route");
            h.succeed();
        } finally { chunk.setAllStarts(oldStarts);chunk.setAllReferences(oldRefs);equipment.close();owner.close(); }
    }

    private static PlayerInteractEvent.RightClickBlock leverEvent(ServerPlayer p,BlockPos pos) {
        return new PlayerInteractEvent.RightClickBlock(p,InteractionHand.MAIN_HAND,pos,
                new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
    }

    private static final class StoryTestPiece extends StructurePiece {
        StoryTestPiece(BoundingBox box) { super(StructurePieceType.JIGSAW,0,box); }
        @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag) {}
        @Override public void postProcess(net.minecraft.world.level.WorldGenLevel world,net.minecraft.world.level.StructureManager manager,
                net.minecraft.world.level.chunk.ChunkGenerator generator,net.minecraft.util.RandomSource random,
                BoundingBox box,ChunkPos chunk,BlockPos pos) {}
    }

    @GameTest(template = "empty", batch = "urban_story", timeoutTicks = 100)
    public static void combat_guard_requires_real_committed_death_and_survives_owner_disconnect(GameTestHelper h) {
        var owner=new EscortPlayer(h); var teammate=new EscortPlayer(h); boolean ownerClosed=false;
        var world=h.getLevel(); BlockPos origin=h.absolutePos(BlockPos.ZERO), base=h.absolutePos(new BlockPos(4,3,4));
        // A chunk-centred node keeps the whole fixture in one chunk while the
        // 9x9 floor and aiming corridor remain inside this test's 32x32 box.
        int nx=origin.getX()+5+Math.floorMod(8-(origin.getX()+5),16);
        int nz=origin.getZ()+5+Math.floorMod(8-(origin.getZ()+5),16);
        BlockPos node=new BlockPos(nx,base.getY(),nz);ChunkPos cp=new ChunkPos(node);var chunk=world.getChunk(node);
        var originalBlocks=new java.util.LinkedHashMap<BlockPos,StoryBlockSnapshot>();
        var structure=world.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(ResourceLocation.fromNamespaceAndPath("academy","urban_facility"));
        var oldStarts=new java.util.HashMap<>(chunk.getAllStarts());
        var oldRefs=new java.util.HashMap<Structure,it.unimi.dsi.fastutil.longs.LongSet>();
        chunk.getAllReferences().forEach((key,value)->oldRefs.put(key,new it.unimi.dsi.fastutil.longs.LongOpenHashSet(value)));
        Zombie guard=null, normal=null; GuardVeto hook=null;
        try {
            h.assertTrue(structure!=null,"registered facility type exists");
            var box=new BoundingBox(node.getX()-4,node.getY()-1,node.getZ()-4,node.getX()+4,node.getY()+4,node.getZ()+4);
            var start=new StructureStart(structure,cp,0,new PiecesContainer(java.util.List.of(new StoryTestPiece(box))));
            chunk.setStartForStructure(structure,start);chunk.addReferenceForStructure(structure,cp.toLong());
            for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++) snapshotStoryBlock(world,originalBlocks,node.offset(x,-1,z));
            for(int x=-1;x<=1;x++)for(int z=-1;z<=3;z++)for(int y=0;y<=3;y++)
                snapshotStoryBlock(world,originalBlocks,node.offset(x,y,z));
            for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++) world.setBlock(node.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
            for(int x=-1;x<=1;x++)for(int z=-1;z<=3;z++)for(int y=0;y<=3;y++)
                world.setBlock(node.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            world.setBlock(node.below(),Blocks.MAGENTA_TERRACOTTA.defaultBlockState(),3);
            world.setBlock(node,Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE,net.minecraft.world.level.block.state.properties.AttachFace.FLOOR).setValue(LeverBlock.POWERED,true),3);
            for(ServerPlayer player:new ServerPlayer[]{owner.player,teammate.player}) {
                for(Objective objective:Objective.values()) {
                    if(objective.chapter()==Chapter.PROTOCOL)break;
                    h.assertTrue(UrbanStoryStorage.recordVerified(player,objective,Approach.EQUIPMENT).orElse(null)==UrbanStoryProgress.Result.RECORDED,
                            "player earned protocol chapter");
                }
                player.moveTo(node.getX()+.5,node.getY(),node.getZ()+2.5,0,0);
                player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));player.setShiftKeyDown(true);
                player.lookAt(EntityAnchorArgument.Anchor.EYES,new Vec3(node.getX()+.5,node.getY()+.25,node.getZ()+.5));
            }
            normal=EntityType.ZOMBIE.create(world);h.assertTrue(normal!=null,"ordinary hostile created");
            normal.moveTo(node.getX()+2.5,node.getY(),node.getZ()+.5,0,0);world.addFreshEntity(normal);
            normal.hurt(world.damageSources().playerAttack(owner.player),1000);
            h.assertFalse(UrbanStoryStorage.read(owner.player).orElseThrow().completed(Objective.PROTOCOL_ARRAY),
                    "ordinary hostile death cannot solve a node");
            normal.discard();normal=null;
            var tag=TagKey.create(Registries.STRUCTURE,ResourceLocation.fromNamespaceAndPath("academy","urban_facility"));
            h.assertTrue(world.structureManager().getStructureWithPieceAt(node,tag).isValid(),"guard test node has real facility membership");
            var eye=owner.player.getEyePosition();
            var ray=world.clip(new net.minecraft.world.level.ClipContext(eye,eye.add(owner.player.getLookAngle().scale(6)),
                    net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,owner.player));
            h.assertTrue(ray instanceof BlockHitResult hit && hit.getBlockPos().equals(node),"sword challenge ray must hit node: type="+ray.getType()+", hit="
                    +(ray instanceof BlockHitResult hit ? hit.getBlockPos() : "none")+", expected="+node
                    +", eye="+eye+", direction="+owner.player.getLookAngle()+", state="+world.getBlockState(node));
            Zombie probe=EntityType.ZOMBIE.create(world);int free=0;
            for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++) {
                if(dx==0 && dz==0)continue;BlockPos at=node.offset(dx,0,dz);
                probe.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
                if(world.hasChunkAt(at) && world.mayInteract(owner.player,at)
                        && world.structureManager().getStructureWithPieceAt(at,tag).isValid()
                        && world.getBlockState(at.below()).isFaceSturdy(world,at.below(),Direction.UP)
                        && world.noCollision(probe,probe.getBoundingBox()))free++;
            }
            h.assertTrue(free>0,"facility must have unobstructed guard spawn candidates; node="+node+", player="+owner.player.position());
            var challenge=leverEvent(owner.player,node);NeoForge.EVENT_BUS.post(challenge);
            var guards=world.getEntitiesOfClass(Zombie.class,new AABB(node).inflate(6),z->z.getPersistentData().getBoolean("academy:urban_guard"));
            h.assertTrue(challenge.isCanceled() && guards.size()==1,"challenge creates one real owned guard: canceled="+challenge.isCanceled()
                    +", result="+challenge.getCancellationResult()+", count="+guards.size()+", free="+free
                    +", shift="+owner.player.isShiftKeyDown()+", item="+owner.player.getMainHandItem()
                    +", chapter="+UrbanStoryStorage.read(owner.player).map(UrbanStoryProgress::chapter));
            guard=guards.getFirst();UUID removedId=guard.getUUID();guard.discard();
            var reissued=leverEvent(owner.player,node);NeoForge.EVENT_BUS.post(reissued);
            guards=world.getEntitiesOfClass(Zombie.class,new AABB(node).inflate(6),
                    z->z.getPersistentData().getBoolean("academy:urban_guard") && !z.isRemoved());
            h.assertTrue(reissued.isCanceled() && guards.size()==1 && !guards.getFirst().getUUID().equals(removedId),
                    "discarded guard releases its persistent binding for a new challenge");
            guard=guards.getFirst();
            world.setBlock(node.below(),Blocks.RED_TERRACOTTA.defaultBlockState(),3);
            guard.hurt(world.damageSources().playerAttack(owner.player),1000);
            h.assertTrue(world.getBlockState(node).getValue(LeverBlock.POWERED)
                    && !UrbanStoryStorage.read(owner.player).orElseThrow().completed(Objective.PROTOCOL_ARRAY),
                    "a guard cannot solve a node whose bound color changed");
            guard.discard();world.setBlock(node.below(),Blocks.MAGENTA_TERRACOTTA.defaultBlockState(),3);
            NeoForge.EVENT_BUS.post(leverEvent(owner.player,node));
            guards=world.getEntitiesOfClass(Zombie.class,new AABB(node).inflate(6),
                    z->z.getPersistentData().getBoolean("academy:urban_guard") && !z.isRemoved());
            h.assertTrue(guards.size()==1,"valid node can be challenged after an invalidated guard dies");
            guard=guards.getFirst();hook=new GuardVeto(guard);NeoForge.EVENT_BUS.register(hook);
            guard.hurt(world.damageSources().playerAttack(owner.player),1000);
            h.assertTrue(hook.deathCalls==1 && !UrbanStoryStorage.read(owner.player).orElseThrow().completed(Objective.PROTOCOL_ARRAY)
                    && world.getBlockState(node).getValue(LeverBlock.POWERED),"vetoed death never closes or credits node");
            guard.setHealth(guard.getMaxHealth());guard.invulnerableTime=0;
            owner.close();ownerClosed=true;
            teammate.player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            var spectator=leverEvent(teammate.player,node);NeoForge.EVENT_BUS.post(spectator);
            h.assertFalse(spectator.isCanceled(),"spectator cannot take over the encounter");
            teammate.player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            var retry=leverEvent(teammate.player,node);NeoForge.EVENT_BUS.post(retry);
            h.assertTrue(retry.isCanceled() && world.getEntitiesOfClass(Zombie.class,new AABB(node).inflate(6),
                    z->z.getPersistentData().getBoolean("academy:urban_guard") && !z.isRemoved()).size()==1,
                    "teammate reconnects to same living guard without duplicate");
            hook.veto=false;hook.cancelDrops=true;
            guard.hurt(world.damageSources().playerAttack(teammate.player),1000);
            h.assertTrue(hook.dropCalls==1 && !world.getBlockState(node).getValue(LeverBlock.POWERED),
                    "cancelled drops do not prevent committed death closing the physical lever");
            h.assertTrue(UrbanStoryStorage.read(teammate.player).orElseThrow().approach(Objective.PROTOCOL_ARRAY).orElse(null)==Approach.COMBAT,
                    "only the eligible killer receives combat evidence");
            h.assertFalse(UrbanStoryStorage.read(owner.player).orElseThrow().completed(Objective.PROTOCOL_ARRAY),
                    "offline owner does not receive another player's progress");
            h.succeed();
        } finally {
            if(hook!=null)NeoForge.EVENT_BUS.unregister(hook);if(guard!=null)guard.discard();if(normal!=null)normal.discard();
            chunk.setAllStarts(oldStarts);chunk.setAllReferences(oldRefs);
            originalBlocks.forEach((pos,snapshot)->{
                world.setBlock(pos,snapshot.state(),2);
                if(snapshot.blockEntity()!=null && world.getBlockEntity(pos)!=null) {
                    world.getBlockEntity(pos).loadWithComponents(snapshot.blockEntity(),world.registryAccess());
                    world.getBlockEntity(pos).setChanged();
                }
            });
            teammate.close();if(!ownerClosed)owner.close();
        }
    }

    private record StoryBlockSnapshot(net.minecraft.world.level.block.state.BlockState state,CompoundTag blockEntity) {}
    private static void snapshotStoryBlock(ServerLevel world,java.util.Map<BlockPos,StoryBlockSnapshot> saved,BlockPos pos) {
        var entity=world.getBlockEntity(pos);
        saved.putIfAbsent(pos.immutable(),new StoryBlockSnapshot(world.getBlockState(pos),
                entity==null?null:entity.saveWithFullMetadata(world.registryAccess()).copy()));
    }

    public static final class GuardVeto {
        final Zombie guard; boolean veto=true,cancelDrops; int deathCalls,dropCalls;
        GuardVeto(Zombie guard){this.guard=guard;}
        @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST) public void death(LivingDeathEvent event) {
            if(event.getEntity()==guard){deathCalls++;if(veto)event.setCanceled(true);}
        }
        @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST) public void drops(LivingDropsEvent event) {
            if(event.getEntity()==guard){dropCalls++;if(cancelDrops)event.setCanceled(true);}
        }
    }

    public static final class VetoDeath {
        private final cn.academy.port.story.UrbanCalibrator.Boss target;
        VetoDeath(cn.academy.port.story.UrbanCalibrator.Boss target){this.target=target;}
        @SubscribeEvent public void death(LivingDeathEvent event){if(event.getEntity()==target)event.setCanceled(true);}
    }
    @GameTest(template = "empty", batch = "urban_story", timeoutTicks = 100)
    public static void calibrator_accepts_native_weapons_and_requires_committed_death(GameTestHelper helper) {
        var level=helper.getLevel();
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"[UrbanBoss]"));
        var boss=cn.academy.port.story.UrbanCalibrator.BOSS.get().create(level);
        if(boss==null)throw new AssertionError("calibrator creation");
        var at=helper.absoluteVec(new Vec3(4,1,4));boss.moveTo(at.x,at.y,at.z,0,0);boss.setNoGravity(true);level.addFreshEntity(boss);
        try {
            for(Objective objective:Objective.values()) {
                if(objective==Objective.CALIBRATOR_DEFEATED)break;
                helper.assertTrue(UrbanStoryStorage.recordVerified(player,objective,Approach.EQUIPMENT).orElseThrow()==Result.RECORDED,
                        "fixture prerequisites " + objective);
            }
            helper.assertTrue(cn.academy.port.highpower.VanillaCombatContract.supportedClass(boss),"exact class is admitted");
            helper.assertTrue(cn.academy.port.highpower.HighPowerCombat.enroll(boss,5),"boss receives high-power ledger");
            var before=new java.math.BigDecimal(cn.academy.port.highpower.HighPowerCombat.healthText(boss))
                    .add(new java.math.BigDecimal(cn.academy.port.highpower.HighPowerCombat.shieldText(boss)));
            var source=player.damageSources().playerAttack(player);
            var veto=new VetoDeath(boss);NeoForge.EVENT_BUS.register(veto);
            try { boss.die(source); }
            finally { NeoForge.EVENT_BUS.unregister(veto); }
            helper.assertTrue(!UrbanStoryStorage.read(player).orElseThrow().completed(Objective.CALIBRATOR_DEFEATED),
                    "vetoed death does not complete the quest");
            helper.assertTrue(boss.hurt(source,10),"ordinary player weapon hits boss");
            var after=new java.math.BigDecimal(cn.academy.port.highpower.HighPowerCombat.healthText(boss))
                    .add(new java.math.BigDecimal(cn.academy.port.highpower.HighPowerCombat.shieldText(boss)));
            helper.assertTrue(before.subtract(after).compareTo(before.multiply(new java.math.BigDecimal(".001")))>0,
                    "ordinary weapon meaningfully damages exact health or shield");
            for(int i=0;i<8 && boss.isAlive();i++){boss.invulnerableTime=0;boss.hurt(source,100);}
            helper.assertTrue(!boss.isAlive(),"repeated ordinary weapon attacks can defeat boss");
            helper.assertTrue(UrbanStoryStorage.read(player).orElseThrow().completed(Objective.CALIBRATOR_DEFEATED),
                    "only committed player-caused death completes the quest");
            helper.succeed();
        } finally { boss.discard(); player.discard(); }
    }

    private static final class RouteSite implements AutoCloseable {
        final ServerLevel world; final BlockPos center; final net.minecraft.world.level.chunk.ChunkAccess chunk;
        final java.util.Map<Structure,StructureStart> starts; final java.util.Map<Structure,it.unimi.dsi.fastutil.longs.LongSet> refs=new java.util.HashMap<>();
        RouteSite(GameTestHelper h,String id) {
            world=h.getLevel();BlockPos origin=h.absolutePos(new BlockPos(8,3,8));
            center=new BlockPos((origin.getX()&~15)+8,origin.getY(),(origin.getZ()&~15)+8);chunk=world.getChunk(center);
            starts=new java.util.HashMap<>(chunk.getAllStarts());chunk.getAllReferences().forEach((k,v)->refs.put(k,new it.unimi.dsi.fastutil.longs.LongOpenHashSet(v)));
            var type=world.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.fromNamespaceAndPath("academy",id));
            var cp=new ChunkPos(center);var box=new BoundingBox(center.getX()-6,center.getY()-1,center.getZ()-6,center.getX()+6,center.getY()+7,center.getZ()+6);
            chunk.setStartForStructure(type,new StructureStart(type,cp,0,new PiecesContainer(java.util.List.of(new StoryTestPiece(box)))));chunk.addReferenceForStructure(type,cp.toLong());
            for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++){
                world.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
                for(int y=0;y<=6;y++)world.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
        }
        BlockPos table(){return center.offset(0,2,2);}
        void puzzle(){world.setBlock(center.below(),Blocks.GOLD_BLOCK.defaultBlockState(),3);
            world.setBlock(center.offset(-1,-1,0),Blocks.IRON_BLOCK.defaultBlockState(),3);world.setBlock(center.offset(1,-1,0),Blocks.IRON_BLOCK.defaultBlockState(),3);
            world.setBlock(center.offset(0,0,2),Blocks.QUARTZ_BRICKS.defaultBlockState(),3);world.setBlock(center.offset(0,1,2),Blocks.QUARTZ_BRICKS.defaultBlockState(),3);
            world.setBlock(table(),Blocks.CARTOGRAPHY_TABLE.defaultBlockState(),3);}
        public void close(){chunk.setAllStarts(starts);chunk.setAllReferences(refs);}
    }
    @GameTest(template="empty",batch="urban_routes",timeoutTicks=100)
    public static void paper_route_requires_owned_live_bridge_and_physical_standing(GameTestHelper h) {
        var owner=new EscortPlayer(h);var foreign=new EscortPlayer(h);var site=new RouteSite(h,"urban_campus");var p=owner.player;var c=site.center;
        try {
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);site.puzzle();var state=AbilityStorage.get(p);state.changeCategoryClassic("paper_psychokinesis");state.setLevel(5);state.learn("paper_walkway");state.learn("paper_patch");state.setActivateState(true);
            state.presets.edit(0,0,"paper_walkway",state::learned);HighPowerCombat.enroll(p,5);HighPowerResources.initializeBudget(p);
            p.moveTo(c.getX()+3.5,c.getY(),c.getZ()+.5,0,0);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,6));
            p.lookAt(EntityAnchorArgument.Anchor.EYES,c.offset(-1,-1,0).getCenter().add(0,.5,0));
            h.assertTrue(cn.academy.port.canon.CanonSkillsCombatB.cast(p,"paper_walkway"),"real six-paper walkway casts onto metal sockets");
            h.assertTrue(p.getMainHandItem().isEmpty(),"actual paper material consumed");
            for(int x=-1;x<=1;x++)h.assertTrue(cn.academy.port.canon.CanonPaperContent.owned(p,c.offset(x,0,0)),"live owned collision segment "+x);
            p.setOnGround(true);NeoForge.EVENT_BUS.post(leverEvent(p,site.table()));
            h.assertFalse(UrbanStoryStorage.read(p).orElseThrow().completed(Objective.BROADCAST_LEAD),"standing beside a construct is no puzzle proof");
            var q=foreign.player;AbilityStorage.get(q).changeCategoryClassic("paper_psychokinesis");q.moveTo(c.getX()+.5,c.getY()+1,c.getZ()+.5,0,0);q.setOnGround(true);
            NeoForge.EVENT_BUS.post(leverEvent(q,site.table()));h.assertFalse(UrbanStoryStorage.read(q).orElseThrow().completed(Objective.BROADCAST_LEAD),"another player's paper cannot be claimed");
            q.moveTo(c.getX()+4.5,c.getY(),c.getZ()+.5,0,0);p.moveTo(c.getX()+.5,c.getY()+1,c.getZ()+.5,0,0);p.setOnGround(true);
            NeoForge.EVENT_BUS.post(leverEvent(p,site.table()));
            h.assertTrue(UrbanStoryStorage.read(p).orElseThrow().approach(Objective.BROADCAST_LEAD).orElse(null)==Approach.ABILITY,"actual bridge and feet record ability route");
            for(int x=-1;x<=1;x++)h.assertTrue(site.world.getBlockState(c.offset(x,0,0)).isAir(),"completed puzzle consumes every owned segment");
            h.assertTrue(UrbanStoryStorage.read(q).orElseThrow().snapshot().isEmpty(),"other player progress remains separate");h.succeed();
        } finally {site.close();foreign.close();owner.close();}
    }
    @GameTest(template="empty",batch="urban_routes",timeoutTicks=100)
    public static void combat_route_requires_the_actual_bound_evidence_guard(GameTestHelper h) {
        var owner=new EscortPlayer(h);var site=new RouteSite(h,"urban_campus");var p=owner.player;Zombie guard=null;
        try {p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);site.puzzle();p.moveTo(site.center.getX()+3.5,site.center.getY(),site.center.getZ()+1.5,0,0);
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));p.setShiftKeyDown(true);
            var challenge=leverEvent(p,site.table());NeoForge.EVENT_BUS.post(challenge);
            var guards=site.world.getEntitiesOfClass(Zombie.class,new AABB(site.center).inflate(6),z->z.isAlive()&&z.getPersistentData().getBoolean("academy:urban_guard"));
            h.assertTrue(challenge.isCanceled()&&guards.size()==1,"real evidence desk creates one bound guard");guard=guards.getFirst();
            h.assertTrue(UrbanStoryStorage.read(p).orElseThrow().snapshot().isEmpty(),"challenge alone grants no clue");
            guard.hurt(site.world.damageSources().playerAttack(p),1000);
            h.assertTrue(UrbanStoryStorage.read(p).orElseThrow().approach(Objective.BROADCAST_LEAD).orElse(null)==Approach.COMBAT,"committed guard death records combat clue");h.succeed();
        } finally {if(guard!=null)guard.discard();site.close();owner.close();}
    }
    @GameTest(template="empty",batch="urban_routes",timeoutTicks=100)
    public static void equipment_route_neutralizes_a_real_calibrator_and_charges_control_bus(GameTestHelper h) {
        var owner=new EscortPlayer(h);var site=new RouteSite(h,"urban_facility");var p=owner.player;
        try {p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);for(Objective objective:Objective.values())if(objective!=Objective.CALIBRATOR_DEFEATED)UrbanStoryStorage.recordVerified(p,objective,Approach.EQUIPMENT);
            var podium=site.center;site.world.setBlock(podium,Blocks.AMETHYST_BLOCK.defaultBlockState(),3);p.moveTo(podium.getX()+3.5,podium.getY(),podium.getZ()+.5,0,0);
            NeoForge.EVENT_BUS.post(leverEvent(p,podium));var bosses=site.world.getEntitiesOfClass(cn.academy.port.story.UrbanCalibrator.Boss.class,new AABB(podium).inflate(12),b->b.isAlive());
            h.assertTrue(bosses.size()==1,"physical podium starts real enrolled boss");p.setShiftKeyDown(true);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.REDSTONE,2));
            NeoForge.EVENT_BUS.post(leverEvent(p,podium));h.assertTrue(bosses.getFirst().isAlive()&&!bosses.getFirst().isRemoved(),"insufficient wiring cannot stop machine");
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.REDSTONE,3));NeoForge.EVENT_BUS.post(leverEvent(p,podium));
            h.assertTrue(bosses.getFirst().isRemoved()&&p.getMainHandItem().isEmpty(),"three real redstone consumed and machine removed");
            h.assertTrue(UrbanStoryStorage.read(p).orElseThrow().approach(Objective.CALIBRATOR_DEFEATED).orElse(null)==Approach.EQUIPMENT,"verified shutdown ends correct route");h.succeed();
        } finally {site.world.getEntitiesOfClass(cn.academy.port.story.UrbanCalibrator.Boss.class,new AABB(site.center).inflate(12)).forEach(net.minecraft.world.entity.Entity::discard);site.close();owner.close();}
    }

    // GameTestServer forces generateStructures=false; exercise this probe only in a normal world.
    public static void naturally_generated_station_is_locatable_and_console_can_be_investigated(GameTestHelper helper) {
        var level=helper.getLevel();
        var key=TagKey.create(Registries.STRUCTURE,ResourceLocation.fromNamespaceAndPath("academy","urban_station"));
        var found=level.findNearestMapStructure(key,helper.absolutePos(BlockPos.ZERO),4,false);
        helper.assertTrue(found!=null,"station must be locatable in the generated test world");
        if(found==null)return;
        level.getChunk(found);
        var type=level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(ResourceLocation.fromNamespaceAndPath("academy","urban_station"));
        var starts=level.structureManager().startsForStructure(new ChunkPos(found),candidate->candidate==type);
        helper.assertTrue(!starts.isEmpty(),"located structure has an actual registered start");
        if(starts.isEmpty())return;
        var box=starts.getFirst().getBoundingBox();
        for(int x=box.minX()>>4;x<=box.maxX()>>4;x++)for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++)level.getChunk(x,z);
        BlockPos console=null;
        for(BlockPos p:BlockPos.betweenClosed(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ()))
            if(level.getBlockState(p).is(Blocks.LECTERN)){console=p.immutable();break;}
        helper.assertTrue(console!=null,"naturally generated station contains its investigation console");
        if(console==null)return;
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"[UrbanWorldgen]"));
        try {
            player.moveTo(console.getX()+.5,console.getY()+1,console.getZ()+1.5,0,0);
            var hit=new BlockHitResult(Vec3.atCenterOf(console),Direction.UP,console,false);
            var event=new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,console,hit);
            NeoForge.EVENT_BUS.post(event);
            helper.assertTrue(event.isCanceled() && UrbanStoryStorage.read(player).orElseThrow().completed(Objective.BROADCAST_SOURCE),
                    "actual station console advances the server-owned source objective");
            helper.succeed();
        } finally {player.discard();}
    }

    // GameTestServer forces generateStructures=false; exercise this probe only in a normal world.
    public static void generated_facility_has_student_switches_and_calibrator(GameTestHelper helper) {
        var level=helper.getLevel();
        var key=TagKey.create(Registries.STRUCTURE,ResourceLocation.fromNamespaceAndPath("academy","urban_facility"));
        var found=level.findNearestMapStructure(key,helper.absolutePos(BlockPos.ZERO),4,false);
        helper.assertTrue(found!=null,"facility must be locatable in generated test world");
        if(found==null)return;
        level.getChunk(found);
        var type=level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(ResourceLocation.fromNamespaceAndPath("academy","urban_facility"));
        var starts=level.structureManager().startsForStructure(new ChunkPos(found),candidate->candidate==type);
        helper.assertTrue(!starts.isEmpty(),"facility has a real generated structure start");
        if(starts.isEmpty())return;
        var box=starts.getFirst().getBoundingBox();
        for(int x=box.minX()>>4;x<=box.maxX()>>4;x++)for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++)level.getChunk(x,z);
        var markers=new java.util.HashMap<String,BlockPos>();
        for(BlockPos p:BlockPos.betweenClosed(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ())){
            var state=level.getBlockState(p);
            if(state.is(Blocks.LECTERN))markers.put("console",p.immutable());
            if(state.is(Blocks.AMETHYST_BLOCK))markers.put("podium",p.immutable());
            if(state.is(Blocks.LEVER)){
                var floor=level.getBlockState(p.below());
                if(floor.is(Blocks.MAGENTA_TERRACOTTA))markers.put("master",p.immutable());
                if(floor.is(Blocks.RED_TERRACOTTA))markers.put("red",p.immutable());
                if(floor.is(Blocks.YELLOW_TERRACOTTA))markers.put("yellow",p.immutable());
                if(floor.is(Blocks.LIME_TERRACOTTA))markers.put("green",p.immutable());
            }
        }
        helper.assertTrue(markers.keySet().containsAll(java.util.Set.of("console","podium","master","red","yellow","green")),"generated facility contains all real controls");
        var residents=level.getEntitiesOfClass(Villager.class,new AABB(box.minX(),box.minY(),box.minZ(),box.maxX()+1,box.maxY()+1,box.maxZ()+1),v->
                "protocol_student".equals(v.getPersistentData().getString(cn.academy.port.story.UrbanStoryClues.WITNESS_KEY)));
        helper.assertTrue(!residents.isEmpty(),"generated facility has its rescue target");
        if(residents.isEmpty()||markers.size()<6)return;
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"[UrbanFacility]"));
        try {
            for(Objective objective:Objective.values()){
                if(objective.chapter()==Chapter.PROTOCOL)break;
                UrbanStoryStorage.recordVerified(player,objective,Approach.EQUIPMENT);
            }
            interactBlock(player,markers.get("console"));
            helper.assertTrue(UrbanStoryStorage.read(player).orElseThrow().completed(Objective.PROTOCOL_ACCESS),"facility console records access");
            var student=residents.getFirst();player.moveTo(student.getX(),student.getY(),student.getZ()+1,0,0);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.HONEY_BOTTLE));
            NeoForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(player,InteractionHand.MAIN_HAND,student));
            helper.assertTrue(UrbanStoryStorage.read(player).orElseThrow().completed(Objective.PROTOCOL_RESCUE),"actual facility student rescued");
            interactBlock(player,markers.get("master"));
            helper.assertTrue(UrbanStoryStorage.read(player).orElseThrow().chapter()==Chapter.CALIBRATOR,"master switch opens final chapter");
            interactBlock(player,markers.get("red"));interactBlock(player,markers.get("yellow"));interactBlock(player,markers.get("green"));
            var state=UrbanStoryStorage.read(player).orElseThrow();
            helper.assertTrue(state.completed(Objective.CALIBRATOR_NODE_A)&&state.completed(Objective.CALIBRATOR_NODE_B)
                    &&state.completed(Objective.CALIBRATOR_NODE_C),"all three world switches recorded");
            // The actual NPC pathfinding escort requires a connected client and is tested separately.
            UrbanStoryStorage.recordVerified(player,Objective.CALIBRATOR_EVACUATION,Approach.EQUIPMENT);
            var podium=markers.get("podium");player.moveTo(podium.getX()+3.5,podium.getY(),podium.getZ()+.5,0,0);
            var event=new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,podium,
                    new BlockHitResult(Vec3.atCenterOf(podium),Direction.UP,podium,false));
            NeoForge.EVENT_BUS.post(event);
            var bosses=level.getEntitiesOfClass(cn.academy.port.story.UrbanCalibrator.Boss.class,new AABB(podium).inflate(12));
            helper.assertTrue(event.isCanceled()&&!bosses.isEmpty(),"world podium summons the enrolled calibrator");
            bosses.forEach(entity->entity.discard());helper.succeed();
        } finally {player.discard();residents.forEach(Villager::discard);}
    }
    private static void interactBlock(FakePlayer player,BlockPos pos){
        player.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+1.5,0,0);
        NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,
                new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false)));
    }
}
