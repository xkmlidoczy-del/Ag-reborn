package cn.academy.port.gametest;

import cn.academy.port.highpower.*;
import java.math.BigDecimal;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static cn.academy.port.gametest.ZeroGapArmorNativeFixture.*;

/** Category traits enabled, genuine native Arrow.tick, exact specialist contact and category fallback side by side. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerCategorySpecialistArrowRuntimeTests {
    private static final String TEMPLATE="highpower_empty",BATCH="highpower_category_specialist_arrows";
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=95)
    public static void category_shell_allows_real_scatter_contact_to_pay_native_amount(GameTestHelper h){specialist(h,"scatter");}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=95)
    public static void category_shell_allows_real_curtain_contact_and_its_arrow_efficiency(GameTestHelper h){specialist(h,"curtain");}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=95)
    public static void category_shell_allows_real_zero_gap_contact_to_pay_native_amount(GameTestHelper h){specialist(h,"armor");}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=95)
    public static void scatter_real_gap_keeps_category_four_percent_vaporization(GameTestHelper h){fallback(h,"gap");}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=95)
    public static void scatter_real_broken_center_keeps_category_four_percent_vaporization(GameTestHelper h){fallback(h,"broken");}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=95)
    public static void unsupported_no_gravity_arrow_keeps_category_four_percent_vaporization(GameTestHelper h){fallback(h,"unsupported");}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void historical_curtain_contact_survives_successful_twenty_tick_renewal(GameTestHelper h){renewal(h,false);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void failed_curtain_renewal_retires_historical_contact_and_keeps_category_fallback(GameTestHelper h){renewal(h,true);}
    private static void renewal(GameTestHelper h,boolean exhaust){
        var f=new ZeroGapArmorNativeFixture(h,0);forceCategoryTraits(h);boolean[] drained={false};
        h.runAfterDelay(61,()->{
            h.assertTrue(DissipationCurtain.start(f.p,f.wire(2,1)),"actual paid native curtain startup");
            long firstUntil=DissipationCurtain.paidUntil(f.p);BigDecimal before=f.shield(),hp=f.hp();String mastery=String.valueOf(f.p.getPersistentData().get(DissipationCurtain.MASTERY_KEY));
            if(exhaust)f.hook(new Object(){@SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST)public void pre(net.neoforged.neoforge.event.tick.ServerTickEvent.Pre event){
                if(!drained[0]&&event.getServer()==f.p.server&&DissipationCurtain.renewalPending(f.p)){
                    // An explicit native finite-budget depletion transaction, never a client write or invented impact.
                    double remaining=HighPowerResources.availableCp(f.p);h.assertTrue(remaining>0&&HighPowerResources.trySpend(f.p,DualPoint.ID,remaining,1),"real finite wallet expenditure before renewal");drained[0]=true;
                }
            }});
            h.runAfterDelay(14,()->{
                Arrow arrow=f.arrow(f.p.position().add(0,1.62,8),new Vec3(0,.1,-1),null);
                h.runAfterDelay(4,()->h.assertTrue(DissipationCurtainArrowReceipts.receiptCount(f.p)>0,"genuine native flight crossed the outer curtain before renewal"));
                h.runAfterDelay(12,()->{
                    BigDecimal spent=before.subtract(f.shield());eq(h,f.hp(),hp,"real surviving base protects HP");
                    if(exhaust){h.assertTrue(drained[0]&&!DissipationCurtain.active(f.p),"failed real renewal ended specialist immediately");eq(h,spent,f.armor().base().maxShield().multiply(EsperShields.PROJECTILE_COST),"unpaid historical contact cannot avoid exact category fallback");h.assertTrue(String.valueOf(f.p.getPersistentData().get(DissipationCurtain.MASTERY_KEY)).equals(mastery),"failed renewal cannot earn specialist contact credit");}
                    else {h.assertTrue(DissipationCurtain.paidUntil(f.p)>firstUntil,"actual second payment advanced the finite lease");h.assertTrue(spent.signum()>0&&spent.compareTo(f.armor().base().maxShield().multiply(EsperShields.PROJECTILE_COST))<0,"renewed historical contact paid native arrow efficiency instead of four-percent vaporization");h.assertTrue(!String.valueOf(f.p.getPersistentData().get(DissipationCurtain.MASTERY_KEY)).equals(mastery),"real historical arrow committed specialist absorption after renewal");}
                    h.assertTrue(arrow.isRemoved(),"real ordinary arrow finished its native body impact");h.succeed();
                });
            });
        });
    }
    private static void forceCategoryTraits(GameTestHelper h){
        EsperLegacyBypass.force(true);
        h.testInfo.addListener(new GameTestListener(){boolean closed;private void close(){if(!closed){closed=true;EsperLegacyBypass.force(false);}}
            public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
    }
    private static void specialist(GameTestHelper h,String kind){
        var f=new ZeroGapArmorNativeFixture(h,0);forceCategoryTraits(h);
        h.runAfterDelay(61,()->{
            h.assertTrue(EsperShields.holding(f.p),"category shell really enabled in this regression");
            boolean started=switch(kind){case "scatter"->ScatterPlates.toggle(f.p,f.wire(1,1));case "curtain"->DissipationCurtain.start(f.p,f.wire(2,1));default->ZeroGapArmor.toggle(f.p,f.wire(0,1));};
            h.assertTrue(started,"authenticated paid specialist activation");
            h.runAfterDelay(7,()->{
                BigDecimal before=f.shield(),hp=f.hp();String key=switch(kind){case "scatter"->ScatterPlates.MASTERY_KEY;case "curtain"->DissipationCurtain.MASTERY_KEY;default->ZeroGapArmor.MASTERY_KEY;};
                String masteryBefore=String.valueOf(f.p.getPersistentData().get(key));
                Arrow arrow=kind.equals("armor")?f.frontArrow(3,3):f.arrow(f.p.position().add(0,kind.equals("curtain")?1.62:1,kind.equals("curtain")?8:3),new Vec3(0,0,kind.equals("curtain")?-8:-3),null);
                float[] incoming={0};f.hook(new Object(){@SubscribeEvent public void hurt(LivingIncomingDamageEvent event){if(event.getEntity()==f.p&&event.getSource().getDirectEntity()==arrow)incoming[0]=event.getAmount();}});
                arrow.tick();
                h.assertTrue(incoming[0]>0,"category shell did not discard the real supported arrow before native hurt");
                BigDecimal nativeAmount=BigDecimal.valueOf((double)incoming[0]);BigDecimal expected=kind.equals("curtain")?nativeAmount.multiply(new BigDecimal("0.8")):nativeAmount;
                eq(h,before.subtract(f.shield()),expected,"real specialist pays native amount, including curtain 1.25x arrow absorption");
                h.assertTrue(expected.compareTo(f.armor().base().maxShield().multiply(EsperShields.PROJECTILE_COST))<0,"specialist grants an actual shield-saving benefit versus category vaporization");
                h.assertTrue(!String.valueOf(f.p.getPersistentData().get(key)).equals(masteryBefore),"real authenticated specialist contact awarded bounded mastery");
                eq(h,f.hp(),hp,"successful specialist preserves HP");h.assertTrue(EsperShields.holding(f.p),"remaining base shield retains super armor");h.succeed();
            });
        });
    }
    private static void fallback(GameTestHelper h,String mode){
        var f=new ZeroGapArmorNativeFixture(h,0);forceCategoryTraits(h);
        h.runAfterDelay(61,()->{
            if(mode.equals("broken"))f.seed(amounts(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxShield().divide(BigDecimal.valueOf(50)).toPlainString(),cn.academy.port.highpower.core.HighPowerLevel.LV5.maxShield().divide(BigDecimal.valueOf(50)).toPlainString(),"0","0",cn.academy.port.highpower.core.HighPowerLevel.LV5.maxShield().divide(BigDecimal.valueOf(50)).toPlainString(),cn.academy.port.highpower.core.HighPowerLevel.LV5.maxShield().divide(BigDecimal.valueOf(50)).toPlainString()),copies("0"));
            h.assertTrue(ScatterPlates.toggle(f.p,f.wire(1,1)),"real active scatter defense, not absence-of-controller shortcut");
            h.runAfterDelay(7,()->{
                BigDecimal before=f.shield(),hp=f.hp(),max=f.armor().base().maxShield();String masteryBefore=String.valueOf(f.p.getPersistentData().get(ScatterPlates.MASTERY_KEY));
                Arrow arrow=f.arrow(f.p.position().add(mode.equals("gap")?.4:0,1,3),new Vec3(0,0,-3),null);if(mode.equals("unsupported"))arrow.setNoGravity(true);
                arrow.tick();eq(h,f.shield(),before.subtract(max.multiply(EsperShields.PROJECTILE_COST)),"unclaimed native impact still pays exact 4% base vaporization");
                eq(h,f.hp(),hp,"base fallback preserves HP");h.assertTrue(arrow.isRemoved(),"base shell actually vaporized the unsupported/gap/broken-facet arrow");
                h.assertTrue(String.valueOf(f.p.getPersistentData().get(ScatterPlates.MASTERY_KEY)).equals(masteryBefore),"category fallback cannot counterfeit specialist contact/mastery");h.succeed();
            });
        });
    }
}
