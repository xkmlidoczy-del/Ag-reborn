package cn.academy.port.client.terminal;

/** Client-only identity and response/disposal guards, independent of native bootstrap for finite lifecycle checks. */
public final class TerminalClientLifecycle {
    private TerminalClientLifecycle(){}
    public static final class Session {
        private Object world,player;
        public boolean synchronize(Object nextWorld,Object nextPlayer){boolean changed=world!=nextWorld||player!=nextPlayer;world=nextWorld;player=nextPlayer;return changed;}
    }
    public static final class Pending {
        private String action="";private long target,selected;
        public void begin(String next,long clicked,long source){action=next;target=clicked;selected=source;}
        public boolean accept(String reply,long clicked,long source){if(action.isEmpty()||!action.equals(reply)||target!=clicked||selected!=source)return false;action="";return true;}
        public void clear(){action="";}
    }
    public record Disposal(boolean release,boolean notifyServer){}
    public static final class Lease {
        private final Object world,player;private boolean released;
        public Lease(Object world,Object player){this.world=world;this.player=player;}
        public Disposal release(Object currentWorld,Object currentPlayer,boolean connected){if(released)return new Disposal(false,false);released=true;return new Disposal(true,connected&&currentWorld!=null&&currentPlayer!=null&&currentWorld==world&&currentPlayer==player);}
    }
}
