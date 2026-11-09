/* AcademyCraft1.0.7 CPData prediction/self-event/remote-argument boundaries. GPLv3; see NOTICE. */
package cn.academy.port.core;
import java.util.Objects;
import java.util.function.Consumer;
/** No snapshots or scheduler: client prediction and synchronous activation observer delivery only. */
public final class ActivationTransport {
    private ActivationTransport(){}
    public static Boolean requested(String value){return "true".equals(value)?Boolean.TRUE:"false".equals(value)?Boolean.FALSE:null;}
    /** Original client setter predicts before sending its explicit requested value, without a local event. */
    public static void predict(AbilityProgress state,boolean active,Consumer<Boolean> send){
        Objects.requireNonNull(state);Objects.requireNonNull(send);state.activated=active;send.accept(active);
    }
    /** Source sendToSelf is synchronous. Its remote argument reads the live flag after nested listeners return. */
    public static void dispatch(AbilityProgress state,boolean active,Consumer<Boolean> self,Consumer<Boolean> remote){
        Objects.requireNonNull(state);Objects.requireNonNull(self);Objects.requireNonNull(remote);self.accept(active);remote.accept(state.activated);
    }
}
