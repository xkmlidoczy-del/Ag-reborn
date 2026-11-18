package cn.aca.omnitrix;

import cn.aca.omnitrix.world.Expedition;
import cn.aca.omnitrix.world.SealStation;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(Omnitrix.ID)
public final class Omnitrix {
    public static final String ID = "aca_omnitrix";
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, ID);
    private static final Map<Kind, Supplier<WatchItem>> WATCHES = new EnumMap<>(Kind.class);

    public static final Supplier<AscalonItem> ASCALON = ITEMS.register("ascalon", AscalonItem::new);
    public static final Supplier<Item> STAR_CORE = ITEMS.registerSimpleItem("star_core", new Item.Properties().stacksTo(16));
    public static final Supplier<Item> CARNA_CORE = ITEMS.registerSimpleItem("carni_core", new Item.Properties().stacksTo(16));
    public static final Supplier<Block> STAR_STATION = BLOCKS.register("star_station",
            () -> new SealStation(BlockBehaviour.Properties.of().strength(4, 80).lightLevel(s -> 6), Kind.CELESTIAL));
    public static final Supplier<Block> QUARANTINE = BLOCKS.register("quarantine_station",
            () -> new SealStation(BlockBehaviour.Properties.of().strength(4, 80).lightLevel(s -> 3), Kind.CARNITRIX));
    public static final Supplier<BlockItem> STAR_STATION_ITEM = ITEMS.register("star_station",
            () -> new BlockItem(STAR_STATION.get(), new Item.Properties()));
    public static final Supplier<BlockItem> QUARANTINE_ITEM = ITEMS.register("quarantine_station",
            () -> new BlockItem(QUARANTINE.get(), new Item.Properties()));
    public static final Supplier<EntityType<PursuitBody>> PURSUER = ENTITIES.register("pursuit",
            () -> EntityType.Builder.<PursuitBody>of(PursuitBody::new, MobCategory.MISC).sized(.6f, 1.8f)
                    .noSummon().noSave().clientTrackingRange(8).updateInterval(1).build(ID + ":pursuit"));
    public static final Supplier<EntityType<Pulse>> PULSE = ENTITIES.register("pulse",
            () -> EntityType.Builder.<Pulse>of(Pulse::new, MobCategory.MISC).sized(.21f, .21f)
                    .noSummon().noSave().clientTrackingRange(6).updateInterval(1).build(ID + ":pulse"));
    public static final Supplier<EntityType<AscalonSlash>> ASCALON_SLASH = ENTITIES.register("ascalon_slash",
            () -> EntityType.Builder.<AscalonSlash>of(AscalonSlash::new, MobCategory.MISC).sized(3.6f, 1.6f)
                    .noSummon().noSave().clientTrackingRange(10).updateInterval(1).build(ID + ":ascalon_slash"));
    public static final Supplier<Block> CRYSTAL = BLOCKS.register("temporary_crystal", () ->
            new Block(BlockBehaviour.Properties.of().strength(.8f, 2).noLootTable().lightLevel(s -> 5)) {
                @Override protected void tick(net.minecraft.world.level.block.state.BlockState state,
                        net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos,
                        net.minecraft.util.RandomSource random) {
                    level.removeBlock(pos, false);
                }
            });
    public static final Supplier<Block> BONE_SPIKE = BLOCKS.register("bone_spike", () -> new BoneSpike());
    public static final Supplier<AttachmentType<Wrist>> WRIST = ATTACHMENTS.register("wrist",
            () -> AttachmentType.serializable(Wrist::new).build());
    public static final Supplier<AttachmentType<AscalonState>> ASCALON_STATE = ATTACHMENTS.register("ascalon_state",
            () -> AttachmentType.serializable(AscalonState::new).build());
    public static final Supplier<SoundEvent> LATCH = sound("latch");
    public static final Supplier<SoundEvent> SELECT = sound("select");
    public static final Supplier<SoundEvent> CLASSIC_SHIFT = sound("classic_shift");
    public static final Supplier<SoundEvent> X_SHIFT = sound("x_shift");
    public static final Supplier<SoundEvent> CARNI_SHIFT = sound("carni_shift");
    public static final Supplier<SoundEvent> RESTORE = sound("restore");
    public static final Supplier<SoundEvent> POWER = sound("power");
    public static final Supplier<SoundEvent> TIMEOUT = sound("timeout");
    public static final Supplier<SoundEvent> MISFIRE = sound("misfire");
    public static final Supplier<SoundEvent> FAILSAFE = sound("failsafe");
    public static final Supplier<SoundEvent> HEARTBEAT = sound("heartbeat");
    public static final Supplier<SoundEvent> EXHAUSTED = sound("exhausted");
    public static final Supplier<SoundEvent> ALIEN_X_STASIS = sound("alien_x_stasis");
    public static final Supplier<SoundEvent> ALIEN_X_STARBURST = sound("alien_x_starburst");
    public static final Supplier<SoundEvent> ALIEN_X_SINGULARITY = sound("alien_x_singularity");
    public static final Supplier<SoundEvent> ALIEN_X_EXTINCTION = sound("alien_x_extinction");
    public static final Supplier<SoundEvent> ALIEN_X_FLICK = sound("alien_x_flick");
    public static final Supplier<SoundEvent> ALIEN_X_AEGIS = sound("alien_x_aegis");
    public static final Supplier<SoundEvent> ALIEN_X_REWRITE = sound("alien_x_rewrite");
    public static final Supplier<SoundEvent> FOUR_ARMS_PUNCH = sound("four_arms_punch");
    public static final Supplier<SoundEvent> FOUR_ARMS_SLAM = sound("four_arms_slam");
    public static final Supplier<SoundEvent> FIRE_LAUNCH = sound("fire_launch");
    public static final Supplier<SoundEvent> FIRE_CONE = sound("fire_cone");
    public static final Supplier<SoundEvent> WILDMUTT_SCENT = sound("wildmutt_scent");
    public static final Supplier<SoundEvent> WILDMUTT_POUNCE = sound("wildmutt_pounce");
    public static final Supplier<SoundEvent> CRYSTAL_SURGE = sound("crystal_surge");
    public static final Supplier<SoundEvent> CRYSTAL_VOLLEY = sound("crystal_volley");
    public static final Supplier<SoundEvent> XLR8_DASH = sound("xlr8_dash");
    public static final Supplier<SoundEvent> XLR8_BRAKE = sound("xlr8_brake");
    public static final Supplier<SoundEvent> GREY_MATTER_GRIP = sound("grey_matter_grip");
    public static final Supplier<SoundEvent> GREY_MATTER_ACTION = sound("grey_matter_action");
    public static final Supplier<SoundEvent> SLIME_SPLAT = sound("slime_splat");
    public static final Supplier<SoundEvent> STINKFLY_WING = sound("stinkfly_wing");
    public static final Supplier<SoundEvent> RIPJAWS_SNAP = sound("ripjaws_snap");
    public static final Supplier<SoundEvent> RIPJAWS_SURGE = sound("ripjaws_surge");
    public static final Supplier<SoundEvent> UPGRADE_LINK = sound("upgrade_link");
    public static final Supplier<SoundEvent> UPGRADE_BEAM = sound("upgrade_beam");
    public static final Supplier<SoundEvent> GHOST_CLOAK = sound("ghost_cloak");
    public static final Supplier<SoundEvent> GHOST_PHASE = sound("ghost_phase");
    public static final Supplier<SoundEvent> X_FOUR_ARMS_PUNCH = sound("x_four_arms_punch");
    public static final Supplier<SoundEvent> X_FOUR_ARMS_SLAM = sound("x_four_arms_slam");
    public static final Supplier<SoundEvent> X_FIRE_LAUNCH = sound("x_fire_launch");
    public static final Supplier<SoundEvent> X_FIRE_CONE = sound("x_fire_cone");
    public static final Supplier<SoundEvent> X_WILDMUTT_SCENT = sound("x_wildmutt_scent");
    public static final Supplier<SoundEvent> X_WILDMUTT_POUNCE = sound("x_wildmutt_pounce");
    public static final Supplier<SoundEvent> X_CRYSTAL_SURGE = sound("x_crystal_surge");
    public static final Supplier<SoundEvent> X_CRYSTAL_VOLLEY = sound("x_crystal_volley");
    public static final Supplier<SoundEvent> X_XLR8_DASH = sound("x_xlr8_dash");
    public static final Supplier<SoundEvent> X_XLR8_BRAKE = sound("x_xlr8_brake");
    public static final Supplier<SoundEvent> X_GREY_MATTER_GRIP = sound("x_grey_matter_grip");
    public static final Supplier<SoundEvent> X_GREY_MATTER_ACTION = sound("x_grey_matter_action");
    public static final Supplier<SoundEvent> X_SLIME_SPLAT = sound("x_slime_splat");
    public static final Supplier<SoundEvent> X_STINKFLY_WING = sound("x_stinkfly_wing");
    public static final Supplier<SoundEvent> X_RIPJAWS_SNAP = sound("x_ripjaws_snap");
    public static final Supplier<SoundEvent> X_RIPJAWS_SURGE = sound("x_ripjaws_surge");
    public static final Supplier<SoundEvent> X_UPGRADE_LINK = sound("x_upgrade_link");
    public static final Supplier<SoundEvent> X_UPGRADE_BEAM = sound("x_upgrade_beam");
    public static final Supplier<SoundEvent> X_GHOST_CLOAK = sound("x_ghost_cloak");
    public static final Supplier<SoundEvent> X_GHOST_PHASE = sound("x_ghost_phase");
    public static final Supplier<SoundEvent> CARNI_HEATBLAST = sound("carni_heatblast");
    public static final Supplier<SoundEvent> CARNI_WILDMUTT = sound("carni_wildmutt");
    public static final Supplier<SoundEvent> CARNI_DIAMONDHEAD = sound("carni_diamondhead");
    public static final Supplier<SoundEvent> CARNI_XLR8 = sound("carni_xlr8");
    public static final Supplier<SoundEvent> CARNI_GREY_MATTER = sound("carni_grey_matter");
    public static final Supplier<SoundEvent> CARNI_FOUR_ARMS = sound("carni_four_arms");
    public static final Supplier<SoundEvent> CARNI_STINKFLY = sound("carni_stinkfly");
    public static final Supplier<SoundEvent> CARNI_RIPJAWS = sound("carni_ripjaws");
    public static final Supplier<SoundEvent> CARNI_UPGRADE = sound("carni_upgrade");
    public static final Supplier<SoundEvent> CARNI_GHOSTFREAK = sound("carni_ghostfreak");
    public static final Supplier<SoundEvent> CARNI_ALIEN_X = sound("carni_alien_x");
    public static final Supplier<SoundEvent> ASCALON_ARMOR_ON = sound("ascalon_armor_on");
    public static final Supplier<SoundEvent> ASCALON_ARMOR_OFF = sound("ascalon_armor_off");
    public static final Supplier<SoundEvent> ASCALON_CLEAVE = sound("ascalon_cleave");
    public static final Supplier<SoundEvent> ASCALON_SLASH_BEAM = sound("ascalon_slash_beam");
    public static final Supplier<SoundEvent> ASCALON_PARRY = sound("ascalon_parry");
    public static final Supplier<SoundEvent> ASCALON_RIFT = sound("ascalon_rift");
    public static final Supplier<SoundEvent> ASCALON_TEMPEST = sound("ascalon_tempest");
    public static final Supplier<SoundEvent> ASCALON_SYZYGY = sound("ascalon_syzygy");
    public static final net.minecraft.core.Holder<MobEffect> NERVE_EXHAUSTION = EFFECTS.register("nerve_exhaustion",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x783947) {}.addAttributeModifier(
                    Attributes.MOVEMENT_SPEED, id("nerve_exhaustion"), -.45, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    public static final net.minecraft.core.Holder<MobEffect> BLEED = EFFECTS.register("bleed", () -> new MobEffect(MobEffectCategory.HARMFUL, 0x891e38) {
        @Override public boolean shouldApplyEffectTickThisTick(int remaining, int amplifier) { return remaining % 20 == 0; }
        @Override public boolean applyEffectTick(net.minecraft.world.entity.LivingEntity entity, int amplifier) {
            entity.hurt(entity.damageSources().magic(), 1f);
            return true;
        }
    });

    static {
        for (Kind kind : Kind.values()) WATCHES.put(kind, ITEMS.register(kind.id, () -> new WatchItem(kind)));
        TABS.register("watches", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.aca_omnitrix.watches"))
                .icon(() -> watch(Kind.CLASSIC).getDefaultInstance())
                .displayItems((params, output) -> {
                    for (Kind kind : Kind.values()) output.accept(watch(kind));
                    output.accept(ASCALON.get());
                    output.accept(STAR_CORE.get());
                    output.accept(CARNA_CORE.get());
                    output.accept(STAR_STATION_ITEM.get());
                    output.accept(QUARANTINE_ITEM.get());
                }).build());
    }

    public Omnitrix(IEventBus bus) {
        ITEMS.register(bus);
        BLOCKS.register(bus);
        TABS.register(bus);
        ENTITIES.register(bus);
        ATTACHMENTS.register(bus);
        SOUNDS.register(bus);
        EFFECTS.register(bus);
        cn.aca.omnitrix.world.ExpeditionStructure.register(bus);
        bus.addListener(Omnitrix::attributes);
        bus.addListener(WristNetwork::register);
        var game = NeoForge.EVENT_BUS;
        WristService.register(game);
        Abilities.register(game);
        game.addListener(WristNetwork::tickScenes);
        game.addListener(WristNetwork::stopScenes);
        AscalonService.register(game);
        SurvivalForms.register(game);
        PursuitBody.register(game);
        game.addListener(Expedition::onNoticeBoard);
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(PURSUER.get(), PursuitBody.attributes().build());
    }

    private static Supplier<SoundEvent> sound(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(id(path)));
    }

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }
    public static WatchItem watch(Kind kind) { return WATCHES.get(kind).get(); }
    public static Wrist wrist(Player player) { return player.getData(WRIST); }
    public static AscalonState ascalon(Player player) { return player.getData(ASCALON_STATE); }
}
