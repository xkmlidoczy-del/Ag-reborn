package cn.academy.port.tutorial;

import java.util.BitSet;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/** Source TutorialData/Conditions state machine. Counters and dirty are intentionally transient. */
public final class TutorialState {
    public enum EventKind { CRAFT, PICKUP, SMELT }
    public static final int CONDITION_COUNT=ClassicTutorials.CONDITION_ITEMS.size()*EventKind.values().length;
    private final BitSet conditions=new BitSet(CONDITION_COUNT);
    private final Set<String> activated=new LinkedHashSet<>();
    private final int misakaID;
    private final boolean giveCloudTerminal;
    private boolean acquired, opened, dirty;
    private int activationCounter=3, grantCounter=10;
    public TutorialState(int misakaID,boolean giveCloudTerminal) {
        if(misakaID<1000||misakaID>=19000)throw new IllegalArgumentException("Misaka ID outside classic exclusive range");
        this.misakaID=misakaID;this.giveCloudTerminal=giveCloudTerminal;
    }
    public int misakaID() { return misakaID; }
    public boolean tutorialAcquired() { return acquired; }
    public boolean firstOpened() { return opened; }
    public void markOpened() { opened=true; }
    public boolean dirty() { return dirty; }
    public long[] conditionBits() { return conditions.toLongArray(); }
    public Set<String> activatedIds() { return Set.copyOf(activated); }
    public boolean activated(String id) { return activated.contains(id); }
    public static int conditionIndex(String item,EventKind event) {
        int index=ClassicTutorials.CONDITION_ITEMS.indexOf(item);
        return index<0?-1:index*3+event.ordinal();
    }
    /** Called only for actual crafted, picked-up or smelted events, never inventory scans. */
    public boolean recordObtained(String item,EventKind event) {
        int index=conditionIndex(item,event);
        if(index<0||conditions.get(index))return false;
        conditions.set(index);dirty=true;return true;
    }
    public boolean visible(String id) {
        var page=ClassicTutorials.page(id);
        return page.defaultInstalled()||page.obtainedItems().stream().anyMatch(item->{
            int first=conditionIndex(item,EventKind.CRAFT);
            return conditions.get(first)||conditions.get(first+1)||conditions.get(first+2);
        });
    }
    /** Source schedules execute activation before first-guide grant when both fall on the same tick. */
    public void tick(Consumer<String> activation,Runnable guideDrop,Runnable sync) {
        if(--activationCounter<=0) {
            activationCounter=3;
            if(dirty) {
                dirty=false;
                for(var page:ClassicTutorials.pages())if(!page.defaultInstalled()&&!activated.contains(page.id())&&visible(page.id())) {
                    activated.add(page.id());activation.accept(page.id());
                }
                sync.run();
            }
        }
        if(giveCloudTerminal&&--grantCounter<=0) {
            grantCounter=10;
            if(!acquired) { guideDrop.run();acquired=true; }
        }
    }
    /** Persisted source fields only; reopening a data part starts new 3/10 schedules and clears dirty. */
    public void restore(long[] bits,Collection<String> ids,boolean acquired,boolean opened) {
        conditions.clear();conditions.or(BitSet.valueOf(bits));
        activated.clear();activated.addAll(ids);
        this.acquired=acquired;this.opened=opened;dirty=false;activationCounter=3;grantCounter=10;
    }
}
