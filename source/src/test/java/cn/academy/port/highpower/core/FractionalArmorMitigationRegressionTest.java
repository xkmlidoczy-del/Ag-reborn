package cn.academy.port.highpower.core;
import java.math.BigDecimal;
import java.math.RoundingMode;
/** Real three/six-way finite shares must remain usable against ordinary armor. */
public final class FractionalArmorMitigationRegressionTest {
 private static int checks;
 public static void main(String[] args){
  BigDecimal quantum=BigDecimal.ONE.scaleByPowerOfTen(-CombatAmounts.MAX_ABSOLUTE_SCALE);
  for(String budget:new String[]{"10000000","5000000000000000000","100000000000000000000"})for(int count:new int[]{3,6,7,16}){
   BigDecimal raw=new BigDecimal(budget).divide(BigDecimal.valueOf(count),256,RoundingMode.DOWN);
   BigDecimal oracle=raw.multiply(new BigDecimal("0.984"));
   BigDecimal result=DamageMitigation.mitigate(raw,new DefenseProfile(2,0,0,0));
   truth(result.signum()>0,"ordinary husk armor must not discard a valid immutable share");
   truth(result.compareTo(oracle)<=0&&oracle.subtract(result).compareTo(quantum)<0,"conservative loss is less than one supported decimal quantum");
   // Arithmetic ceiling fixture is independent of the default player balance.
   var before=new ExactCombatState(CombatAmounts.CAP,CombatAmounts.CAP,BigDecimal.ZERO,BigDecimal.ZERO,false);var hit=before.applyMitigatedDamage(result);
   truth(before.health().add(before.shield()).subtract(hit.state().health().add(hit.state().shield())).compareTo(result)==0,"the exact ledger debits the admitted result exactly once");
  }
  for(int armor=0;armor<=30;armor++)for(int resistance=0;resistance<=5;resistance++)for(int protection:new int[]{0,1,20}){
   BigDecimal raw=new BigDecimal("1.000000000000000000000000000000000000000000000000000000000000001").setScale(256);
   BigDecimal result=DamageMitigation.mitigate(raw,new DefenseProfile(armor,4,resistance,protection));
   truth(result.signum()>=0&&result.compareTo(raw)<=0,"finite fractional mitigation never amplifies or becomes invalid");
   CombatAmounts.requireAmount(result,"actual generated result");checks++;
  }
  truth(DamageMitigation.mitigate(quantum,new DefenseProfile(20,0,0,0)).signum()==0,"sub-quantum generated loss rounds toward zero without inventing damage");
  truth(DamageMitigation.mitigate(new BigDecimal("1e-100"),new DefenseProfile(20,4,0,0)).signum()>0,"supported tiny damage is preserved");
  reject(new BigDecimal("1e-257"));reject(new BigDecimal("-1"));reject(CombatAmounts.CAP.add(BigDecimal.ONE));
  System.out.println("PASS "+checks+" fractional armor boundary assertions");
 }
 private static void reject(BigDecimal raw){try{DamageMitigation.mitigate(raw,new DefenseProfile(2,0,0,0));throw new AssertionError("untrusted raw input must still fail closed");}catch(IllegalArgumentException expected){checks++;}}
 private static void truth(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
}
