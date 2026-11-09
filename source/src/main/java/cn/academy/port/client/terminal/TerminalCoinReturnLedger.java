package cn.academy.port.client.terminal;
import java.util.LinkedHashSet;
/** De-duplicates the authoritative natural-return packet and local trajectory fallback, scoped to one client world. */
public final class TerminalCoinReturnLedger {
    private record Return(int owner,long token){}
    private final LinkedHashSet<Return> seen=new LinkedHashSet<>();
    public boolean claim(int owner,long token){if(token<=0)return false;Return returned=new Return(owner,token);if(!seen.add(returned))return false;if(seen.size()>256)seen.remove(seen.iterator().next());return true;}
    public void clear(){seen.clear();}
}
