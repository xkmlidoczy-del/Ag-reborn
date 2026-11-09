package cn.academy.port.canon;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Actual finite medium and a transient, collision-aware human-shaped liquid body. */
public final class CanonLiquidEntities {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("academy");
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE,"academy");
    public static final DeferredItem<Item> DENSE_MEDIUM = ITEMS.registerSimpleItem("dense_shadow_medium",new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> OBSERVATION_MODULE = ITEMS.registerSimpleItem("shadow_observer_module",new Item.Properties().stacksTo(16));
    public static final DeferredHolder<EntityType<?>,EntityType<Shadow>> SHADOW = ENTITIES.register("liquid_shadow_body",
            () -> EntityType.Builder.<Shadow>of(Shadow::new,MobCategory.MISC).sized(.6f,1.975f)
                    .noSave().clientTrackingRange(10).updateInterval(1).build("academy:liquid_shadow_body"));
    private CanonLiquidEntities() {}
    public static void register(IEventBus bus) {
        ITEMS.register(bus); ENTITIES.register(bus); bus.addListener(CanonLiquidEntities::attributes); bus.addListener(CanonLiquidEntities::creative);
    }
    private static void attributes(EntityAttributeCreationEvent event) { event.put(SHADOW.get(),ArmorStand.createAttributes().build()); }
    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if(event.getTabKey()==CreativeModeTabs.INGREDIENTS){event.accept(DENSE_MEDIUM);event.accept(OBSERVATION_MODULE);}
    }

    /** No equipment, pickup, drops, saved ghost or player inventory interaction. */
    public static final class Shadow extends ArmorStand {
        private static final EntityDataAccessor<Optional<UUID>> CONTROL_OWNER =
                SynchedEntityData.defineId(Shadow.class,EntityDataSerializers.OPTIONAL_UUID);
        private static final EntityDataAccessor<Boolean> OBSERVATION_MODULE =
                SynchedEntityData.defineId(Shadow.class,EntityDataSerializers.BOOLEAN);
        @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
            super.defineSynchedData(builder); builder.define(CONTROL_OWNER,Optional.empty());
            builder.define(OBSERVATION_MODULE,false);
        }
        public boolean hasObservationModule() { return getEntityData().get(OBSERVATION_MODULE); }
        void observationModule(boolean installed) {
            if(level().isClientSide) throw new IllegalStateException("server-owned observation module");
            getEntityData().set(OBSERVATION_MODULE,installed);
        }
        public Optional<UUID> controlOwner() { return getEntityData().get(CONTROL_OWNER); }
        void controlOwner(UUID owner) {
            if(level().isClientSide) throw new IllegalStateException("server-owned shadow lease");
            getEntityData().set(CONTROL_OWNER,Optional.ofNullable(owner));
        }
        public Shadow(EntityType<? extends ArmorStand> type,Level level) {
            super(type,level); setNoGravity(true); setNoBasePlate(true); setShowArms(true); setSilent(true);
        }
        @Override public InteractionResult interactAt(Player p,Vec3 hit,InteractionHand hand) { return InteractionResult.FAIL; }
        @Override public ItemStack getPickResult() { return ItemStack.EMPTY; }
        /** Builder.noSave() also makes save() refuse an encode id, but vanilla's
         * shouldBeSaved() does not consult the entity type's serialization flag. */
        @Override public boolean shouldBeSaved() { return false; }
        /** Arrows pass through the liquid body: the first stray arrow used to dispel it before Intercept/Screen could ever act on one. */
        @Override public boolean canBeHitByProjectile() { return false; }
        @Override public boolean hurt(DamageSource source,float amount) {
            if (level().isClientSide || !Float.isFinite(amount) || amount <= 0 || isRemoved()) return false;
            discard(); return true;
        }
        @Override public void kill() { discard(); }
    }
}
