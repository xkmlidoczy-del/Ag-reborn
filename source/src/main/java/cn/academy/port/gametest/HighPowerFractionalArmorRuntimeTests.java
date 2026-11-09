package cn.academy.port.gametest;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.gametest.*;
/** Regression for real native contacts against a standard armored, gravity-bound husk. */
@GameTestHolder("academy_highpower_spatial_corridor")
@PrefixGameTestTemplate(false)
public final class HighPowerFractionalArmorRuntimeTests {
 private static final String BATCH="highpower_fractional_armor_boundary";
 private static BigDecimal total(LivingEntity e){return new BigDecimal(HighPowerCombat.healthText(e)).add(HighPowerCombat.shield(e));}
 private static void ordinaryHusk(LivingEntity e){e.setNoGravity(false);e.getAttribute(Attributes.ARMOR).setBaseValue(2);e.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);}
 @GameTest(template="highpower_empty",batch=BATCH,timeoutTicks=35)
 public static void fractional_corridor_share_survives_ordinary_husk_armor(GameTestHelper h){
  var f=new HighPowerSpatialPincerCorridorRuntimeTests.Fixture(h,true);ordinaryHusk(f.target);var hit=f.observe(f.target);BigDecimal before=total(f.target);
  BigDecimal share=HighPowerGrowth.values(f.p).damage().multiply(new BigDecimal("2.8")).divide(BigDecimal.valueOf(3),256,RoundingMode.DOWN);
  BigDecimal expected=share.multiply(new BigDecimal("0.984")).setScale(256,RoundingMode.DOWN);
  f.begin(()->{f.first().tick();h.assertTrue(hit.posts==1,"one actual native projectile contact completes damage");
   h.assertTrue(before.subtract(total(f.target)).compareTo(expected)==0,"ordinary armor reduces the full fractional share exactly within the ledger scale");
   h.assertTrue(!f.target.isNoGravity()&&f.target.getArmorValue()==2&&f.target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)==0,"standard husk defenses and gravity remain active");SpatialPincerCorridor.cancelActive(f.p);});f.finish(22);
 }
 @GameTest(templateNamespace="academy_highpower_array",template="highpower_landing_array",batch=BATCH,timeoutTicks=70)
 public static void fractional_array_shares_survive_ordinary_husk_armor(GameTestHelper h){
  var f=new HighPowerMultidimensionalLandingArrayRuntimeTests.Fixture(h,1,true,14.5,18.5);var target=f.targets.getFirst();ordinaryHusk(target);var hit=f.observe(target);BigDecimal before=total(target);
  BigDecimal whole=HighPowerGrowth.values(f.p).damage().multiply(new BigDecimal("4")).min(CombatAmounts.CAP);
  f.begin(()->f.post(16,()->{BigDecimal loss=before.subtract(total(target));
   h.assertTrue(hit.posts>0&&loss.signum()>0,"actual original needles pass native armored damage instead of silently rejecting precision");
   h.assertTrue(loss.compareTo(whole.multiply(new BigDecimal("0.984")))<=0,"all accepted contacts remain within their armor-reduced whole budget");
   h.assertTrue(!target.isNoGravity()&&target.getArmorValue()==2,"ordinary husk gravity and armor remain active");MultidimensionalLandingArray.cancelActive(f.p);
   h.assertTrue(f.needles()+BorrowedNeedleEscrow.retainedCount(f.p)==6,"terminating an armored volley preserves all six originals");}));f.finish(48);
 }
}
