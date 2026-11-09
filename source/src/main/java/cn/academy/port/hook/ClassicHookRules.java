/* AcademyCraft1.0.7 EntityMagHook + LambdaLib1.2.3 Rigidbody. GPLv3/MIT; see NOTICE. */
package cn.academy.port.hook;

/** Source math and decisions, independent of Minecraft and client bootstrap. */
public final class ClassicHookRules {
    public static final double SPEED=2, GRAVITY=.05, FACE_OFFSET=.51;
    public static final float DAMAGE=4, FLIGHT_SIZE=.5F, ANCHOR_SIZE=1F;
    public record Vector(double x,double y,double z) {
        public boolean finite(){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);}
    }
    public record Anchor(Vector position,float yaw,float pitch) {}
    /** Rigidbody traces old velocity, then subtracts gravity, then advances that new velocity. */
    public static Vector afterGravity(Vector velocity){return new Vector(velocity.x,velocity.y-GRAVITY,velocity.z);}
    public static Vector advance(Vector position,Vector velocity){return new Vector(position.x+velocity.x,position.y+velocity.y,position.z+velocity.z);}
    public static Anchor anchor(int x,int y,int z,int side,float oldYaw){
        return switch(side){
            case 0 -> new Anchor(new Vector(x+.5,y+.5-FACE_OFFSET,z+.5),oldYaw,-90);
            case 1 -> new Anchor(new Vector(x+.5,y+.5+FACE_OFFSET,z+.5),oldYaw,90);
            case 2 -> new Anchor(new Vector(x+.5,y+.5,z+.5-FACE_OFFSET),0,0);
            case 3 -> new Anchor(new Vector(x+.5,y+.5,z+.5+FACE_OFFSET),180,0);
            case 4 -> new Anchor(new Vector(x+.5-FACE_OFFSET,y+.5,z+.5),-90,0);
            case 5 -> new Anchor(new Vector(x+.5+FACE_OFFSET,y+.5,z+.5),90,0);
            default -> throw new IllegalArgumentException("Invalid classic hook face "+side);
        };
    }
    public static boolean validSide(int side){return side>=0&&side<6;}
    public static byte packed(boolean hit,int side){return(byte)((hit?1:0)|(side<<1));}
    public static boolean hit(byte packed){return(packed&1)!=0;}
    public static int side(byte packed){return packed>>1;}
    public static boolean returnOnImpact(boolean otherHook,boolean otherAnchored){return !otherHook||otherAnchored;}
    public static float throwPitch(float random){return .4F/(random*.4F+.8F);}
    private ClassicHookRules(){}
}
