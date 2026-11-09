package cn.academy.port.worldboss;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Authored demo terrain/weather, with independent native undo ownership for each boss.
 * Combat, authored crack/ring/sound/shake and all client camera work stay with their directors.
 * This class never applies scripted damage, ignition, direct velocity or global gamerule edits.
 */
@EventBusSubscriber(modid="academy")
public final class WorldBossEnvironment {
    public static final int MAX_CHANGED=60000;
    private static ServerLevel server(WorldBossContent.Boss boss){return boss.level() instanceof ServerLevel level?level:null;}
    private static void set(WorldBossContent.Boss boss,BlockPos pos,BlockState state){
        ServerLevel level=server(boss);if(level!=null)WorldBossEnvironmentData.get(level).change(level,boss.getUUID(),pos,state);
    }
    private static int surface(ServerLevel level,int x,int z){
        BlockPos pos=new BlockPos(x,level.getMinBuildHeight(),z);
        return level.hasChunkAt(pos)?level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z):Integer.MIN_VALUE;
    }
    /** Idempotent native-spawn entry. A reloaded persisted lease never excavates a second pit. */
    public static void begin(WorldBossContent.Boss boss){
        ServerLevel level=server(boss);if(level==null||!boss.isAlive()||boss.isRemoved())return;
        var data=WorldBossEnvironmentData.get(level);var owner=data.owner(boss.getUUID());if(owner.pit)return;
        owner.pit=true;data.setDirty();pit(boss);
    }
    private static void pit(WorldBossContent.Boss boss){
        int r=14,depth=17,x0=(int)Math.floor(boss.getX()),y0=(int)Math.floor(boss.getY()),z0=(int)Math.floor(boss.getZ());
        var random=boss.getRandom();
        for(int dx=-17;dx<=17;dx++)for(int dz=-17;dz<=17;dz++){
            double distance=Math.sqrt(dx*dx+dz*dz);if(distance>17)continue;int x=x0+dx,z=z0+dz;
            if(distance<=r){
                int bottom=y0-depth+(int)(distance/r*5);
                for(int y=y0+1;y>=bottom;y--)set(boss,new BlockPos(x,y,z),y==bottom?(random.nextInt(3)==0?Blocks.SHROOMLIGHT:Blocks.MAGMA_BLOCK).defaultBlockState():Blocks.AIR.defaultBlockState());
                for(int y=y0+30;y>y0+1;y--)set(boss,new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
            }else{
                var pos=new BlockPos(x,y0-1,z);double q=random.nextDouble();set(boss,pos,(q<.4?Blocks.BLACKSTONE:q<.7?Blocks.BASALT:Blocks.MAGMA_BLOCK).defaultBlockState());
                if(random.nextDouble()<.04)fire(boss,pos.above());
            }
        }
    }
    private static void fire(WorldBossContent.Boss boss,BlockPos pos){
        ServerLevel level=server(boss);if(level!=null&&level.hasChunkAt(pos)&&level.getBlockState(pos).isAir())set(boss,pos,Blocks.FIRE.defaultBlockState());
    }
    private static void scorch(WorldBossContent.Boss boss,double cx,double cz,int radius,double density){
        ServerLevel level=server(boss);if(level==null)return;var random=boss.getRandom();
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            double distance=Math.sqrt(dx*dx+dz*dz);if(distance>radius||random.nextDouble()>density*(1-distance/radius*.7))continue;
            int x=(int)cx+dx,z=(int)cz+dz,y=surface(level,x,z);if(y==Integer.MIN_VALUE)continue;
            var pos=new BlockPos(x,y-1,z);var state=level.getBlockState(pos);
            if(state.isAir()||state.is(Blocks.BEDROCK)||!state.getFluidState().isEmpty())continue;
            double q=random.nextDouble();set(boss,pos,(q<.34?Blocks.MAGMA_BLOCK:q<.62?Blocks.BLACKSTONE:q<.84?Blocks.BASALT:Blocks.NETHERRACK).defaultBlockState());
            if(random.nextDouble()<.035)fire(boss,pos.above());
        }
    }
    private static void storm(WorldBossContent.Boss boss){
        ServerLevel level=server(boss);if(level!=null)WorldBossEnvironmentData.get(level).storm(level,boss.getUUID(),boss.castSequence());
    }
    private static void fx(ServerLevel level,String kind,Vec3 pos,float a,float b,float c,int i,int j){
        PacketDistributor.sendToPlayersNear(level,null,pos.x,pos.y,pos.z,420,new FxPacket(kind,(float)pos.x,(float)pos.y,(float)pos.z,a,b,c,i,j));
    }
    private static void bolt(WorldBossContent.Boss boss,Vec3 point,boolean flash){
        ServerLevel level=server(boss);if(level==null)return;
        int y=surface(level,(int)point.x,(int)point.z);if(y==Integer.MIN_VALUE)return;
        var lightning=EntityType.LIGHTNING_BOLT.create(level);if(lightning==null)return;
        lightning.moveTo(point.x,y,point.z);lightning.setVisualOnly(true);level.addFreshEntity(lightning);
        var random=boss.getRandom();fx(level,"bolt",new Vec3(point.x,y,point.z),(float)(point.x+(random.nextDouble()-.5)*10),y+190,(float)(point.z+(random.nextDouble()-.5)*10),flash?1:0,0);
        WorldBossPresentation.sound(level,"thunder",point.x,y,point.z,14,.9F+random.nextFloat()*.3F);
        WorldBossPresentation.shake(level,point.x,y,point.z,.38F,10,160);
    }
    private static void spikes(WorldBossContent.Boss boss,int minimum,int maximum){
        ServerLevel level=server(boss);if(level==null)return;var random=boss.getRandom();
        for(int i=0;i<26;i++){
            double angle=random.nextDouble()*Math.PI*2,radius=minimum+random.nextDouble()*(maximum-minimum);
            int x=(int)(boss.getX()+Math.cos(angle)*radius),z=(int)(boss.getZ()+Math.sin(angle)*radius),y=surface(level,x,z),height=3+random.nextInt(6);
            if(y==Integer.MIN_VALUE)continue;
            for(int k=0;k<height;k++)set(boss,new BlockPos(x,y+k,z),(k==height-1?Blocks.MAGMA_BLOCK:k%2==0?Blocks.BLACKSTONE:Blocks.BASALT).defaultBlockState());
            fx(level,"spike",new Vec3(x+.5,y,z+.5),height,0,0,24,0);
        }
    }
    /** Invoked only for real authored animation events by WorldBossCombat. */
    public static void event(WorldBossContent.Boss boss,String type,int age){
        ServerLevel level=server(boss);if(level==null||!boss.isAlive()||boss.isRemoved())return;
        WorldBossEnvironmentData.get(level).owner(boss.getUUID());var random=boss.getRandom();double x=boss.getX(),z=boss.getZ();
        switch(type){
            case "storm"->storm(boss);
            case "lightning"->bolt(boss,boss.local((random.nextDouble()-.5)*50,0,-20-random.nextDouble()*40),true);
            case "ram"->{var p=boss.local(0,8,-23);scorch(boss,p.x,p.z,9,.3);}
            case "tail_quake"->{var p=boss.local(0,0,28);scorch(boss,p.x,p.z,14,.3);}
            case "stomp"->{var p=boss.local(5.1,0,0);scorch(boss,p.x,p.z,18,.3);}
            case "quake"->scorch(boss,x,z,20,.3);
            case "eruption"->{spikes(boss,14,32);scorch(boss,x,z,24,.3);}
            case "nova"->{
                for(int i=0;i<8;i++){double angle=i*Math.PI/4;bolt(boss,new Vec3(x+Math.cos(angle)*(18+random.nextInt(14)),boss.getY(),z+Math.sin(angle)*(18+random.nextInt(14))),i==0);}
                scorch(boss,x,z,26,.3);
            }
            case "cataclysm"->{
                storm(boss);spikes(boss,18,44);scorch(boss,x,z,30,.3);
                for(int i=0;i<10;i++)bolt(boss,new Vec3(x+(random.nextDouble()-.5)*110,boss.getY(),z+(random.nextDouble()-.5)*110),false);
            }
            case "roar"->{if(boss.clip()==Clips.SPAWN||boss.clip()==Clips.WORLDBREAKER)storm(boss);}
            default->{}
        }
    }
    /** Same authored every-other-tick breath/solar-lance ground scorch, no extra DOT. */
    public static void continuous(WorldBossContent.Boss boss,int age){
        ServerLevel level=server(boss);if(level==null||!boss.isAlive()||boss.isRemoved())return;
        int clip=boss.clip();boolean breath=clip==Clips.BREATH&&age>=46&&age<102,beam=clip==Clips.LANCE&&age>=57&&age<107;
        if((!breath&&!beam)||age%2!=0)return;
        var random=boss.getRandom();Vec3 mouth=WorldBossCombat.mouth(boss),aim=boss.attackAim().subtract(mouth);
        Vec3 forward=new Vec3(aim.x,0,aim.z);forward=forward.lengthSqr()>1.0e-8?forward.normalize():boss.forward();double length=breath?46:90;
        WorldBossEnvironmentData.get(level).owner(boss.getUUID());
        for(int s=6;s<length;s+=breath?5:4){
            double width=breath?s*.28:2;
            int x=(int)(mouth.x+forward.x*s+(random.nextDouble()-.5)*width*2),z=(int)(mouth.z+forward.z*s+(random.nextDouble()-.5)*width*2),y=surface(level,x,z);
            if(y==Integer.MIN_VALUE)continue;var pos=new BlockPos(x,y-1,z);
            if(random.nextDouble()<.5&&!level.getBlockState(pos).isAir()){
                set(boss,pos,(beam?Blocks.BLACKSTONE:Blocks.MAGMA_BLOCK).defaultBlockState());if(random.nextDouble()<.12)fire(boss,pos.above());
            }
        }
    }
    public static void meteorImpact(WorldBossContent.Boss boss,Vec3 position){
        if(server(boss)!=null&&boss.isAlive()&&!boss.isRemoved())scorch(boss,position.x,position.z,7,.3);
    }
    /** Idempotent explicit death/removal release; other bosses' layers and weather remain. */
    public static void release(WorldBossContent.Boss boss){
        ServerLevel level=server(boss);if(level==null)return;var data=WorldBossEnvironmentData.existing(level);if(data!=null)data.release(level,boss.getUUID());
    }
    public static int changedBlockCount(WorldBossContent.Boss boss){
        ServerLevel level=server(boss);if(level==null)return 0;var data=WorldBossEnvironmentData.existing(level);if(data==null)return 0;
        UUID id=boss.getUUID();return (int)data.cells.values().stream().filter(cell->cell.layers.containsKey(id)).count();
    }
    public static int activeBossCount(ServerLevel level){var data=WorldBossEnvironmentData.existing(level);return data==null?0:data.owners.size();}
    public static int weatherOwnerCount(ServerLevel level){var data=WorldBossEnvironmentData.existing(level);return data==null?0:data.weatherOwners.size();}
    @SubscribeEvent public static void beforeTick(ServerTickEvent.Pre event){for(ServerLevel level:event.getServer().getAllLevels()){var data=WorldBossEnvironmentData.existing(level);if(data!=null)data.tick(level);}}
    private WorldBossEnvironment(){}
}
