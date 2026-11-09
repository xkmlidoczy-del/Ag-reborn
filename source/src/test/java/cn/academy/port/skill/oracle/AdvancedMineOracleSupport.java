/* Test-only Minecraft 1.7 / LambdaLib contract stubs. Not production code or a GL/pixel oracle.
 * Motion3D/RandUtils/MdParticleFactory/EntityRayBase/RendererRayComposite run as unchanged upstream
 * class bodies next to this file. LambdaLib/Minecraft public boundary semantics are approximated
 * only where expressly documented below. See classic-oracles/advanced-mining/NOTICE.txt. */
package cn.academy.port.skill.oracle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AdvancedMineOracleSupport {
    private AdvancedMineOracleSupport() {}
    public enum Side { CLIENT, SERVER }
    public @interface SideOnly { Side value(); }
    public @interface Registrant {}
    public @interface RegEntity {
        boolean clientOnly() default false;
        @interface HasRender {}
        @interface Render {}
    }
    public interface IAssociatePlayer { EntityPlayer getPlayer(); }
    public static final class ResourceLocation {
        public final String location;
        public ResourceLocation(String location) { this.location=location; }
        @Override public String toString() { return location; }
    }
    public static final class Resources {
        public static ResourceLocation getTexture(String name) {
            return new ResourceLocation("academy:textures/"+name+".png");
        }
    }
    public static final class GameTimer {
        public static long time=1;
        public static long getTime() { return time; }
    }
    public static final class World {
        public final boolean isRemote=true;
        public final List<Entity> spawned=new ArrayList<>();
        public final List<String> renderCalls=new ArrayList<>();
        public boolean spawnEntityInWorld(Entity entity) { spawned.add(entity); return true; }
    }
    public static class Entity {
        public World worldObj;
        public double posX,posY,posZ,motionX,motionY,motionZ;
        public float rotationYaw,rotationPitch,prevRotationYaw,prevRotationPitch;
        public float height=1.8F;
        public boolean ignoreFrustumCheck,dead;
        public Entity(World world) { worldObj=world; }
        public void setPosition(double x,double y,double z) { posX=x;posY=y;posZ=z; }
        public float getEyeHeight() { return height*.85F; }
        public float getRotationYawHead() { return rotationYaw; }
        public void setDead() { dead=true; }
        public void onUpdate() {}
        public boolean shouldRenderInPass(int pass) { return false; }
        protected void readEntityFromNBT(NBTTagCompound tag) {}
        protected void writeEntityToNBT(NBTTagCompound tag) {}
    }
    public static class EntityLivingBase extends Entity {
        public float rotationYawHead;
        public EntityLivingBase(World world) { super(world); }
        @Override public float getRotationYawHead() { return rotationYawHead; }
    }
    public static final class EntityPlayer extends EntityLivingBase {
        public float eye=1.62F;
        public EntityPlayer(World world,double x,double y,double z) { super(world);setPosition(x,y,z); }
        @Override public float getEyeHeight() { return eye; }
    }
    public interface EntityCallback { void execute(Entity target); }
    /** Lifecycle scheduler records original executeAfter calls, without pretending to run EntityX. */
    public static class EntityAdvanced extends Entity {
        public int ticks,scheduledLife=-1;
        public EntityCallback scheduled;
        public EntityAdvanced(World world) { super(world); }
        protected void onFirstUpdate() {}
        public void executeAfter(EntityCallback callback,int ticks) { scheduled=callback;scheduledLife=ticks; }
        @Override public void onUpdate() { if(ticks++==0)onFirstUpdate(); }
    }
    public static final class NBTTagCompound {
        private final Map<String,Double> values=new HashMap<>();
        public double getDouble(String key) { return values.getOrDefault(key,0D); }
        public void setDouble(String key,double value) { values.put(key,value); }
    }
    public static final class Minecraft {
        private static final Minecraft INSTANCE=new Minecraft();
        public EntityPlayer thePlayer;
        public static Minecraft getMinecraft() { return INSTANCE; }
    }
    public static final class ACRenderingHelper {
        public static boolean isThePlayer(EntityPlayer player) { return player.equals(Minecraft.getMinecraft().thePlayer); }
    }
    /** Minecraft 1.7 MathHelper's public lookup-table contract; no private game source is included. */
    public static final class MathHelper {
        private static final float[] SIN=new float[65536];
        static { for(int i=0;i<SIN.length;i++)SIN[i]=(float)Math.sin(i*Math.PI*2/65536); }
        public static float sin(float value) { return SIN[(int)(value*10430.378F)&65535]; }
        public static float cos(float value) { return SIN[(int)(value*10430.378F+16384F)&65535]; }
    }
    public static final class Vec3 {
        public double xCoord,yCoord,zCoord;
        public Vec3(double x,double y,double z) { xCoord=x;yCoord=y;zCoord=z; }
        public static Vec3 createVectorHelper(double x,double y,double z) { return new Vec3(x,y,z); }
        @Override public String toString() { return xCoord+","+yCoord+","+zCoord; }
    }
    public static final class VecUtils {
        public static Vec3 vec(double x,double y,double z) { return Vec3.createVectorHelper(x,y,z); }
    }
    /** Only Motion3D.toString()'s harmless Guava formatting boundary is stubbed. */
    public static final class Objects {
        public static Helper toStringHelper(Object object) { return new Helper(object.getClass().getSimpleName()); }
        public static final class Helper {
            private final StringBuilder value;
            Helper(String name) { value=new StringBuilder(name); }
            public Helper add(String name,Object object) { value.append(' ').append(name).append(object);return this; }
            @Override public String toString() { return value.toString(); }
        }
    }
    public static final class Color {
        public double r=1,g=1,b=1,a=1;
        public void setColor4i(int r,int g,int b,int a) { this.r=r/255D;this.g=g/255D;this.b=b/255D;this.a=a/255D; }
    }
    public static final class Particle extends Entity {
        public ResourceLocation texture;
        public final Color color=new Color();
        public float size;
        public int fadeStart,fadeDuration;
        public Particle() { super(null); }
        public void fadeAfter(int start,int duration) { fadeStart=start;fadeDuration=duration; }
    }
    public interface ParticleDecorator { void decorate(Particle particle); }
    /** Simple fresh-particle creation boundary. Original MdParticleFactory decorators are executed. */
    public static class ParticleFactory {
        public final Particle template;
        private final List<ParticleDecorator> decorators=new ArrayList<>();
        public ParticleFactory(Particle template) { this.template=template; }
        public void addDecorator(ParticleDecorator decorator) { decorators.add(decorator); }
        public Particle next(World world,Vec3 position,Vec3 velocity) {
            Particle particle=new Particle();particle.worldObj=world;particle.texture=template.texture;
            particle.setPosition(position.xCoord,position.yCoord,position.zCoord);
            particle.motionX=velocity.xCoord;particle.motionY=velocity.yCoord;particle.motionZ=velocity.zCoord;
            for(ParticleDecorator decorator:decorators)decorator.decorate(particle);
            return particle;
        }
    }
    public static abstract class Render {
        public abstract void doRender(Entity entity,double x,double y,double z,float yaw,float partialTicks);
        protected abstract ResourceLocation getEntityTexture(Entity entity);
    }
    /** Rendering sinks observe the original composite's invocation order and settings; no GL draw. */
    public static final class RendererRayGlow extends Render {
        public double width=.9;
        public final Color color=new Color();
        public final String textureName;
        private RendererRayGlow(String name) { textureName=name; }
        public static RendererRayGlow createFromName(String name) { return new RendererRayGlow(name); }
        @Override public void doRender(Entity entity,double x,double y,double z,float yaw,float partialTicks) { entity.worldObj.renderCalls.add("glow:"+textureName); }
        @Override protected ResourceLocation getEntityTexture(Entity entity) { return null; }
    }
    public static final class RendererRayCylinder extends Render {
        public double width,headFix=1;
        public final Color color=new Color();
        public RendererRayCylinder(double width) { this.width=width; }
        @Override public void doRender(Entity entity,double x,double y,double z,float yaw,float partialTicks) { entity.worldObj.renderCalls.add("cylinder:"+width+":"+headFix); }
        @Override protected ResourceLocation getEntityTexture(Entity entity) { return null; }
    }
}
