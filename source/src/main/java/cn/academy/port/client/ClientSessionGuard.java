package cn.academy.port.client;
/** Identity guard for same-dimension respawn as well as level changes; no game APIs. */
public final class ClientSessionGuard {
    private Object level, player;
    public boolean synchronize(Object nextLevel,Object nextPlayer) {
        if(level==nextLevel&&player==nextPlayer)return false;
        level=nextLevel;player=nextPlayer;return true;
    }
}
