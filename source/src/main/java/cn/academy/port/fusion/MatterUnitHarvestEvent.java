/* AcademyCraft1.0.7 MatterUnitHarvestEvent, modern NeoForge event bus adapter. GPLv3. */
package cn.academy.port.fusion;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;
/** Source event after material capture or release; achievements are a separate unported consumer. */
public final class MatterUnitHarvestEvent extends Event {
    public final Player player;public final String material;
    public MatterUnitHarvestEvent(Player player,String material){this.player=player;this.material=material;}
}
