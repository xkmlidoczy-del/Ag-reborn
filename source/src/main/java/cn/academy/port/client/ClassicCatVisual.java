/* AcademyCraft1.0.7 RenderCatEngine geometry/time contract. GPLv3. */
package cn.academy.port.client;
public final class ClassicCatVisual {
    private ClassicCatVisual(){}
    public record Point(double x,double y,double z){}
    public static double rotation(double previous,long lastRender,long now,double generation){return lastRender==0?previous:(previous+(now-lastRender)*generation*.01)%360;}
    public static double bob(long now){return .03*Math.sin(now*.006);}
    public static double yaw(double cameraRelativeX,double cameraRelativeZ){return Math.toDegrees(Math.atan2(cameraRelativeX+.5,cameraRelativeZ+.5))+180;}
    public static Point vertex(double u,double v,double rotation,long time,double x,double z){
        double pitch=Math.toRadians(rotation),heading=Math.toRadians(yaw(x,z)),cx=u-.5,cy=(v-.5)*Math.cos(pitch),cz=(v-.5)*Math.sin(pitch);
        return new Point(x+.5+cx*Math.cos(heading)+cz*Math.sin(heading),bob(time)+.5+cy,z+.5-cx*Math.sin(heading)+cz*Math.cos(heading));
    }
}
