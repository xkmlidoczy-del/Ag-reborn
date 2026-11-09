package cn.academy.port.highpower.core;
import java.math.BigDecimal;
import static cn.academy.port.highpower.core.DisplacedProjectionRules.*;
public final class DisplacedProjectionRulesRegressionTest {
    private static int checks;
    private static void require(boolean result,String message){checks++;if(!result)throw new AssertionError(message);}
    public static void main(String[] args){
        Point t=new Point(3,7,11);
        for(int yaw=-720;yaw<=720;yaw++){
            Point s=side(t,yaw);
            require(Math.abs(s.distance(t)-1.5)<1e-12,"fixed side distance");
            require(s.y()==t.y(),"fixed target-height plane");
            require(s.distance(side(t,yaw+360))<1e-12,"one exact orientation modulo360");
        }
        require(side(t,0).x()==1.5,"yaw0 right is minusX");
        require(side(t,90).z()==9.5,"yaw90 right is minusZ");
        require(side(t,Double.NaN)==null&&side(t,Double.POSITIVE_INFINITY)==null,"bad yaw refuses");
        require(side(new Point(30_000_000,0,0),180)==null,"side cannot cross world coordinate bound");
        Point a=new Point(0,0,0),b=new Point(RANGE-1.5,0,0),c=new Point(RANGE,0,0);
        require(path(a,b,c),"exact cumulative boundary");
        require(!path(a,b,new Point(Math.nextUp(RANGE),0,0)),"oneULP outside is not silently admitted");
        require(!path(a,new Point(RANGE,1,0),c),"caster emitter leg counts");
        require(!path(a,null,c)&&!path(a,new Point(Double.NaN,0,0),c),"nonfinite/refused geometry");
        require(damage(new BigDecimal("1e30")).compareTo(new BigDecimal("1e20"))==0,"final bound");
        require(damage(new BigDecimal("0.0000001")).compareTo(new BigDecimal("0.0000001"))==0,"no float reduction");
        require(damage(BigDecimal.ZERO).signum()==0&&damage(null).signum()==0,"no invalid output");
        double previous=cpCost(BigDecimal.ONE);
        for(int i=101;i<=500;i++){double next=cpCost(BigDecimal.valueOf(i,2));require(next<previous,"smooth declining CP");previous=next;}
        require(CAST_FENCE==8&&FLIGHT_TICKS==20,"physical fences survive LV5");
        require(cpCost(new BigDecimal("5"))==.05&&cooldownTicks(new BigDecimal("5"))==1,"bounded LV5 utility");
        System.out.println("DisplacedProjectionRulesRegressionTest passed "+checks+" checks");
    }
}
