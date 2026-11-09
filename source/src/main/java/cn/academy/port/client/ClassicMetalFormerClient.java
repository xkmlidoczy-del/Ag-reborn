/* AcademyCraft 1.0.7 TileMetalFormer/TileEntitySound. GPLv3; see NOTICE. */
package cn.academy.port.client;

import cn.academy.port.former.ClassicMetalFormer;
import cn.academy.port.former.ClassicMetalFormerBlockEntity;
import cn.academy.port.wireless.ClassicWirelessProtocol;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Distribution-isolated registration: the ordinary cube needs no block entity renderer or OBJ loader. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClassicMetalFormerClient {
    private static final UUID OPEN_TOKEN = new UUID(0, 0);
    private ClassicMetalFormerClient() {}

    @SubscribeEvent public static void menus(RegisterMenuScreensEvent event) {
        event.register(ClassicMetalFormer.MENU.get(), ClassicMetalFormerScreen::new);
    }

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ClassicMetalFormerBlockEntity.setClientObserver(Sounds::observe));
    }

    public static void openWireless(int menuId, BlockPos origin) {
        PacketDistributor.sendToServer(new ClassicWirelessProtocol.Request(
                menuId, OPEN_TOKEN, "open_former", origin, "", ""));
    }

    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
    public static final class Sounds {
        private static final Map<BlockPos, FormerLoop> LOOPS = new HashMap<>();
        private static ClientLevel world;
        private Sounds() {}

        static void observe(ClassicMetalFormerBlockEntity tile) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || tile.getLevel() != mc.level) return;
            if (world != mc.level) reset(mc.level);
            BlockPos key = tile.getBlockPos().immutable();
            FormerLoop existing = LOOPS.get(key);
            if (existing != null && (existing.isStopped() || existing.tile != tile || !tile.clientWorking())) {
                existing.finish();
                LOOPS.remove(key);
            }
            if (tile.clientWorking() && !tile.isRemoved() && !LOOPS.containsKey(key)) {
                FormerLoop loop = new FormerLoop(tile, mc.level);
                LOOPS.put(key, loop);
                mc.getSoundManager().play(loop);
            }
        }

        private static void reset(ClientLevel next) {
            for (FormerLoop loop : LOOPS.values()) loop.finish();
            LOOPS.clear();
            world = next;
        }

        @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != world) reset(mc.level);
            LOOPS.entrySet().removeIf(entry -> {
                FormerLoop loop = entry.getValue();
                if (!loop.valid()) loop.finish();
                return loop.isStopped();
            });
        }
    }

    private static final class FormerLoop extends AbstractTickableSoundInstance {
        private final ClassicMetalFormerBlockEntity tile;
        private final ClientLevel world;
        FormerLoop(ClassicMetalFormerBlockEntity tile, ClientLevel world) {
            super(SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(
                    "academy", "machine.machine_work")), SoundSource.MASTER, RandomSource.create());
            this.tile = tile;
            this.world = world;
            volume = .6f;
            looping = true;
            delay = 0;
            x = tile.getBlockPos().getX() + .5;
            y = tile.getBlockPos().getY() + .5;
            z = tile.getBlockPos().getZ() + .5;
        }

        boolean valid() {
            return Minecraft.getInstance().level == world && !tile.isRemoved()
                    && world.getBlockEntity(tile.getBlockPos()) == tile && tile.clientWorking();
        }
        @Override public void tick() { if (!valid()) stop(); }
        void finish() { stop(); }
    }
}
