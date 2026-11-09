/* Real client activation/snapshot seam: source prediction with the existing modern cleanup fallback. GPLv3. */
package cn.academy.port.client;
import cn.academy.port.AbilityStorage;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ActivationTransport;
import net.minecraft.nbt.CompoundTag;
import java.util.Objects;
import java.util.function.Consumer;
/** Transient reconciliation only. It does not implement source key groups or synthesize observer events. */
public final class ClassicActivationClientState {
    private boolean authoritativeActivated;
    public void resetSession(AbilityProgress fresh){authoritativeActivated=Objects.requireNonNull(fresh).activated;}
    /** The existing skill/delegate override wins; only the default branch predicts and sends a desired flag. */
    public void activate(AbilityProgress local,boolean overridden,Runnable override,Consumer<Boolean> send){
        Objects.requireNonNull(local);Objects.requireNonNull(override);Objects.requireNonNull(send);
        if(!local.hasCategory())return;
        if(overridden)override.run();else ActivationTransport.predict(local,!local.isActivated(),send);
    }
    private static String signature(AbilityProgress state){return state.category+":"+state.activated+":"+state.presets.current()+":"+state.presets.revision();}
    /** Preserve local rejection/category/preset cleanup, and add acknowledgements hidden by prediction. */
    public void receive(AbilityProgress local,CompoundTag tag,Consumer<AbilityProgress> install,Runnable resetDelegates){
        Objects.requireNonNull(local);Objects.requireNonNull(tag);Objects.requireNonNull(install);Objects.requireNonNull(resetDelegates);
        var snapshot=AbilityStorage.decode(tag);snapshot.interfering=tag.getBoolean("interfering");
        boolean reset=authoritativeActivated!=snapshot.activated||!signature(local).equals(signature(snapshot));
        authoritativeActivated=snapshot.activated;install.accept(snapshot);if(reset)resetDelegates.run();
    }
}
