package cn.academy.port.client;

import java.util.TreeMap;

/** Cosmetic rendezvous only. Neither receiving a paid visual nor this outcome ledger grants motion. */
public final class BeamStepFeedbackState {
    public static final int LIMIT = 3, LIFE = 20;
    public enum Outcome { APPLIED, PAID_NOOP, PAID_EXPIRED }
    public enum Notice { NONE, PAID_NOOP, PAID_EXPIRED }
    public record Visual(long token, long epoch, long issued, long expires, BeamStepPlateState.Point at) {}
    public record Decision(Notice notice, Visual visual, int visualAge) {
        private static final Decision NONE = new Decision(Notice.NONE, null,0);
    }
    private static final class Entry {
        final long token, epoch, issued, expires;
        int age;
        Visual visual;
        Outcome outcome;
        boolean delivered;
        Entry(long token, long epoch, long issued, long now) {
            this.token=token; this.epoch=epoch; this.issued=issued; expires=issued+LIFE;
            age=(int)Math.max(0,now-issued);
        }
    }
    private final TreeMap<Long,Entry> entries = new TreeMap<>();
    private long retiredFloor;
    /** Authenticated, actually applied local motion is the only path to Outcome.APPLIED. */
    public Decision outcome(long token, long epoch, long issued, long grantExpires, long now, Outcome outcome) {
        if (outcome == null || issued < 0 || issued > Long.MAX_VALUE-LIFE
                || grantExpires != issued+BeamStepReceiptState.TTL) return Decision.NONE;
        Entry entry = entry(token,epoch,issued,now);
        if (entry == null || entry.outcome != null) return Decision.NONE;
        entry.outcome=outcome;
        if (outcome == Outcome.APPLIED) return deliver(entry);
        entry.visual=null;
        return new Decision(outcome == Outcome.PAID_EXPIRED ? Notice.PAID_EXPIRED : Notice.PAID_NOOP,null,0);
    }
    public Decision visual(Visual visual, long now) {
        if (visual == null || visual.at() == null || !visual.at().finite()
                || visual.issued() < 0 || visual.issued() > Long.MAX_VALUE-LIFE
                || visual.expires() != visual.issued()+LIFE) return Decision.NONE;
        Entry entry = entry(visual.token(),visual.epoch(),visual.issued(),now);
        if (entry == null || entry.visual != null || entry.delivered
                || entry.outcome != null && entry.outcome != Outcome.APPLIED) return Decision.NONE;
        entry.visual=visual;
        return deliver(entry);
    }
    private Decision deliver(Entry entry) {
        if (entry.outcome != Outcome.APPLIED || entry.visual == null || entry.delivered) return Decision.NONE;
        entry.delivered=true;
        return new Decision(Notice.NONE,entry.visual,entry.age);
    }
    private Entry entry(long token,long epoch,long issued,long now) {
        expire(now,false);
        if (token<=0 || token<=retiredFloor || epoch<=0 || issued<0 || issued>Long.MAX_VALUE-LIFE
                || now<issued-1 || now>=issued+LIFE) return null;
        Entry old=entries.get(token);
        if (old!=null) return old.epoch==epoch && old.issued==issued ? old : null;
        if (entries.size()==LIMIT) {
            long first=entries.firstKey();
            if(token<first) { retiredFloor=Math.max(retiredFloor,token); return null; }
            entries.remove(first); retiredFloor=Math.max(retiredFloor,first);
        }
        Entry next=new Entry(token,epoch,issued,now); entries.put(token,next); return next;
    }
    public void tick(long now,boolean paused) { expire(now,!paused); }
    private void expire(long now,boolean advance) {
        var it=entries.entrySet().iterator();
        while(it.hasNext()) {
            Entry entry=it.next().getValue();
            if(advance)entry.age++;
            if(entry.age>=LIFE || now>=entry.expires) { retiredFloor=Math.max(retiredFloor,entry.token); it.remove(); }
        }
    }
    public void terminal(long token) { retiredFloor=Math.max(retiredFloor,token); entries.headMap(token,true).clear(); }
    public int size() { return entries.size(); }
    public void clear() { if(!entries.isEmpty())retiredFloor=Math.max(retiredFloor,entries.lastKey()); entries.clear(); }
    public void resetConnection() { entries.clear(); retiredFloor=0; }
}
