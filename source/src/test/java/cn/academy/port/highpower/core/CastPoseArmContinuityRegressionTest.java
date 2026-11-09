package cn.academy.port.highpower.core;

import java.util.EnumSet;

/**
 * Projects the real vanilla empty-arm/sleeve end faces, rather than testing a copy of the hand coefficients.
 * Native basis: ItemInHandRenderer.renderPlayerArm, PlayerRenderer.renderHand and PlayerModel.createMesh.
 * renderItemInHand uses getFov(..., false): 70 degrees, or about 60 in water/lava. World FOV is irrelevant.
 * This is a finite geometry contract; native client images still decide the appearance of the actual skin/effect.
 */
public final class CastPoseArmContinuityRegressionTest {
    private static int checks;
    private static final EnumSet<CastPose.Archetype> REPAIRED = EnumSet.of(
            CastPose.Archetype.LIFT, CastPose.Archetype.GATHER, CastPose.Archetype.FLICK,
            CastPose.Archetype.CONSTRUCT_PULL, CastPose.Archetype.CONSTRUCT_STEER,
            CastPose.Archetype.CONSTRUCT_LAND, CastPose.Archetype.RAIL_DEPLOY,
            CastPose.Archetype.FIELD_TUNE, CastPose.Archetype.ACCELERATOR, CastPose.Archetype.CANOPY,
            CastPose.Archetype.PHASE_ROUTE, CastPose.Archetype.VECTOR_CAGE,
            CastPose.Archetype.LISTEN, CastPose.Archetype.GRID_DEPLOY);

    public static void main(String[] args) {
        require(CastPose.firstPersonLookLag(0,10,-10).equals(CastPose.LookLag.NONE),"no casting means no look compensation");
        require(CastPose.firstPersonLookLag(Double.NaN,10,10).equals(CastPose.LookLag.NONE),"invalid strength is silent");
        require(CastPose.firstPersonLookLag(1,Double.NaN,10).equals(CastPose.LookLag.NONE),"invalid lag is silent");
        for(double s:new double[]{0,.01,.1,.25,.5,.75,.9,.99,1}) {
            var lag=CastPose.firstPersonLookLag(s,10,-10);
            require(lag.pitch()<=0&&lag.pitch()>=-10&&lag.yaw()>=0&&lag.yaw()<=10,"bounded entry/exit look blend");
        }
        require(Math.abs(CastPose.firstPersonLookLag(1e-9,10,10).pitch())<1e-6,"no first/last-frame jump");
        // Concrete old-pose counterexamples reproduce the end face seen in the final20 native PNGs.
        for (var old : new CastPose.Hand[]{new CastPose.Hand(0,.2,-.18,26,16,8),
                new CastPose.Hand(-.15,.1,-.22,20,34,18), new CastPose.Hand(-.18,.12,-.22,18,26,-12),
                new CastPose.Hand(-.2,.12,-.24,20,42,-10)}) {
            require(fullyExposed(mul(hand(old), nativeArm(true, true)), true, true, 70), "old complete cap counterexample");
        }
        for (boolean right : new boolean[]{false,true}) for (boolean slim : new boolean[]{false,true}) {
            double[][] nativeArm = nativeArm(right,slim);
            require(!fullyExposed(nativeArm,right,slim,70), "vanilla shoulder stays outside normal view");
            for (var a : CastPose.Archetype.values()) {
                for (int i=0;i<=96;i++) inspect(a,i/4.0,-1,right,slim,nativeArm);
                for (double age : new double[]{30,40,60,100}) inspect(a,age,-1,right,slim,nativeArm);
                for (int i=0;i<=32;i++) inspect(a,30,i/4.0,right,slim,nativeArm);
                require(CastPose.hand(a,30,8,right).equals(CastPose.Hand.NONE), "full natural relaxation "+a);
            }
        }
        // At an extreme combined 20deg native lag, vanilla itself can show the complete cap.
        // No-cast and the exact zero-strength boundary intentionally remain native, rather than moving a hidden hand.
        require(fullyExposed(chain(r(0,20),r(1,-20),nativeArm(false,false)),false,false,70)
                ||fullyExposed(chain(r(0,20),r(1,-20),nativeArm(true,false)),true,false,70),"native extreme-look boundary counterexample");
        System.out.println("CastPoseArmContinuityRegressionTest PASS "+checks+" checks");
    }

    private static void inspect(CastPose.Archetype a,double age,double since,boolean right,boolean slim,double[][] nativeArm) {
        var h=CastPose.hand(a,age,since,right);
        for(double v:new double[]{h.x(),h.y(),h.z(),h.rotX(),h.rotY(),h.rotZ()}) require(Double.isFinite(v),"finite "+a);
        var mat=mul(hand(h),nativeArm);
        for(double fov:new double[]{60,70}) {
            // The native parent is Rx(pitch) * Ry(yaw); compensation MUST be Ry(-yaw) * Rx(-pitch).
            // A full gesture is stable, while its entry/exit smoothly restores normal native look lag.
            double strength=CastPose.envelope(a,age,since);
            double[] lags=strength==1?new double[]{-20,-10,0,10,20}:new double[]{-10,0,10};
            for(double pitch:lags) for(double yaw:lags) {
                var lag=CastPose.firstPersonLookLag(strength,pitch,yaw);
                var rendered=chain(r(0,pitch),r(1,yaw),r(1,lag.yaw()),r(0,lag.pitch()),mat);
                for(boolean sleeve:new boolean[]{false,true}) require(!fullyExposed(rendered,right,slim,fov,sleeve),
                        "complete shoulder cap "+a+" age="+age+" since="+since+" look="+pitch+","+yaw);
                if(Math.abs(pitch)==10&&Math.abs(yaw)==10) for(int phase=0;phase<8;phase++) {
                    var walking=mul(walkingBob(phase/8.0),rendered);
                    for(boolean sleeve:new boolean[]{false,true}) require(!fullyExposed(walking,right,slim,fov,sleeve),
                            "combined native walking/look "+a+" age="+age+" since="+since);
                }
            }
            if(!REPAIRED.contains(a)) continue;
            for(int phase=-1;phase<8;phase++) {
                var view=phase<0?identity():walkingBob(phase/8.0);
                var viewed=mul(view,mat);
                for(boolean sleeve:new boolean[]{false,true}) for(var p:endFace(right,slim,sleeve?-2.25:-2,sleeve?.25:0)) {
                    var screen=project(viewed,p,fov);
                    require(screen!=null&&screen[1]>720,"repaired shoulder remains cropped "+a+" age="+age+" since="+since);
                }
            }
            if(age>=8&&since<0) {
                var palm=project(mat,center(cap(right,slim,10.25)),fov);
                require(palm!=null&&palm[0]>0&&palm[0]<1280&&palm[1]>330&&palm[1]<680,"real hand remains visible "+a);
                require(Math.hypot(palm[0]-640,palm[1]-360)>70,"central target stays clear "+a);
                // The existing wrist proxy is shared with signature rendering. Keep a .14m ring intersecting the view.
                var wrist=CastPose.wristCamera(h,right);
                var upper=project(identity(),new double[]{wrist.x(),wrist.y()+.14,wrist.z()},fov);
                require(upper!=null&&upper[1]<720,"wrist decoration has visible room "+a+" age="+age+" right="+right+" fov="+fov+" y="+(upper==null?"behind":upper[1]));
            }
        }
    }
    private static boolean fullyExposed(double[][] m,boolean right,boolean slim,double fov) {
        return fullyExposed(m,right,slim,fov,true);
    }
    private static boolean fullyExposed(double[][] m,boolean right,boolean slim,double fov,boolean sleeve) {
        var cap=endFace(right,slim,sleeve?-2.25:-2,sleeve?.25:0);var c=point(m,center(cap));
        double facing=0;for(int i=0;i<3;i++)facing+=m[i][1]*c[i];
        if(facing<=0)return false;
        for(var p:cap){var s=project(m,p,fov);if(s==null||s[0]<=0||s[0]>=1280||s[1]<=0||s[1]>=720)return false;}
        return true;
    }
    private static double[][] nativeArm(boolean right,boolean slim) {
        double s=right?1:-1;
        // No equip lowering or attack swing: exact idle empty-main-hand branch, including native model age0 bob.
        return chain(t(s*.64000005,-.6,-.71999997),r(1,s*45),t(-s,3.6,3.5),r(2,s*120),r(0,200),
                // setupAnim resets both normal and slim arms to y=2 before sleeves copy the live arm pose.
                r(1,-s*135),t(s*5.6,0,0),t(-s*5/16,2.0/16,0),r(2,s*Math.toDegrees(.1)));
    }
    private static double[][] walkingBob(double phase) {
        double a=phase*2*Math.PI,b=.1;
        return chain(t(Math.sin(a)*b*.5,-Math.abs(Math.cos(a)*b),0),r(2,Math.sin(a)*b*3),r(0,Math.abs(Math.cos(a-.2)*b)*5));
    }
    private static double[][] hand(CastPose.Hand h){return chain(t(h.x(),h.y(),h.z()),r(0,h.rotX()),r(1,h.rotY()),r(2,h.rotZ()));}
    private static double[][] cap(boolean right,boolean slim,double y) {
        return endFace(right,slim,y,.25);
    }
    private static double[][] endFace(boolean right,boolean slim,double y,double expand) {
        double lo=slim?-2:-3,hi=1;if(!right){double v=lo;lo=-hi;hi=-v;}
        return new double[][]{{(lo-expand)/16,y/16,(-2-expand)/16},{(lo-expand)/16,y/16,(2+expand)/16},
                {(hi+expand)/16,y/16,(-2-expand)/16},{(hi+expand)/16,y/16,(2+expand)/16}};
    }
    private static double[] center(double[][] ps){double[] c=new double[3];for(var p:ps)for(int i=0;i<3;i++)c[i]+=p[i]/ps.length;return c;}
    private static double[] project(double[][] m,double[] p,double fov){var q=point(m,p);if(q[2]>=-.05)return null;double k=-q[2]*Math.tan(Math.toRadians(fov/2));return new double[]{640*(q[0]/k/(16.0/9)+1),360*(1-q[1]/k)};}
    private static double[] point(double[][] m,double[] p){double[] out=new double[3];for(int i=0;i<3;i++){out[i]=m[i][3];for(int j=0;j<3;j++)out[i]+=m[i][j]*p[j];}return out;}
    private static double[][] identity(){double[][] m=new double[4][4];for(int i=0;i<4;i++)m[i][i]=1;return m;}
    private static double[][] t(double x,double y,double z){var m=identity();m[0][3]=x;m[1][3]=y;m[2][3]=z;return m;}
    private static double[][] r(int axis,double angle){var m=identity();double c=Math.cos(Math.toRadians(angle)),s=Math.sin(Math.toRadians(angle));int i=axis==0?1:axis==1?2:0,j=axis==0?2:axis==1?0:1;m[i][i]=m[j][j]=c;m[i][j]=-s;m[j][i]=s;return m;}
    private static double[][] mul(double[][] a,double[][] b){double[][] out=new double[4][4];for(int i=0;i<4;i++)for(int j=0;j<4;j++)for(int k=0;k<4;k++)out[i][j]+=a[i][k]*b[k][j];return out;}
    private static double[][] chain(double[][]... ms){var out=identity();for(var m:ms)out=mul(out,m);return out;}
    private static void require(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
}
