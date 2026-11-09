package cn.academy.port.client;

/** Pure camera ownership decisions; never transfers a camera already owned by another system. */
public final class CanonLiquidViewRules {
    public enum LeaseAction { KEEP, RESTORE_PLAYER, FORGET }
    private CanonLiquidViewRules() {}
    public static LeaseAction lease(boolean cameraIsBody,boolean sameWorld,boolean bodyValid,boolean toggle) {
        if(!cameraIsBody)return LeaseAction.FORGET;
        if(!sameWorld||!bodyValid||toggle)return LeaseAction.RESTORE_PLAYER;
        return LeaseAction.KEEP;
    }
    /** Hide first-person hands only for a still-valid camera lease owned by this client. */
    public static boolean hideFirstPersonHands(boolean hasLease,boolean cameraIsBody,boolean bodyValid) {
        return hasLease&&cameraIsBody&&bodyValid;
    }
    public static boolean mayEnter(boolean playerCamera,boolean screenOpen,int ownedBodies) {
        return playerCamera&&!screenOpen&&ownedBodies==1;
    }
}
