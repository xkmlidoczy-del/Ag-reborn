/* Modern common counterpart of classic CalcEvent player calculations. GPLv3; see NOTICE. */
package cn.academy.port.api;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
/** Source calculation events are mutable float values and are not cancelable. */
public abstract class AbilityCalculationEvent extends Event {
    public final ServerPlayer player;
    public float value;
    protected AbilityCalculationEvent(ServerPlayer player,float value){this.player=player;this.value=value;}
    public static final class MaxCP extends AbilityCalculationEvent {public MaxCP(ServerPlayer player,float value){super(player,value);}}
    public static final class MaxOverload extends AbilityCalculationEvent {public MaxOverload(ServerPlayer player,float value){super(player,value);}}
    public static final class CPRecoverSpeed extends AbilityCalculationEvent {public CPRecoverSpeed(ServerPlayer player,float value){super(player,value);}}
    public static final class OverloadRecoverSpeed extends AbilityCalculationEvent {public OverloadRecoverSpeed(ServerPlayer player,float value){super(player,value);}}
}
