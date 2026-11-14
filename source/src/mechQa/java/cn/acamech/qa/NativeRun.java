package cn.acamech.qa;

import cn.acamech.Chassis;
import cn.acamech.MechEntity;
import cn.acamech.MechMod;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@Mod(value = "acamechqa", dist = Dist.CLIENT)
public final class NativeRun {
    private static final int FLOOR = -60;
    private static final Map<Chassis, MechEntity> mechs = new EnumMap<>(Chassis.class);
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static final InputConstants.Key AUX = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_X);
    private static final InputConstants.Key ATTACK = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_Z);
    private static final InputConstants.Key FIRE = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_G);
    private static boolean opening, done;
    private static int age;
    private static Vec3 before;
    private static float yaw;
    private static int charge, accumulated;
    private static double peak;
    private static ArmorStand cam;
    private static Husk enemy;
    private static Husk flank;
    private static final boolean SHOWCASE = System.getProperty("acamech.qa.ffmpeg") != null;

    public NativeRun() {
        if (Boolean.getBoolean("acamech.qa")) {
            NeoForge.EVENT_BUS.addListener(NativeRun::tick);
            NeoForge.EVENT_BUS.addListener((RenderHandEvent e) -> {
                if (Minecraft.getInstance().getCameraEntity() == cam && cam != null) e.setCanceled(true);
            });
            if (SHOWCASE) DriveShowcase.register();
        }
    }

    private static void tick(ClientTickEvent.Post event) {
        if (done) return;
        var mc = Minecraft.getInstance();
        try {
            if (mc.getSingleplayerServer() == null && mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen
                    && Boolean.getBoolean("acamech.qa.reopened")) {
                var skip = net.minecraft.network.chat.Component.translatable("selectWorld.backupJoinSkipButton").getString();
                for (var child : mc.screen.children()) if (child instanceof net.minecraft.client.gui.components.Button button
                        && button.getMessage().getString().equals(skip)) { button.onPress(); break; }
            }
            if (mc.getSingleplayerServer() == null && mc.screen instanceof TitleScreen && !opening
                    && !Boolean.getBoolean("acamech.qa.reopened")) {
                opening = true;
                var settings = new LevelSettings("ACA Mech proving ground", GameType.CREATIVE,
                        false, Difficulty.NORMAL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("mech-proving-ground", settings,
                        new WorldOptions(61432L, false, false), r -> r.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
                return;
            }
            if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
            age++;
            if (Boolean.getBoolean("acamech.qa.reopened")) {
                if (age == 60) {
                    server(mc, () -> { verifyReopened(mc); return null; });
                    report.put("completed", true);
                    finish(mc, null);
                }
                return;
            }
            if (age == 12) mc.player.displayClientMessage(net.minecraft.network.chat.Component.empty(), true);
            if (age == 5) server(mc, () -> {
                arena(mc);
                if (SHOWCASE) DriveShowcase.prepare(player(mc).serverLevel());
                return null;
            });
            if (SHOWCASE) {
                if (age >= 60 && DriveShowcase.tick(mc, age - 60)) {
                    report.putAll(DriveShowcase.finish());
                    report.put("completed", true);
                    finish(mc, null);
                }
                return;
            }
            if (age == 16) {
                mc.options.fov().set(57);
                mc.options.bobView().set(false);
                mc.options.hideGui = true;
                camera(mc, .5, FLOOR + 3.55, -14.55, 0f, 3.5f);
            }
            if (age == 32) shot(mc, "01-three-frames");
            if (age == 43) camera(mc, -11.65, FLOOR + 3.28, -7.9, -25f, 2f);
            if (age == 52) shot(mc, "02-atlas");
            if (age == 58) camera(mc, 2.7, FLOOR + 2.5, -7.1, 19f, 2f);
            if (age == 66) shot(mc, "03-aether");
            if (age == 72) camera(mc, 12.15, FLOOR + 3.25, -8.4, 23f, 3f);
            if (age == 80) shot(mc, "04-seraph");
            if (age == 86) {
                mc.options.hideGui = false;
                mc.setCameraEntity(mc.player);
            }
            if (age >= 90 && age < 480) {
                int index = (age - 90) / 130;
                drive(mc, Chassis.values()[index], (age - 90) % 130);
            }
            if (age == 481) server(mc, () -> { service(mc); return null; });
            if (age == 492) {
                mc.options.hideGui = true;
                camera(mc, -14.2, FLOOR + 2.95, 8.8, -139f, 2.2f);
            }
            if (age == 501) shot(mc, "07-atlas-hydraulics");
            if (age == 504) camera(mc, 11.2, FLOOR + 3.6, 7.5, 152f, 2.7f);
            if (age == 514) shot(mc, "08-seraph-wing-back");
            if (age == 518) {
                server(mc, () -> { levelLighting(mc, 14500); return null; });
                camera(mc, -8.5, FLOOR + 3.25, -5.4, 0f, 0f);
            }
            if (age == 537) shot(mc, "09-reactor-at-dusk");
            if (age == 545) server(mc, () -> { darkroom(mc, 0, false); return null; });
            if (age == 556) shot(mc, "10-reactor-unpowered");
            if (age == 562) server(mc, () -> { darkroom(mc, 0, true); return null; });
            if (age == 576) shot(mc, "11-wall-unpowered");
            if (age == 582) server(mc, () -> { darkroom(mc, Chassis.ATLAS.energy, true); return null; });
            if (age == 596) shot(mc, "12-wall-powered");
            if (age == 602) {
                server(mc, () -> {
                    darkroom(mc, Chassis.ATLAS.energy, false);
                    levelLighting(mc, 3000);
                    mc.getSingleplayerServer().saveEverything(false, true, true);
                    return null;
                });
                report.put("controlled_halo_scene_captured", true);
                report.put("completed", true);
                report.put("loopback_local_world", true);
                finish(mc, null);
            }
        } catch (Throwable error) {
            finish(mc, error);
        }
    }

    private static void arena(Minecraft mc) {
        var player = player(mc);
        var level = player.serverLevel();
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
        level.setDayTime(3000);
        player.connection.teleport(-9.5, FLOOR, -9.5, 180f, 0f);
        for (int x = -20; x <= 20; x++) {
            for (int z = -16; z <= 16; z++) {
                Block deck = z >= -5 ? Blocks.SMOOTH_STONE : Blocks.GRAY_CONCRETE;
                if (z == -5 || z == 16) deck = Blocks.POLISHED_ANDESITE;
                if (z == -11 && Math.floorMod(x, 4) < 2) deck = Blocks.WHITE_CONCRETE;
                if (z >= -4 && z <= 5 && (x == -12 || x == -4 || x == 4 || x == 12)) deck = Blocks.YELLOW_CONCRETE;
                level.setBlockAndUpdate(new BlockPos(x, FLOOR - 1, z), deck.defaultBlockState());
            }
        }
        for (int x = -19; x <= 19; x++) {
            level.setBlockAndUpdate(new BlockPos(x, FLOOR, 8), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(x, FLOOR + 1, 8),
                    (Math.floorMod(x, 4) == 0 ? Blocks.SEA_LANTERN : Blocks.GRAY_CONCRETE).defaultBlockState());
            if (Math.abs(x) > 13) {
                for (int y = 2; y < 5; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, FLOOR + y, 8),
                            (y == 3 ? Blocks.CYAN_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE).defaultBlockState());
                }
            }
        }
        for (Chassis model : Chassis.values()) {
            BlockPos pad = new BlockPos((model.ordinal() - 1) * 8, FLOOR - 1, 0);
            var stack = new ItemStack(MechMod.crate(model));
            player.setYRot(180f);
            var placed = stack.getItem().useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, stack, top(pad)));
            require(placed.consumesAction(), "deployment " + model);
            var found = level.getEntitiesOfClass(MechEntity.class, new net.minecraft.world.phys.AABB(pad).inflate(1));
            require(found.size() == 1 && found.getFirst().chassis() == model, "registry spawn " + model);
            mechs.put(model, found.getFirst());
            var blocked = stack.getItem().useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, stack, top(pad)));
            require(!blocked.consumesAction(), "occupied deployment blocked " + model);
        }
        var recipes = level.getRecipeManager();
        boolean academy = ModList.get().isLoaded("academy");
        for (Chassis model : Chassis.values()) {
            require(recipes.byKey(MechMod.id(model.id + "_crate")).isPresent(), "vanilla recipe " + model);
            var compat = recipes.byKey(MechMod.id("academy_" + model.id + "_crate"));
            require(compat.isPresent() == academy, "conditional ACA recipe " + model);
            if (academy) {
                var assembled = compat.orElseThrow().value().getResultItem(level.registryAccess());
                require(assembled.is(MechMod.crate(model)), "ACA recipe gives correct unit " + model);
            }
        }
        report.put("academy_loaded", academy);
        report.put("three_crate_deployments", true);
        report.put("blocked_deployment", true);
        report.put("three_normal_recipes", true);
        report.put("three_conditional_academy_recipes", academy);
    }

    private static void drive(Minecraft mc, Chassis frame, int time) throws Exception {
        MechEntity mech = mechs.get(frame);
        if (time == 0) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(mc, () -> {
                var pilot = player(mc);
                pilot.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                pilot.connection.teleport(mech.getX(), FLOOR, mech.getZ() - frame.width * .5 - 1.12, 180, 0);
                return null;
            });
        }
        if (time == 8) {
            var clientMech = (MechEntity) mc.level.getEntity(mech.getId());
            require(clientMech != null, "entity tracked by client " + frame);
            mc.gameMode.interact(mc.player, clientMech, InteractionHand.MAIN_HAND);
        }
        if (time == 15) {
            require(mc.player.getVehicle() instanceof MechEntity s && s.chassis() == frame, "client mounted " + frame);
            server(mc, () -> {
                require(mech.getFirstPassenger() == player(mc), "server mounted " + frame);
                require(Math.abs(player(mc).getY() - mech.getY() - frame.seat) < .12, "seat attachment " + frame);
                before = mech.position();
                yaw = mech.getYRot();
                charge = mech.charge();
                if (frame == Chassis.ATLAS) {
                    var level = player(mc).serverLevel();
                    enemy = EntityType.HUSK.create(level);
                    enemy.moveTo(mech.getX(), FLOOR, mech.getZ() - 3.25, 0, 0);
                    enemy.setNoAi(true);
                    level.addFreshEntity(enemy);
                    // Within range, beyond the heavy punch's cone.
                    var side = EntityType.HUSK.create(level);
                    side.moveTo(mech.getX() + 2.92, FLOOR, mech.getZ() - 2.52, 0, 0);
                    side.setNoAi(true);
                    level.addFreshEntity(side);
                    flank = side;
                }
                return null;
            });
        }
        // The fleet gun and mech both default to G. A held, loaded gun must
        // not fire when the pilot operates the mech's ranged weapon.
        if (frame == Chassis.AETHER && time == 16 && ModList.get().isLoaded("academy")) server(mc, () -> {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("academy", "g01_pulse_sidearm"));
            require(item != Items.AIR, "fleet sidearm exists");
            var stack = new ItemStack(item);
            net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    stack, tag -> tag.putInt("aclab_ammo", 12));
            player(mc).setItemInHand(InteractionHand.MAIN_HAND, stack);
            return null;
        });
        if (frame == Chassis.AETHER && time == 20 && ModList.get().isLoaded("academy")) {
            KeyMapping.set(FIRE, true);
            KeyMapping.click(FIRE);
        }
        if (frame == Chassis.AETHER && time == 26 && ModList.get().isLoaded("academy")) {
            KeyMapping.set(FIRE, false);
            server(mc, () -> {
                var held = player(mc).getMainHandItem();
                int ammo = held.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getInt("aclab_ammo");
                require(ammo == 12, "held fleet gun did not fire while piloting; ammo=" + ammo);
                require(mech.rangedAge() >= 0, "mech accepted G while holding the gun");
                report.put("shared_g_mech_fires_gun_ammo_unchanged", true);
                player(mc).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                return null;
            });
        }
        if (time == 20 && frame == Chassis.ATLAS) {
            shot(mc, "05-atlas-cockpit");
            KeyMapping.click(ATTACK);
        }
        if (time == 25 && frame == Chassis.ATLAS) server(mc, () -> {
            require(mech.strikeAge() >= 0 && mech.strikeResult() == -1 && enemy.getHealth() == enemy.getMaxHealth(),
                    "ATLAS winds up before damage");
            return null;
        });
        if (time == 33 && frame == Chassis.ATLAS) server(mc, () -> {
            require(enemy.getHealth() < enemy.getMaxHealth() && mech.charge() < charge, "native strike damage and power");
            require(mech.strikeResult() == 1, "server confirmed exactly one target");
            require(flank.getHealth() == flank.getMaxHealth(), "ATLAS narrow punch missed off-axis target");
            report.put("atlas_strike_damage", enemy.getMaxHealth() - enemy.getHealth());
            report.put("atlas_strike_delayed_to_contact", true);
            report.put("atlas_off_axis_target_unharmed", true);
            enemy.discard();
            flank.discard();
            return null;
        });
        int driveTick = time - (frame == Chassis.ATLAS ? 14 : 0);
        if (driveTick == 30) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            mc.player.setYRot(180);
            mc.player.setXRot(10);
            mc.options.keyUp.setDown(true);
        }
        if (driveTick == 45) {
            mc.options.keyUp.setDown(false);
            server(mc, () -> {
                require(mech.getZ() < before.z - 1.6 && mech.charge() < charge, "native W key drives " + frame);
                report.put(frame.id + "_forward_blocks", Math.round((before.z - mech.getZ()) * 100) / 100.0);
                accumulated = Math.round(Math.abs(mech.driveSpeed()) * 1000);
                return null;
            });
        }
        if (driveTick == 48) mc.options.keyRight.setDown(true);
        if (driveTick == 57) {
            mc.options.keyRight.setDown(false);
            server(mc, () -> {
                require(Math.abs(net.minecraft.util.Mth.wrapDegrees(mech.getYRot() - yaw)) > 13, "native D key steers " + frame);
                before = mech.position();
                return null;
            });
            mc.options.keyDown.setDown(true);
        }
        if (driveTick == 69) {
            mc.options.keyDown.setDown(false);
            server(mc, () -> {
                require(mech.getZ() > before.z + .22, "native S key reverses " + frame);
                before = mech.position();
                charge = mech.charge();
                return null;
            });
        }
        if (driveTick == 74) {
            KeyMapping.set(AUX, true);
            if (frame == Chassis.AETHER) mc.options.keyUp.setDown(true);
            if (frame == Chassis.SERAPH) peak = FLOOR;
        }
        if (driveTick == 84) {
            server(mc, () -> {
                require(mech.auxiliary(), "native auxiliary key " + frame);
                require(mech.charge() < charge, "aux spends power " + frame);
                switch (frame) {
                    case ATLAS -> {
                        int pre = mech.hull();
                        mech.hurt(mech.damageSources().generic(), 20f);
                        require(pre - mech.hull() == 9, "ATLAS brace damage reduction");
                        report.put("atlas_braced_damage_from_20", 9);
                    }
                    case AETHER -> {
                        require(mech.driveSpeed() * 1000 > accumulated * 1.35f, "AETHER sprints faster");
                        report.put("aether_sprint_blocks_per_tick", Math.round(mech.driveSpeed() * 1000) / 1000.0);
                    }
                    case SERAPH -> {
                        require(mech.getY() > FLOOR + 1.1, "SERAPH lifts under power");
                        peak = mech.getY();
                    }
                }
                return null;
            });
            shot(mc, "06-" + frame.id + "-aux");
        }
        if (driveTick == 106) {
            server(mc, () -> {
                if (frame == Chassis.SERAPH) {
                    require(!mech.auxiliary(), "SERAPH boost expires even with key held");
                    report.put("seraph_sampled_rise_blocks", Math.round((Math.max(peak, mech.getY()) - FLOOR) * 100) / 100.0);
                }
                return null;
            });
            KeyMapping.set(AUX, false);
            mc.options.keyUp.setDown(false);
            mc.options.keyShift.setDown(true);
        }
        if (driveTick == 115) mc.options.keyShift.setDown(false);
        if (driveTick == (frame == Chassis.ATLAS ? 115 : 123)) {
            server(mc, () -> {
                var rider = player(mc);
                require(!rider.isPassenger() && !mech.isVehicle(), "native Shift dismount " + frame);
                require(rider.getY() < FLOOR + .2 && rider.onGround() && !mech.getBoundingBox().intersects(rider.getBoundingBox()),
                        "rider placed on free ground " + frame + " at " + rider.position());
                return null;
            });
            require(mc.player.getVehicle() == null, "driver dismount synced " + frame);
            report.put(frame.id + "_safe_dismount", true);
        }
    }

    private static void service(Minecraft mc) {
        var p = player(mc);
        var level = p.serverLevel();
        MechEntity atlas = mechs.get(Chassis.ATLAS);
        p.connection.teleport(-16, FLOOR, 4, 180, 0);
        boolean academy = ModList.get().isLoaded("academy");
        if (academy) {
            var cell = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("academy", "heavy_cell"));
            require(cell != Items.AIR, "ACA heavy cell registered");
            p.setItemInHand(InteractionHand.MAIN_HAND, cell.getDefaultInstance());
            int start = atlas.charge();
            atlas.interact(p, InteractionHand.MAIN_HAND);
            require(start < atlas.charge(), "ACA cell charges frame");
            report.put("academy_cell_charges", true);
        }
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MechMod.CELL.get()));
        int startCharge = atlas.charge();
        atlas.interact(p, InteractionHand.MAIN_HAND);
        require(atlas.charge() == Math.min(atlas.chassis().energy, startCharge + 360), "flux cell charge");
        int pre = atlas.hull();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        atlas.interact(p, InteractionHand.MAIN_HAND);
        require(atlas.hull() > pre, "iron repair");
        atlas.hurt(atlas.damageSources().generic(), 14);
        int damaged = atlas.hull();
        int chargeSaved = atlas.charge();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MechMod.WRENCH.get()));
        p.setShiftKeyDown(true);
        atlas.interact(p, InteractionHand.MAIN_HAND);
        p.setShiftKeyDown(false);
        require(atlas.isRemoved(), "pack grounded damaged frame");
        var entry = p.getInventory().items.stream().filter(i -> i.is(MechMod.crate(Chassis.ATLAS))).findFirst().orElseThrow();
        BlockPos pad = new BlockPos(-9, FLOOR - 1, 3);
        var r = entry.getItem().useOn(new UseOnContext(level, p, InteractionHand.MAIN_HAND, entry, top(pad)));
        require(r.consumesAction(), "redeploy packed frame");
        MechEntity unpacked = level.getEntitiesOfClass(MechEntity.class, new net.minecraft.world.phys.AABB(pad).inflate(1))
                .stream().filter(e -> e.chassis() == Chassis.ATLAS).findFirst().orElseThrow();
        require(unpacked.charge() == chargeSaved && unpacked.hull() == damaged, "crate state restored");
        mechs.put(Chassis.ATLAS, unpacked);
        var nbt = new net.minecraft.nbt.CompoundTag();
        unpacked.saveWithoutId(nbt);
        var scratch = MechMod.entity(Chassis.ATLAS).create(level);
        scratch.load(nbt);
        require(scratch.charge() == chargeSaved && scratch.hull() == damaged && nbt.hasUUID("Owner"), "entity NBT round trip");
        report.put("repair_and_pack_round_trip", true);
        report.put("nbt_round_trip", true);
        report.put("stored_atlas_hull", damaged);
        report.put("stored_atlas_charge", chargeSaved);
    }

    private static void verifyReopened(Minecraft mc) {
        var level = player(mc).serverLevel();
        var area = new net.minecraft.world.phys.AABB(-22, FLOOR - 4, -18, 22, FLOOR + 10, 18);
        var found = level.getEntitiesOfClass(MechEntity.class, area);
        require(found.size() == 3 && found.stream().map(MechEntity::chassis).distinct().count() == 3,
                "all three distinct chassis survive actual world reopening: " + found.size());
        var atlas = found.stream().filter(e -> e.chassis() == Chassis.ATLAS).findFirst().orElseThrow();
        require(atlas.hull() == Chassis.ATLAS.hull - 14 && atlas.charge() == Chassis.ATLAS.energy,
                "world stores damaged hull and charge: " + atlas.hull() + "/" + atlas.charge());
        report.put("three_mechs_reopened", true);
        report.put("restored_hull", atlas.hull());
        report.put("restored_charge", atlas.charge());
    }

    private static BlockHitResult top(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos).add(0, .5, 0), Direction.UP, pos, false);
    }

    static void camera(Minecraft game, double x, double y, double z, float yaw, float pitch) {
        if (cam == null) cam = new ArmorStand(game.level, 0, 0, 0);
        cam.setPos(x, y - cam.getEyeHeight(), z);
        cam.setYRot(yaw); cam.setXRot(pitch); cam.setYHeadRot(yaw);
        cam.setOldPosAndRot();
        game.setCameraEntity(cam);
    }

    private static void darkroom(Minecraft mc, int charge, boolean wall) {
        var atlas = mechs.get(Chassis.ATLAS);
        var tag = atlas.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        tag.putInt("Charge", charge);
        atlas.load(tag);
        require(atlas.charge() == charge, "darkroom tester set charge");
        var fill = wall ? Blocks.GRAY_CONCRETE : Blocks.AIR;
        var level = player(mc).serverLevel();
        for (int x = -13; x <= -4; x++) {
            for (int y = FLOOR; y <= FLOOR + 7; y++) {
                level.setBlockAndUpdate(new BlockPos(x, y, 0), fill.defaultBlockState());
            }
        }
    }

    private static void levelLighting(Minecraft mc, long time) {
        player(mc).serverLevel().setDayTime(time);
    }

    static MechEntity mech(Chassis chassis) { return mechs.get(chassis); }
    static boolean usesCamera(Minecraft game) { return cam != null && game.getCameraEntity() == cam; }

    static ServerPlayer player(Minecraft game) {
        return game.getSingleplayerServer().getPlayerList().getPlayer(game.player.getUUID());
    }
    static <T> T server(Minecraft game, Supplier<T> action) throws Exception {
        return game.getSingleplayerServer().submit(action).get(12, TimeUnit.SECONDS);
    }
    static void require(boolean supported, String name) {
        if (!supported) throw new IllegalStateException("MECH QA: " + name);
    }
    private static void shot(Minecraft mc, String label) {
        Screenshot.grab(mc.gameDirectory, label + ".png", mc.getMainRenderTarget(), x -> {});
    }
    static void finish(Minecraft mc, Throwable error) {
        done = true;
        if (error != null) {
            if (SHOWCASE) DriveShowcase.abort();
            report.put("completed", false);
            report.put("issue", error.toString());
            LogUtils.getLogger().error("MECH QA FAILED at tick " + age, error);
        }
        try {
            Files.writeString(Path.of("report.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        } catch (Exception e) {
            LogUtils.getLogger().error("MECH QA could not save report", e);
        }
        mc.stop();
    }
}
