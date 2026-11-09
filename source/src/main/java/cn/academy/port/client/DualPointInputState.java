package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import java.util.ArrayDeque;

/** Authenticated NEW211 tap/mark identities survive release; endpoints and hits are never predicted. */
public final class DualPointInputState {
    public static final int MAX_PENDING = 16;
    private final ArrayDeque<HighPowerInput.Input> pending = new ArrayDeque<>();
    private HighPowerInput.Input latest, accepted;
    public record DispatchContext(Object world,Object player,Object connection,long session,int actor,long ownerEpoch,
                                  int preset,long revision,String mappings) {}
    public record Gates(DispatchContext context,int slot,int physicalKey,boolean inGame,boolean activated,
                        boolean canUseAbility,boolean mapped,boolean enabled,boolean screenClosed,
                        boolean focused,boolean cameraOwned,boolean sourceEligible,boolean cooldownReady) {
        public boolean allowed(){return context!=null&&context.world!=null&&context.player!=null&&context.connection!=null
                &&context.session>0&&context.actor>=0&&context.ownerEpoch>0&&slot>=0&&slot<4&&physicalKey!=0
                &&inGame&&activated&&canUseAbility&&mapped&&enabled&&screenClosed&&focused&&cameraOwned&&sourceEligible;}
    }
    private DispatchContext owner;
    private int physicalKey;
    public boolean eligible(Gates g){return g!=null&&g.allowed()&&g.cooldownReady;}
    /** Only an exact outstanding owner receipt survives its own cooldown; fresh taps still require readiness. */
    public boolean delegateEligible(Gates g){
        if(latest!=null&&!ownsControl(g))return false;
        return ownsControl(g)&&(!pending.isEmpty()||accepted!=null)||eligible(g);
    }
    public boolean tap(HighPowerInput.Input input,Gates g){
        if(!eligible(g)||input==null||input.slot()!=g.slot||input.actor()!=g.context.actor||input.epoch()!=g.context.ownerEpoch)return false;
        tap(input);owner=g.context;physicalKey=g.physicalKey;return true;
    }
    private boolean ownsControl(Gates g){return g!=null&&g.allowed()&&latest!=null&&latest.slot()==g.slot
            &&physicalKey==g.physicalKey&&same(owner,g.context);}
    private static boolean same(DispatchContext a,DispatchContext b){return a!=null&&b!=null&&a.world==b.world
            &&a.player==b.player&&a.connection==b.connection&&a.session==b.session&&a.actor==b.actor&&a.ownerEpoch==b.ownerEpoch
            &&a.preset==b.preset&&a.revision==b.revision&&java.util.Objects.equals(a.mappings,b.mappings);}


    public void tap(HighPowerInput.Input input) {
        if (input == null || HighPowerInput.parse(input.wire()) == null)
            throw new IllegalArgumentException("Valid authenticated input required");
        if (latest != null && (latest.epoch() != input.epoch() || latest.actor() != input.actor())) reset();
        if (latest != null && input.input() <= latest.input()) throw new IllegalArgumentException("Input must advance");
        latest = input;
        if (pending.size() == MAX_PENDING) pending.removeFirst();
        pending.addLast(input);
    }
    public void release(HighPowerInput.Input input) { /* A tap/mark is not a hold. */ }
    public boolean permits(long input, long epoch) {
        return input > 0 && latest != null && epoch == latest.epoch()
                && (accepted != null && accepted.input() == input && accepted.epoch() == epoch
                || pending.stream().anyMatch(p -> p.input() == input && p.epoch() == epoch));
    }
    public void accepted(long input, long epoch) {
        if (!permits(input, epoch)) return;
        for (var p : pending) if (p.input() == input && p.epoch() == epoch) { accepted = p; break; }
        pending.removeIf(p -> p.epoch() == epoch && p.input() < input);
    }
    public void ended(long input, long epoch) {
        if (accepted != null && accepted.epoch() == epoch && accepted.input() <= input) accepted = null;
        pending.removeIf(p -> p.epoch() == epoch && p.input() <= input);
    }
    public HighPowerInput.Input cancel() { var result = latest; reset(); return result; }
    public HighPowerInput.Input cancel(HighPowerInput.Input input) { return latest != null && latest.equals(input) ? cancel() : null; }
    public HighPowerInput.Input cancelWhenBlocked(boolean blocked) { return blocked ? cancel() : null; }
    public HighPowerInput.Input current() { return latest; }
    public int pendingCount() { return pending.size(); }
    public void reset() { latest = accepted = null; owner=null;physicalKey=0;pending.clear(); }
}
