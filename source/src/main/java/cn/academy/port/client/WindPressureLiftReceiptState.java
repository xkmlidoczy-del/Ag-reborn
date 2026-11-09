package cn.academy.port.client;

/** Local input/sequence fence. Real payment freshness is checked atomically by the integrated server. */
public final class WindPressureLiftReceiptState {
    public record Delivery(Object world,Object player,Object connection,long generation) {}
    public record Geometry(double anchorX,double anchorY,double anchorZ,int mode,double directionX,double directionY,double directionZ) {
        private boolean valid() {
            return Double.isFinite(anchorX) && Double.isFinite(anchorY) && Double.isFinite(anchorZ) && (mode==0 || mode==1)
                    && Double.isFinite(directionX) && Double.isFinite(directionY) && Double.isFinite(directionZ)
                    && Math.abs(directionY)<=1e-8 && Math.abs(directionX*directionX+directionZ*directionZ-1)<=1e-6;
        }
    }
    private long input, epoch, token, sequence, consumed, lastAppliedTick = -1, retiredTravel = -1;
    private long cancelledEpoch, cancelledThrough;
    private boolean open;
    private Geometry geometry;
    public boolean begin(long input, long epoch) {
        if (input <= 0 || epoch <= 0 || epoch < cancelledEpoch || epoch == cancelledEpoch && input <= cancelledThrough
                || open || epoch < this.epoch || epoch == this.epoch && input <= this.input) return false;
        this.input = input; this.epoch = epoch; token = sequence = consumed = 0; retiredTravel=-1;geometry=null; open = true; return true;
    }
    public boolean matches(long input, long epoch) { return open && this.input == input && this.epoch == epoch; }
    public boolean receive(long input, long epoch, long token, long sequence, boolean terminal) {
        if (!matches(input, epoch) || token <= 0 || sequence < this.sequence || sequence == this.sequence && !terminal
                || sequence <= 0 || this.token != 0 && token != this.token) return false;
        this.token = token; this.sequence = sequence;
        if (terminal) cancel(input, epoch);
        return true;
    }
    /** Direct immutable server lookup may return the same already observed but unconsumed proof. */
    public boolean observeCurrent(long input,long epoch,long token,long sequence) {
        if (!matches(input,epoch) || token<=0 || sequence<=0 || sequence<this.sequence
                || this.token!=0 && this.token!=token || sequence<=consumed) return false;
        this.token=token;this.sequence=sequence;return true;
    }
    /** Dual-clock snapshot versions are observations, not one-use physical impulses. */
    public boolean observeSnapshot(long input,long epoch,long token,long sequence) {
        if(!matches(input,epoch) || token<=0 || sequence<=0 || sequence<this.sequence
                || this.token!=0 && this.token!=token)return false;
        this.token=token;this.sequence=sequence;return true;
    }
    /** Only the opaque native witness supplies this serial; every real attempt is retired before callbacks. */
    public boolean retireTravel(long input,long epoch,long token,long sequence,long nativeTravel) {
        if(!matches(input,epoch) || token<=0 || token!=this.token || sequence<=0 || sequence!=this.sequence
                || nativeTravel<=retiredTravel || nativeTravel<=0)return false;
        retiredTravel=nativeTravel;return true;
    }
    public boolean ownsTravel(long input,long epoch,long token,long sequence,long nativeTravel) {
        return matches(input,epoch) && token>0 && token==this.token && sequence>0 && sequence==this.sequence
                && nativeTravel>0 && nativeTravel==retiredTravel;
    }
    /** Zero is bootstrap only. begin resets this; cancellation never unlocks the current captured input. */
    public long expectedToken() { return token; }
    /** Authenticated END wins regardless of its sequence, including after a local free-off fence. */
    public boolean terminal(long input,long epoch,long token) {
        if(input<=0 || epoch<=0 || token<=0 || input!=this.input || epoch!=this.epoch || this.token!=0 && token!=this.token)return false;
        cancel(input,epoch);return true;
    }
    public boolean geometryMatches(Geometry proposed) {return proposed!=null && proposed.valid() && (geometry==null || geometry.equals(proposed));}
    public boolean bindGeometry(long token,Geometry proposed) {
        if(!open || token<=0 || token!=this.token || !geometryMatches(proposed))return false;
        if(geometry==null)geometry=proposed;return true;
    }
    /** Queue delivery must match the exact live client objects and generation even after receipt reset. */
    public static boolean sameDelivery(Delivery captured,Delivery current) {
        return captured!=null && current!=null && captured.world!=null && captured.player!=null && captured.connection!=null
                && captured.world==current.world && captured.player==current.player && captured.connection==current.connection
                && captured.generation==current.generation;
    }
    public boolean available(long input, long epoch, long token, long sequence, long tick) {
        return matches(input, epoch) && token == this.token && sequence == this.sequence
                && sequence > consumed && tick >= 0 && tick != lastAppliedTick;
    }
    /** Safety callbacks may cancel or supersede a proposal after it was retired for one-shot use. */
    public boolean ownsRetired(long input,long epoch,long token,long sequence) {
        return matches(input,epoch) && token>0 && this.token==token && sequence>0
                && this.sequence==sequence && consumed==sequence;
    }
    /** Retire even a refused receipt: a later input callback cannot bank a paid impulse. */
    public void retire(long sequence) { consumed = Math.max(consumed, sequence); }
    public void applied(long sequence, long tick) { retire(sequence); lastAppliedTick = tick; }
    public void cancel(long input, long epoch) {
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = input; }
        else if (epoch == cancelledEpoch) cancelledThrough = Math.max(cancelledThrough, input);
        if (this.epoch < epoch || this.epoch == epoch && this.input <= input) open = false;
    }
    public void clear() { if (open) cancel(input, epoch); }
    public void resetConnection() { input = epoch = token = sequence = consumed = cancelledEpoch = cancelledThrough = 0; geometry=null;lastAppliedTick = retiredTravel = -1; open = false; }
}
