package cn.academy.port.client;

/** Camera ownership is independent of Minecraft bootstrap or network connectivity. */
public final class CanonLiquidViewRulesTest {
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args) {
        var keep=CanonLiquidViewRules.LeaseAction.KEEP;
        var restore=CanonLiquidViewRules.LeaseAction.RESTORE_PLAYER;
        var forget=CanonLiquidViewRules.LeaseAction.FORGET;
        require(CanonLiquidViewRules.lease(true,true,true,false)==keep,"valid owned camera persists");
        require(CanonLiquidViewRules.lease(true,true,true,true)==restore,"manual exit restores player");
        require(CanonLiquidViewRules.lease(true,false,true,false)==restore,"dimension switch restores player");
        require(CanonLiquidViewRules.lease(true,true,false,false)==restore,"removed or revoked body restores player");
        require(CanonLiquidViewRules.lease(false,false,false,true)==forget,"foreign camera is never replaced");
        require(CanonLiquidViewRules.lease(false,true,true,false)==forget,"foreign camera wins even while body exists");
        require(CanonLiquidViewRules.mayEnter(true,false,1),"one owned body admits control");
        require(!CanonLiquidViewRules.mayEnter(false,false,1),"foreign camera prevents entry");
        require(!CanonLiquidViewRules.mayEnter(true,true,1),"open screen prevents entry");
        require(!CanonLiquidViewRules.mayEnter(true,false,0),"no owner body prevents entry");
        require(!CanonLiquidViewRules.mayEnter(true,false,2),"ambiguous owner bodies prevent entry");
        require(CanonLiquidViewRules.hideFirstPersonHands(true,true,true),"owned active body hides hands");
        require(!CanonLiquidViewRules.hideFirstPersonHands(false,false,false),"normal view retains hands");
        require(!CanonLiquidViewRules.hideFirstPersonHands(true,false,true),"foreign camera retains hands");
        require(!CanonLiquidViewRules.hideFirstPersonHands(true,true,false),"expired or revoked lease retains hands");
        System.out.println("CanonLiquidViewRulesTest passed 15 camera-ownership checks");
    }
}
