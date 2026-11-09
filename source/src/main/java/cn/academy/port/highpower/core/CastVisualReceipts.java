package cn.academy.port.highpower.core;
import java.util.LinkedHashSet;
import java.util.UUID;
/** Bounded cosmetic exactly-once receipts. Caller must first authenticate the server success and owner/session. */
public final class CastVisualReceipts {
    public static final int LIMIT=512;
    private record Receipt(UUID owner,String skill,long epoch,long input,long token) {}
    private final LinkedHashSet<Receipt> receipts=new LinkedHashSet<>();
    public boolean accept(UUID owner,String skill,long epoch,long input,long token) {
        if(owner==null||skill==null||skill.isEmpty()||epoch<0||input<0||token<=0)return false;
        if(!receipts.add(new Receipt(owner,skill,epoch,input,token)))return false;
        while(receipts.size()>LIMIT)receipts.remove(receipts.iterator().next());
        return true;
    }
    public int size(){return receipts.size();}
    public void clear(){receipts.clear();}
}
