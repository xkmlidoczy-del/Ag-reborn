package cn.acamech;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(MechMod.ID)
public final class MechMod {
    public static final String ID = "acamech";
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    private static final Map<Chassis, Supplier<EntityType<MechEntity>>> MECHS = new EnumMap<>(Chassis.class);
    private static final Map<Chassis, Supplier<MechCrate>> CRATES = new EnumMap<>(Chassis.class);
    public static final Supplier<Item> CELL = ITEMS.registerSimpleItem("flux_cell");
    public static final Supplier<Item> WRENCH = ITEMS.registerSimpleItem("service_wrench", new Item.Properties().stacksTo(1));
    public static final Supplier<EntityType<PulseBolt>> PULSE_BOLT = ENTITIES.register("pulse_bolt", () ->
            EntityType.Builder.<PulseBolt>of(PulseBolt::new, MobCategory.MISC).sized(.40f, .40f)
                    .noSave().noSummon().clientTrackingRange(9).updateInterval(1).build(ID + ":pulse_bolt"));
    static final StreamCodec<ByteBuf, Vec3> POSITION = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, Vec3::x, ByteBufCodecs.DOUBLE, Vec3::y, ByteBufCodecs.DOUBLE, Vec3::z, Vec3::new);

    private static final DeferredRegister<EntityDataSerializer<?>> DATA =
            DeferredRegister.create(NeoForgeRegistries.ENTITY_DATA_SERIALIZERS, ID);
    static final Supplier<EntityDataSerializer<java.util.Optional<Footwork.Frame>>> FEET =
            DATA.register("feet", () -> EntityDataSerializer.forValueType(Footwork.Frame.CODEC));

    static {
        for (Chassis model : Chassis.values()) {
            MECHS.put(model, ENTITIES.register(model.id, () -> EntityType.Builder
                    .<MechEntity>of((type, world) -> new MechEntity(type, world, model), MobCategory.MISC)
                    .sized(model.width, model.height).clientTrackingRange(12).updateInterval(1)
                    .build(ID + ":" + model.id)));
            CRATES.put(model, ITEMS.register(model.id + "_crate", () -> new MechCrate(model)));
        }
        TABS.register("hangar", () -> CreativeModeTab.builder()
                .title(net.minecraft.network.chat.Component.translatable("itemGroup.acamech.hangar"))
                .icon(() -> crate(Chassis.ATLAS).getDefaultInstance())
                .displayItems((params, out) -> {
                    for (Chassis model : Chassis.values()) out.accept(crate(model));
                    out.accept(CELL.get());
                    out.accept(WRENCH.get());
                }).build());
    }

    public MechMod(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
        DATA.register(modBus);
        modBus.addListener(MechMod::registerPackets);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }

    public static EntityType<MechEntity> entity(Chassis model) { return MECHS.get(model).get(); }
    public static MechCrate crate(Chassis model) { return CRATES.get(model).get(); }

    private static void registerPackets(RegisterPayloadHandlersEvent event) {
        event.registrar("5").playToServer(Controls.TYPE, Controls.CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player && player.getVehicle() instanceof MechEntity mech) {
                mech.acceptControls(player, msg.bits, msg.mount);
            }
        }).playToClient(StrikeImpact.TYPE, StrikeImpact.CODEC, (msg, ctx) -> {
            if (ctx.player().level().getEntity(msg.mount()) instanceof MechEntity mech) {
                mech.showStrikeImpact(msg.positions(), msg.yaw());
            }
        }).playToClient(RangedEffect.TYPE, RangedEffect.CODEC, (msg, ctx) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) cn.acamech.client.RangedEffects.receive(msg, ctx.player().level());
        });
    }

    record StrikeImpact(int mount, float yaw, List<Vec3> positions) implements CustomPacketPayload {
        private static final Type<StrikeImpact> TYPE = new Type<>(id("strike_impact"));
        private static final StreamCodec<RegistryFriendlyByteBuf, StrikeImpact> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, StrikeImpact::mount,
                ByteBufCodecs.FLOAT, StrikeImpact::yaw,
                POSITION.apply(ByteBufCodecs.list(MechAttack.MAX_VISIBLE_HITS)), StrikeImpact::positions,
                StrikeImpact::new);

        @Override public Type<StrikeImpact> type() { return TYPE; }
    }

    public record RangedRay(Vec3 start, Vec3 end, int contact) {
        private static final StreamCodec<ByteBuf, RangedRay> CODEC = StreamCodec.composite(
                POSITION, RangedRay::start, POSITION, RangedRay::end, ByteBufCodecs.VAR_INT, RangedRay::contact, RangedRay::new);
    }

    public record RangedEffect(RangedWeapon weapon, List<RangedRay> rays) implements CustomPacketPayload {
        private static final Type<RangedEffect> TYPE = new Type<>(id("ranged_effect"));
        private static final StreamCodec<RegistryFriendlyByteBuf, RangedEffect> CODEC = StreamCodec.composite(
                ByteBufCodecs.idMapper(i -> RangedWeapon.values()[i], RangedWeapon::ordinal), RangedEffect::weapon,
                RangedRay.CODEC.apply(ByteBufCodecs.list(2)), RangedEffect::rays, RangedEffect::new);
        @Override public Type<RangedEffect> type() { return TYPE; }
    }

    public record Controls(byte bits, int mount) implements CustomPacketPayload {
        public static final int FORWARD = 1, BACK = 2, LEFT = 4, RIGHT = 8, AUX = 16, STRIKE = 32, FIRE = 64, RUN = 128;
        public static final Type<Controls> TYPE = new Type<>(id("controls"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Controls> CODEC =
                StreamCodec.composite(ByteBufCodecs.BYTE, Controls::bits, ByteBufCodecs.VAR_INT, Controls::mount, Controls::new);

        @Override
        public Type<Controls> type() { return TYPE; }
    }
}
