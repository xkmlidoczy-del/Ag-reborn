package dev.acafauna.standalone;
import dev.acafauna.core.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
@Mod(FaunaCore.ID)
public final class AcaFauna {
    public AcaFauna(IEventBus bus,ModContainer container) {
        FaunaCore.register(bus);
        container.registerConfig(ModConfig.Type.SERVER,FaunaConfig.SPEC,"aca-fauna-server.toml");
    }
}
