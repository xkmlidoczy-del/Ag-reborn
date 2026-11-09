package cn.academy.port.fleet;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public final class InteractionLab {
    public static final String ID = "academy";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ID);
    public static final DeferredHolder<net.minecraft.sounds.SoundEvent,net.minecraft.sounds.SoundEvent> C03_FIRE=SOUNDS.register("c03_fire",()->net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ID,"c03_fire")));
    public static final DeferredHolder<net.minecraft.sounds.SoundEvent,net.minecraft.sounds.SoundEvent> C03_MAG_OUT=SOUNDS.register("c03_mag_out",()->net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ID,"c03_mag_out")));
    public static final DeferredHolder<net.minecraft.sounds.SoundEvent,net.minecraft.sounds.SoundEvent> C03_MAG_IN=SOUNDS.register("c03_mag_in",()->net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ID,"c03_mag_in")));
    public static final DeferredHolder<net.minecraft.sounds.SoundEvent,net.minecraft.sounds.SoundEvent> C03_ACTIVATE=SOUNDS.register("c03_activate",()->net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ID,"c03_activate")));
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, ID);
    public static final DeferredItem<Item> LIGHT_CELL = ITEMS.registerSimpleItem("light_cell");
    public static final DeferredItem<CoilMagazineItem> COIL_MAGAZINE = ITEMS.register("g03_energy_magazine", ()->new CoilMagazineItem());
    @SuppressWarnings("unchecked") public static final DeferredItem<CoilMagazineItem>[] MAGAZINES=new DeferredItem[6];
    public static final DeferredItem<Item> HEAVY_CELL = ITEMS.registerSimpleItem("heavy_cell");
    public static final String[] GUN_IDS = {"g01_pulse_sidearm", "g02_compact_pdw", "g03_coil_rifle", "g04_precision_rifle", "g05_scatter_emitter", "g06_heavy_emitter"};
    public static final String[] CAR_IDS = {"v01_cyan_sedan", "v02_city_shuttle", "v03_campus_scooter", "v04_patrol_suv", "v05_rescue_van", "v06_utility_truck"};
    public static final int[] CAPS = {18, 30, 24, 10, 8, 12};
    public static final int[] RELOAD = {27, 34, 40, 46, 45, 56};
    public static final int[] COOLDOWN = {5, 3, 4, 12, 11, 7};
    public static final float[] DAMAGE = {4, 3, 5, 9, 7, 8};
    @SuppressWarnings("unchecked") public static final DeferredItem<GunItem>[] GUNS = new DeferredItem[GUN_IDS.length];
    public static final DeferredHolder<EntityType<?>, EntityType<LabVehicle>> CAR = TYPES.register("vehicle", () ->
        EntityType.Builder.<LabVehicle>of(LabVehicle::new, MobCategory.MISC).sized(2.2f, 1.8f).clientTrackingRange(16).updateInterval(1).build("academy:vehicle"));
    @SuppressWarnings("unchecked") public static final DeferredItem<VehicleDeployItem>[] VEHICLES=new DeferredItem[6];
    static { for (int i=0; i<GUN_IDS.length; i++) { final int variant=i; VEHICLES[i]=ITEMS.register(CAR_IDS[i]+"_crate",()->new VehicleDeployItem(variant));GUNS[i]=ITEMS.register(GUN_IDS[i], ()->new GunItem(variant));MAGAZINES[i]=i==2?COIL_MAGAZINE:ITEMS.register("g0"+(i+1)+"_energy_magazine",()->new CoilMagazineItem(variant)); } }
    public static net.minecraft.world.item.Item magazine(int variant){return MAGAZINES[Math.max(0,Math.min(5,variant))].get();}
    private static void creative(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent e){
        if(e.getTabKey()==net.minecraft.world.item.CreativeModeTabs.COMBAT){for(var g:GUNS)e.accept(g);for(int i=0;i<6;i++)e.accept(CoilMagazineItem.create(i,0));e.accept(LIGHT_CELL);e.accept(HEAVY_CELL);}
        if(e.getTabKey()==net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES)for(var v:VEHICLES)e.accept(v);
    }
    public static void register(IEventBus bus) {
        AudioDefs.bootstrap();
        ITEMS.register(bus); TYPES.register(bus); SOUNDS.register(bus); bus.addListener(LabNetwork::register);bus.addListener(InteractionLab::creative);
        NeoForge.EVENT_BUS.register(LabGameplay.class);
        if(Boolean.getBoolean("academy.qa.fleetStandalone"))NeoForge.EVENT_BUS.register(CoilQa.class);
        if(Boolean.getBoolean("academy.qa.fleetStandalone"))NeoForge.EVENT_BUS.register(FleetQa.class);
        if(Boolean.getBoolean("academy.qa.fleetStandalone"))NeoForge.EVENT_BUS.register(FleetPerfWorld.class);
        LOGGER.info("ACA fleet: integrated six guns, six vehicles and dedicated audio");
    }
}
