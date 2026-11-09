package cn.academy.port.highpower;

import cn.academy.port.highpower.core.*;
import java.math.BigDecimal;

/** Residual, shared-budget and overkill behavior of the always-present category shield. */
public final class CategoryShieldRegressionTest {
    static BigDecimal n(String s){return new BigDecimal(s);}
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    public static void main(String[] args){
        var before=new ExactCombatState(n("100"),n("100"),n("80"),n("80"),false);
        var plan=new ExactCombatState(n("90"),n("100"),n("70"),n("80"),false);
        var after=CategoryShieldRules.finish(before,plan,n("10"));
        check(after.health().compareTo(n("100"))==0&&after.shield().compareTo(n("60"))==0,"residual paid by remaining base shield after specialized payment");
        after=CategoryShieldRules.finish(before,plan,n("90"));
        check(after.health().compareTo(n("80"))==0&&after.shield().signum()==0,"breaking hit spends shield before only excess HP");
        var clipped=new ExactCombatState(BigDecimal.ZERO,n("100"),n("70"),n("80"),false);
        after=CategoryShieldRules.finish(before,clipped,n("1000"));
        check(after.isDead()&&after.shield().signum()==0,"overkill is never lost by reading clipped HP loss");
        for(String cat:new String[]{"electromaster","meltdowner","teleporter","vecmanip"}){
            var hud=new HighPowerRecoveryHudModel.Values("100","100","80","80","full","0",0,0,0,true,"0","0",false,cat);
            check(hud.shieldVisible()&&hud.shieldHolding(),cat+" advertises real shield and super armor");
            var empty=new HighPowerRecoveryHudModel.Values("100","100","0","80","full","0",0,0,0,false,"0","0",false,cat);
            check(empty.shieldVisible()&&!empty.shieldHolding(),cat+" broken shield has no super armor");
        }
        System.out.println("PASS category shield residual / overkill / four-category HUD checks");
    }
}
