package cn.academy.port.client;

import cn.academy.port.highpower.core.WindPressureLiftGeometry;
import cn.academy.port.highpower.core.WindPressureLiftRules.Velocity;

/** Proof of allowed native collision outcomes; actual block collision remains native engine code. */
public final class WindPressureLiftNativeSweepRegressionTest {
    private static int checks;
    private static void check(boolean value,String name) {checks++;if(!value)throw new AssertionError(name);}
    public static void main(String[] args) {
        var column=new WindPressureLiftGeometry.Frame(new WindPressureLiftGeometry.Point(0,64,0));
        var standing=new WindPressureLiftGeometry.Box(-.3,64,-.3,.3,65.8,.3);
        var downSide=new Velocity(.03,-.02,0);var groundClip=new Velocity(.03,0,0);
        check(!WindPressureLiftGeometry.containsSweep(column,standing,downSide),"raw downward floor displacement is outside column");
        var accepted=WindPressureLiftNativeSweep.accepted(downSide,groundClip,false);
        check(accepted!=null && accepted.equals(groundClip),"native ground side clip accepted");
        check(WindPressureLiftGeometry.containsSweep(column,standing,accepted),"ground side sweep stays in original fixed column");
        check(downSide.y()==-.02,"collision geometry does not change requested additive velocity");
        check(WindPressureLiftNativeSweep.accepted(downSide,groundClip,true)==null,"landing after airborne ends authorization");
        check(WindPressureLiftNativeSweep.accepted(new Velocity(.03,.1,0),new Velocity(.03,0,0),false)==null,"ceiling clipping refused");
        check(WindPressureLiftNativeSweep.accepted(downSide,new Velocity(0,0,0),false)==null,"horizontal wall clipping refused");
        check(WindPressureLiftNativeSweep.accepted(downSide,new Velocity(.03,.1,0),false)==null,"step-up correction refused");
        check(WindPressureLiftNativeSweep.accepted(downSide,new Velocity(.03,-.03,0),false)==null,"extra downward displacement refused");
        check(WindPressureLiftNativeSweep.accepted(downSide,new Velocity(Double.NaN,0,0),false)==null,"nonfinite native outcome refused");
        check(WindPressureLiftNativeSweep.accepted(downSide,new Velocity(.03,0,Double.MIN_VALUE),false)==null,"even tiny horizontal clipping is not a supported step");
        var raised=new WindPressureLiftGeometry.Box(-.3,68.2,-.3,.3,70,.3);
        var up=new Velocity(0,.1,0);
        check(WindPressureLiftNativeSweep.accepted(up,up,true)!=null,"unclipped native up keeps displacement");
        check(!WindPressureLiftGeometry.containsSweep(column,raised,up),"column ceiling denies unclipped ascent");
        var edge=new WindPressureLiftGeometry.Box(2.3,64,-.3,2.9,65.8,.3);
        check(!WindPressureLiftGeometry.containsSweep(column,edge,new Velocity(.18,0,0)),"circular perimeter remains bounded after floor clipping");
        check(WindPressureLiftNativeSweep.accepted(new Velocity(0,-.02,0),new Velocity(0,-.02,0),true)!=null,"native gentle descent allowed before landing");
        Object world=new Object(),player=new Object(),connection=new Object(),input=new Object(),pose=new Object();
        var position=new WindPressureLiftGeometry.Point(0,64,0);
        var boundary=new WindPressureLiftNativeSweep.Boundary(world,player,connection,1,input,pose,position,downSide,standing,standing,0,.1F,true,0,0,false,false);
        check(WindPressureLiftNativeSweep.unchanged(boundary,boundary),"unchanged collision callback can commit");
        var knockback=new WindPressureLiftNativeSweep.Boundary(world,player,connection,1,input,pose,position,new Velocity(.4,.3,0),standing,standing,0,.1F,true,0,0,false,false);
        check(!WindPressureLiftNativeSweep.unchanged(boundary,knockback),"callback knockback must not be overwritten");
        var teleported=new WindPressureLiftNativeSweep.Boundary(world,player,connection,1,input,pose,new WindPressureLiftGeometry.Point(.01,64,0),downSide,standing,standing,0,.1F,true,0,0,false,false);
        check(!WindPressureLiftNativeSweep.unchanged(boundary,teleported),"even small callback teleport prevents commit");
        var replacement=new WindPressureLiftNativeSweep.Boundary(world,new Object(),connection,1,input,pose,position,downSide,standing,standing,0,.1F,true,0,0,false,false);
        check(!WindPressureLiftNativeSweep.unchanged(boundary,replacement),"replaced entity identity prevents commit");
        var reload=new WindPressureLiftNativeSweep.Boundary(world,player,connection,2,input,pose,position,downSide,standing,standing,0,.1F,true,0,0,false,false);
        check(!WindPressureLiftNativeSweep.unchanged(boundary,reload),"reload generation prevents commit");
        var laterJump=new WindPressureLiftNativeSweep.Boundary(world,player,connection,1,input,pose,position,downSide,standing,standing,0,.1F,true,0,0,true,false);
        check(!WindPressureLiftNativeSweep.unchanged(boundary,laterJump),"callback jump prevents commit");
        var laterSpeed=new WindPressureLiftNativeSweep.Boundary(world,player,connection,1,input,pose,position,downSide,standing,standing,0,.13F,true,0,0,false,false);
        check(!WindPressureLiftNativeSweep.unchanged(boundary,laterSpeed),"callback speed mutation prevents stale native prediction");
        float nativeInput=1F*.98F;
        var ordinary=WindPressureLiftNativeSweep.project(new Velocity(0,.002,0),new Velocity(0,0,nativeInput),.02F,0,1);
        check(ordinary!=null && ordinary.y()==.002,"travel-boundary current small velocity is not clamped a second time");
        check(ordinary.z()==(double)nativeInput*.02F,"actual travel input is not scaled by .98 a second time");
        check(ordinary.z()!=(double)(nativeInput*.98F)*.02F,"double-scaled old input prediction is a destructive negative");
        var side=WindPressureLiftNativeSweep.project(new Velocity(0,-.02,.03),new Velocity(nativeInput,0,0),.02F,0,1);
        check(side!=null&&side.y()==-.02&&side.z()==.03&&side.x()==(double)nativeInput*.02F,"SIDE addition combines with ordinary actual strafe input");
        var sprint=WindPressureLiftNativeSweep.project(new Velocity(0,.1,0),new Velocity(0,0,nativeInput),.026F,0,1);
        check(sprint!=null&&sprint.z()==(double)nativeInput*.026F&&sprint.y()==.1,"actual final sprint acceleration used");
        var rotated=WindPressureLiftNativeSweep.project(new Velocity(.01,.1,.02),new Velocity(0,0,nativeInput),.02F,1,0);
        check(rotated!=null&&rotated.x()==.01-(double)nativeInput*.02F&&rotated.z()==.02,"native yaw rotation uses real argument");
        var diagonal=WindPressureLiftNativeSweep.project(new Velocity(0,0,0),new Velocity(nativeInput,0,nativeInput),.02F,0,1);
        check(diagonal!=null&&Math.abs(Math.hypot(diagonal.x(),diagonal.z())-.02F)<1e-15,"native diagonal input normalization retained");
        check(WindPressureLiftNativeSweep.project(new Velocity(0,0,0),new Velocity(0,Double.NaN,0),.02F,0,1)==null,"nonfinite actual travel refused");
        check(WindPressureLiftNativeSweep.project(new Velocity(0,0,0),new Velocity(0,0,0),.50001F,0,1)==null,"unsupported actual acceleration refused");
        System.out.println("PASS WindPressureLiftNativeSweepRegressionTest: "+checks+" checks");
    }
}
