package oracle;

import cn.academy.port.hook.ClassicHookRules;
import cn.academy.port.client.ClassicHookTransform;
import cn.academy.port.client.ClassicPortableTransform;
import cn.academy.vanilla.electromaster.entity.EntityMagHook;
import cn.academy.vanilla.electromaster.item.ItemMagHook;
import cn.academy.vanilla.electromaster.client.renderer.RendererMagHook;
import cn.lambdalib.util.entityx.handlers.Rigidbody;
import net.minecraft.world.World;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraftforge.client.IItemRenderer.ItemRenderType;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import java.util.Random;

/** Executes unchanged Hook, Motion3D, Rigidbody, EntityX, item and renderer implementations.
 * Old external world/ray API is a programmable shim, not a second hook implementation. */
public final class SourceOracle {
    static String lastModel;static Matrix4f lastMatrix;static int assertions;
    public static void recordModel(String name){lastModel=name;lastMatrix=new Matrix4f(GL11.matrix);}
    static void check(boolean b,String label){assertions++;if(!b)throw new AssertionError(label);}
    static void close(double a,double b,String label){check(Math.abs(a-b)<1E-8,label+": "+a+" != "+b);}
    static void matrix(Matrix4f a,Matrix4f b,String label){check(a.equals(b,1E-6F),label+"\n"+a+"\n"+b);}
    static EntityPlayer player(World w){var p=new EntityPlayer(w);p.setPosition(4,7,-3);p.rotationYaw=31;p.rotationPitch=-23;return p;}
    static ClassicHookRules.Vector vector(Entity e){return new ClassicHookRules.Vector(e.posX,e.posY,e.posZ);}
    static ClassicHookRules.Vector velocity(Entity e){return new ClassicHookRules.Vector(e.motionX,e.motionY,e.motionZ);}
    static void position(Entity e,ClassicHookRules.Vector p,String label){close(e.posX,p.x(),label+"x");close(e.posY,p.y(),label+"y");close(e.posZ,p.z(),label+"z");}
    public static void main(String[] args){
        var rng=new Random(214);for(int scenario=0;scenario<100;scenario++){
            var world=new World();var p=player(world);p.rotationYaw=rng.nextFloat()*720-360;p.rotationPitch=rng.nextFloat()*180-90;var hook=new EntityMagHook(p);
            close(Math.sqrt(hook.motionX*hook.motionX+hook.motionY*hook.motionY+hook.motionZ*hook.motionZ),2,"source normalized launch speed");close(hook.posY,p.posY+p.getEyeHeight(),"source eye origin");
            float yaw=hook.rotationYaw,pitch=hook.rotationPitch;
            for(int tick=0;tick<20;tick++){
                var old=vector(hook);var velocity=velocity(hook);var next=ClassicHookRules.afterGravity(velocity);hook.onUpdate();
                position(hook,ClassicHookRules.advance(old,next),"source Rigidbody advance");close(hook.motionY,next.y(),"source Rigidbody gravity");close(world.lastTraceEnd.yCoord,old.y()+velocity.y(),"trace uses old velocity");close(hook.rotationYaw,yaw,"source flight yaw stays initial");close(hook.rotationPitch,pitch,"source flight pitch stays initial");
            }
        }
        for(int side=0;side<6;side++){
            var world=new World();var p=player(world);var hook=new EntityMagHook(p);var old=vector(hook);var vel=velocity(hook);float yaw=hook.rotationYaw;
            world.nextHit=new MovingObjectPosition(side,-12,34,56);hook.onUpdate();check(hook.isHit,"hit bit set immediately");close(hook.width,.5,"hit tick retains .5 width");position(hook,ClassicHookRules.advance(old,ClassicHookRules.afterGravity(vel)),"hit tick still advances");check(hook.canBeCollidedWith(),"source hit pickable before still tick");
            hook.onUpdate();close(hook.width,1,"next tick expands to1");close(hook.motionX,0,"next tick stops");var anchor=ClassicHookRules.anchor(-12,34,56,side,yaw);position(hook,anchor.position(),"six-side source anchor");close(hook.rotationYaw,anchor.yaw(),"anchor yaw including retained vertical yaw");close(hook.rotationPitch,anchor.pitch(),"anchor pitch");
            byte state=hook.dataWatcher.getWatchableObjectByte(10);check(state==ClassicHookRules.packed(true,side),"source packed sync");
            NBTTagCompound nbt=new NBTTagCompound();hook.writeEntityToNBT(nbt);var reload=new EntityMagHook(world);reload.readEntityFromNBT(nbt);reload.rotationYaw=yaw;reload.onUpdate();position(reload,anchor.position(),"source NBT anchor");check(reload.isHit&&reload.hitSide==side,"source NBT fields");
            var clientWorld=new World();clientWorld.isRemote=true;var client=new EntityMagHook(clientWorld);client.dataWatcher.updateObject(10,state);client.dataWatcher.updateObject(11,-12);client.dataWatcher.updateObject(12,34);client.dataWatcher.updateObject(13,56);client.onUpdate();check(client.isHit,"client watcher learns hit");close(client.width,.5,"client sync schedules still");client.onUpdate();close(client.width,1,"client next tick sets still");
            hook.onCollideWithPlayer(p);check(!hook.isDead,"no automatic pickup");hook.attackEntityFrom(new DamageSource(new Entity(world)),100);check(!hook.isDead,"environment/nonplayer damage does not retrieve");hook.attackEntityFrom(DamageSource.causePlayerDamage(player(world)),0);check(hook.isDead,"any player attack retrieves even zero damage");check(world.spawned.stream().filter(EntityItem.class::isInstance).count()==1,"one source retrieval item");
        }
        var world=new World();var p=player(world);var hook=new EntityMagHook(p);var victim=new Entity(world);var start=vector(hook);world.nextHit=new MovingObjectPosition(victim);hook.onUpdate();close(victim.receivedDamage,4,"source nonhook damage4");check(victim.receivedSource.getEntity()==p,"source player-attributed damage");check(hook.isDead,"impact returns");var drop=(EntityItem)world.spawned.getFirst();position(drop,start,"impact drop is old position");
        world=new World();p=player(world);hook=new EntityMagHook(p);world.nextHit=new MovingObjectPosition(p);hook.onUpdate();check(!hook.isDead,"owner permanently excluded");
        var flying=new EntityMagHook(world);world.nextHit=new MovingObjectPosition(flying);hook.onUpdate();check(!hook.isDead&&!flying.isDead,"flying hook impact ignored");flying.isHit=true;world.nextHit=new MovingObjectPosition(flying);hook.onUpdate();check(hook.isDead&&!flying.isDead,"anchored hook returns incoming hook only");close(flying.receivedDamage,0,"hooks never impact-damaged");
        world=new World();hook=new EntityMagHook(player(world));world.nextHit=new MovingObjectPosition(1,0,0,0);hook.onUpdate();hook.onUpdate();check(!hook.isDead,"non-air host retains hook");world.hostAir=true;hook.onUpdate();check(hook.isDead,"host-air drops item");
        var item=new ItemMagHook();world=new World();p=player(world);var stack=new ItemStack(item);stack.stackSize=8;item.onItemRightClick(stack,world,p);check(stack.stackSize==7&&world.spawned.size()==1&&world.sounds==1,"source item actual throw consumes/sounds/spawns");p.capabilities.isCreativeMode=true;item.onItemRightClick(stack,world,p);check(stack.stackSize==7,"creative throw retains stack");world.isRemote=true;int spawns=world.spawned.size();item.onItemRightClick(stack,world,p);check(stack.stackSize==7&&world.spawned.size()==spawns,"client use predicts without spawn/debit");
        // Execute unsafe original edge cases separately; the port intentionally repairs these.
        world=new World();p=player(world);hook=new EntityMagHook(p);world.nextHit=new MovingObjectPosition(1,0,0,0);hook.onUpdate();hook.onUpdate();hook.attackEntityFrom(DamageSource.causePlayerDamage(p),1);hook.attackEntityFrom(DamageSource.causePlayerDamage(p),1);check(world.spawned.size()==2,"confirmed original repeated-punch double-drop quirk repaired by port");
        world=new World();world.spawnAccepted=false;p=player(world);stack=new ItemStack(item);stack.stackSize=2;item.onItemRightClick(stack,world,p);check(stack.stackSize==1,"confirmed source consumes despite cancelled spawn; repaired by port");
        hook=new EntityMagHook(p);hook.isHit=true;hook.attackEntityFrom(DamageSource.causePlayerDamage(p),1);check(hook.isDead,"confirmed source discards despite cancelled refund; repaired by port");
        world=new World();var reloadedFlight=new EntityMagHook(world);world.nextHit=new MovingObjectPosition(1,0,0,0);reloadedFlight.onUpdate();check(!reloadedFlight.isHit,"confirmed source world-constructor flight has no collide callback; repaired by port");
        visualOracle(item,stack,p);
        System.out.println("PASS unchanged-original Hook differential: "+assertions+" assertions; 2000 physics updates, six faces, lifecycle/item/sync/NBT and exact item/entity GL matrices");
    }
    static void visualOracle(ItemMagHook item,ItemStack stack,EntityPlayer p){
        for(boolean groundFirst:new boolean[]{false,true}){
            var renderer=new ItemMagHook.HookRender();var state=new ClassicHookTransform.ItemState();check(!renderer.handleRenderType(stack,ItemRenderType.INVENTORY),"source GUI uses icon");
            if(groundFirst){GL11.reset();renderer.renderItem(ItemRenderType.ENTITY,stack,null,new EntityItem(p.worldObj,0,0,0,stack));matrix(lastMatrix,state.render(ClassicPortableTransform.Context.GROUND,false),"ground-first source matrix");}
            for(int i=0;i<2;i++){GL11.reset();renderer.renderItem(ItemRenderType.EQUIPPED_FIRST_PERSON,stack,null,p);matrix(lastMatrix,state.render(ClassicPortableTransform.Context.FIRST_PERSON,false),"first/subsequent equip offsets and item-local matrix");check(lastModel.equals("maghook"),"item closed mesh");}
            GL11.reset();renderer.renderItem(ItemRenderType.EQUIPPED,stack,null,p);matrix(lastMatrix,state.render(ClassicPortableTransform.Context.THIRD_PERSON,false),"source third person local matrix");
        }
        var renderer=new RendererMagHook();var hook=new EntityMagHook(p);GL11.reset();renderer.doRender(hook,0,0,0,0,0);matrix(lastMatrix,ClassicHookTransform.entity(hook.rotationYaw,hook.rotationPitch),"source closed entity transform .0054");check(lastModel.equals("maghook"),"flight closed mesh");
        for(int side=0;side<6;side++){
            hook.isHit=true;hook.hitSide=side;hook.hookX=2;hook.hookY=3;hook.hookZ=4;GL11.reset();renderer.doRender(hook,-999,-999,-999,0,0);
            var expected=new Matrix4f().translate((float)hook.posX,(float)hook.posY,(float)hook.posZ).mul(ClassicHookTransform.entity(hook.rotationYaw,hook.rotationPitch));matrix(lastMatrix,expected,"source snapped open entity transform");check(lastModel.equals("maghook_open"),"anchored open mesh");
        }
    }
}
