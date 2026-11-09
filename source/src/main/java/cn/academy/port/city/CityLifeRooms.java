package cn.academy.port.city;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/** Furnished public ground floors and three distinctive station neighborhoods, generated once. */
@net.neoforged.fml.common.EventBusSubscriber(modid="academy")
public final class CityLifeRooms {
    record Column(Map<Integer,BlockState> blocks,int top,boolean clearSky) { BlockState block(int y){return clearSky&&y>5?Blocks.AIR.defaultBlockState():blocks.get(y);} }
    private record Notice(String first,String second,String third) {}
    private static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    private static final class Layout {
        static final Map<Long,Column> COLUMNS;
        static final Map<BlockPos,Notice> NOTICES=new HashMap<>();
        static final Map<BlockPos,String> BOOKS=new HashMap<>();
        static {
            var b=new Builder();library(b);clinic(b);stationService(b);shop(b,false);shop(b,true);
            for(int station=1;station<=3;station++)stationRoom(b,station);
            // Retain service spawning markers and clear the staff walking/interaction points.
            for(var site:CityServicePlan.SITES)if(b.blocks.containsKey(key(site.home().getX(),site.home().getZ()))) {
                for(var at:new BlockPos[]{site.home(),site.shelf(),site.waiting()})if(at!=null){b.put(at,Blocks.AIR.defaultBlockState());b.put(at.above(),Blocks.AIR.defaultBlockState());}
                b.put(site.home().below(),Blocks.CYAN_GLAZED_TERRACOTTA.defaultBlockState());
            }
            var out=new HashMap<Long,Column>();b.blocks.forEach((k,v)->out.put(k,new Column(Map.copyOf(v),v.keySet().stream().mapToInt(Integer::intValue).max().orElse(0),b.clearSky.contains(k))));COLUMNS=Map.copyOf(out);
        }
    }
    private static final class Builder {
        final Map<Long,Map<Integer,BlockState>> blocks=new HashMap<>();
        final java.util.Set<Long> clearSky=new java.util.HashSet<>();
        void put(BlockPos p,BlockState s){blocks.computeIfAbsent(key(p.getX(),p.getZ()),k->new HashMap<>()).put(p.getY(),s);}
        void at(int x,int y,int z,BlockState s){put(new BlockPos(x,y,z),s);}
        void box(int x1,int y1,int z1,int x2,int y2,int z2,BlockState s){for(int x=x1;x<=x2;x++)for(int y=y1;y<=y2;y++)for(int z=z1;z<=z2;z++)at(x,y,z,s);}
        void chair(int x,int z,Direction facing){at(x,0,z,Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,facing));}
        void desk(int x,int z){for(int dx=-1;dx<=1;dx++){at(x+dx,0,z,Blocks.SPRUCE_FENCE.defaultBlockState());at(x+dx,1,z,Blocks.SPRUCE_TRAPDOOR.defaultBlockState());}chair(x,z+1,Direction.NORTH);}
        void lamp(int x,int y,int z){at(x,y,z,CityBlocks.block("ceiling_light").defaultBlockState());}
        void book(int x,int z,String id){var p=new BlockPos(x,0,z);put(p,Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING,Direction.SOUTH).setValue(LecternBlock.HAS_BOOK,true));Layout.BOOKS.put(p,id);}
        void sign(int x,int y,int z,Direction facing,String a,String c,String d){var p=new BlockPos(x,y,z);put(p.relative(facing.getOpposite()),Blocks.WHITE_CONCRETE.defaultBlockState());put(p,Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING,facing));Layout.NOTICES.put(p,new Notice(a,c,d));}
        void planter(int x,int z,boolean cherry){box(x-1,-1,z-1,x+1,-1,z+1,Blocks.POLISHED_ANDESITE.defaultBlockState());at(x,0,z,Blocks.DIRT.defaultBlockState());box(x,1,z,x,3,z,cherry?Blocks.CHERRY_LOG.defaultBlockState():Blocks.OAK_LOG.defaultBlockState());box(x-1,3,z-1,x+1,4,z+1,(cherry?Blocks.CHERRY_LEAVES:Blocks.OAK_LEAVES).defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT,true));}
        void shell(int x1,int z1,int x2,int z2,BlockState trim,BlockState floor,int door1,int door2){
            box(x1,-1,z1,x2,-1,z2,floor);box(x1,0,z1,x2,5,z2,Blocks.AIR.defaultBlockState());
            for(int x=x1;x<=x2;x++)for(int z=z1;z<=z2;z++) {
                clearSky.add(key(x,z));
                boolean edge=x==x1||x==x2||z==z1||z==z2;
                if(edge)for(int y=0;y<=4;y++)at(x,y,z,y==0||y==4?trim:y<=2&&Math.floorMod(x+z,4)!=0?Blocks.GLASS.defaultBlockState():Blocks.WHITE_CONCRETE.defaultBlockState());
                at(x,5,z,CityBlocks.block("white_panel_slab").defaultBlockState());
            }
            box(door1,0,z2,door2,2,z2,Blocks.AIR.defaultBlockState());
            for(int x=x1+3;x<x2;x+=6)for(int z=z1+3;z<z2;z+=6)lamp(x,4,z);
        }
    }
    private static void library(Builder b){
        b.box(-243,-1,-47,-206,-1,-24,Blocks.BIRCH_PLANKS.defaultBlockState());
        b.box(-243,0,-47,-206,4,-24,Blocks.AIR.defaultBlockState());
        for(int x:new int[]{-241,-208})for(int z=-44;z<=-25;z+=3)b.box(x,0,z,x,2,z+1,Blocks.BOOKSHELF.defaultBlockState());
        for(int x:new int[]{-235,-214})for(int z:new int[]{-33,-27})b.desk(x,z);
        for(int x=-228;x<=-221;x++)b.at(x,0,-43,Blocks.SPRUCE_SLAB.defaultBlockState());
        for(int x:new int[]{-237,-226,-215})for(int z:new int[]{-44,-35,-26})b.lamp(x,4,z);
        b.book(-234,-44,"transfer_leaflet");b.book(-214,-44,"if_wiring_card");b.book(-222,-30,"ability_development_guide");
        b.box(-228,-1,-46,-221,-1,-25,Blocks.SMOOTH_QUARTZ.defaultBlockState());
        b.at(-210,0,-26,Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        b.sign(-224,3,-47,Direction.SOUTH,"D7 资料阅览室","LIBRARY / STUDY","借阅 · 实训资料 · 委托");
    }
    private static void clinic(Builder b){
        b.shell(-242,-57,-231,-49,Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState(),Blocks.SMOOTH_QUARTZ.defaultBlockState(),-237,-236);
        // East entrance joins the library canopy, away from the reception desk.
        b.box(-231,0,-53,-231,2,-51,Blocks.AIR.defaultBlockState());b.box(-230,-1,-53,-227,-1,-51,Blocks.SMOOTH_QUARTZ.defaultBlockState());b.box(-230,0,-53,-227,2,-51,Blocks.AIR.defaultBlockState());
        for(int x:new int[]{-240,-237}){
            var bed=Blocks.LIGHT_BLUE_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.NORTH);
            b.at(x,0,-54,bed.setValue(BedBlock.PART,BedPart.FOOT));b.at(x,0,-55,bed.setValue(BedBlock.PART,BedPart.HEAD));
            b.at(x+1,0,-55,Blocks.BARREL.defaultBlockState());
        }
        b.box(-237,0,-49,-233,0,-49,Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
        b.at(-240,1,-52,Blocks.WHITE_STAINED_GLASS.defaultBlockState());b.at(-239,1,-52,Blocks.WHITE_STAINED_GLASS.defaultBlockState());
        b.at(-233,0,-55,Blocks.CAULDRON.defaultBlockState());
        b.sign(-235,3,-50,Direction.NORTH,"D7 校园保健室","MEDICAL / FIRST AID","医护治疗 · 4 学园币");
    }
    private static void stationService(Builder b){
        b.shell(-82,428,-71,437,CityBlocks.block("brushed_steel").defaultBlockState(),Blocks.POLISHED_ANDESITE.defaultBlockState(),-81,-80);
        for(int x=-79;x<=-73;x++)b.at(x,0,431,Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
        for(int x:new int[]{-80,-77,-74})b.chair(x,435,Direction.NORTH);
        b.at(-72,0,435,Blocks.BARREL.defaultBlockState());b.at(-72,1,435,Blocks.IRON_TRAPDOOR.defaultBlockState());
        b.book(-80,430,"transfer_leaflet");b.sign(-76,3,429,Direction.SOUTH,"中央站 服务厅","CENTRAL / INFORMATION","兑换 · 线路 · 城市委托");
    }
    private static void shop(Builder b,boolean cafe){
        int cx=cafe?-45:-85;
        b.shell(cx-7,566,cx+7,583,cafe?Blocks.TERRACOTTA.defaultBlockState():Blocks.GREEN_TERRACOTTA.defaultBlockState(),cafe?Blocks.DARK_OAK_PLANKS.defaultBlockState():Blocks.SMOOTH_QUARTZ.defaultBlockState(),cx-5,cx-4);
        for(int x=cx-2;x<=cx+2;x++)b.at(x,0,567,Blocks.SPRUCE_SLAB.defaultBlockState());
        b.box(cx-6,-1,563,cx+6,-1,565,Blocks.POLISHED_ANDESITE.defaultBlockState());
        b.box(cx-6,4,563,cx+6,4,565,cafe?Blocks.ORANGE_TERRACOTTA.defaultBlockState():Blocks.GREEN_CONCRETE.defaultBlockState());
        if(cafe){
            b.box(cx-5,0,581,cx+5,0,581,Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState());
            for(int x=cx-5;x<=cx+5;x++)b.at(x,1,581,Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
            for(int x:new int[]{cx-4,cx,cx+4})b.at(x,2,582,Blocks.BOOKSHELF.defaultBlockState());
            b.at(cx-4,1,581,Blocks.LANTERN.defaultBlockState());b.at(cx+4,1,581,Blocks.POTTED_FERN.defaultBlockState());
            b.desk(cx+3,574);b.desk(cx+3,578);b.at(cx-5,0,577,Blocks.CRAFTING_TABLE.defaultBlockState());b.at(cx-5,1,578,Blocks.BREWING_STAND.defaultBlockState());
            b.book(cx-5,580,"if_wiring_card");b.at(cx+5,0,571,Blocks.POTTED_FERN.defaultBlockState());
        }else{
            for(int x=cx-5;x<=cx+5;x+=2)b.box(x,0,581,x,2,581,Blocks.BARREL.defaultBlockState());
            for(int x:new int[]{cx-5,cx+4})b.box(x,0,573,x,1,577,Blocks.BARREL.defaultBlockState());
            b.at(cx+5,0,569,Blocks.CRAFTING_TABLE.defaultBlockState());b.at(cx-4,0,570,Blocks.POTTED_FERN.defaultBlockState());
        }
        b.sign(cx,3,567,Direction.SOUTH,cafe?"东亭 咖啡与器材":"西亭 城市便利店",cafe?"CAFE / ABILITY SUPPLIES":"FOOD / CITY GOODS",cafe?"阅读 · 手作 · 补给":"便餐 · 饮品 · 商亭差事");
        b.planter(cx-9,562,cafe);
    }
    private static void stationRoom(Builder b,int station){
        int z=cn.academy.port.train.NorthSouthLine.STATIONS.get(station).z();
        BlockState accent=(station==1?Blocks.LIGHT_BLUE_CONCRETE:station==2?Blocks.CYAN_TERRACOTTA:Blocks.ORANGE_TERRACOTTA).defaultBlockState();
        b.shell(106,z+76,128,z+94,accent,(station==3?Blocks.SPRUCE_PLANKS:Blocks.SMOOTH_QUARTZ).defaultBlockState(),114,117);
        b.box(106,-1,z+95,138,-1,z+100,Blocks.SMOOTH_QUARTZ.defaultBlockState());b.box(106,0,z+95,138,3,z+100,Blocks.AIR.defaultBlockState());
        b.box(108,0,z+87,113,0,z+87,Blocks.SPRUCE_SLAB.defaultBlockState());
        b.book(126,z+91,"transfer_leaflet");
        if(station==1){
            b.box(107,0,z+78,107,2,z+90,Blocks.BOOKSHELF.defaultBlockState());b.desk(120,z+85);b.desk(120,z+90);
            b.planter(108,z+99,true);b.planter(126,z+99,true);
        }else if(station==2){
            for(int x:new int[]{118,121,124})b.chair(x,z+89,Direction.NORTH);
            b.box(119,1,z+77,124,2,z+77,CityBlocks.block("led_screen").defaultBlockState());
            b.at(127,0,z+80,Blocks.BARREL.defaultBlockState());b.at(127,0,z+82,Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
            b.planter(108,z+99,false);b.lamp(134,3,z+98);
        }else{
            b.desk(120,z+85);b.desk(120,z+90);b.at(126,0,z+79,Blocks.CRAFTING_TABLE.defaultBlockState());
            b.box(107,0,z+78,107,1,z+82,Blocks.BARREL.defaultBlockState());b.at(126,0,z+88,Blocks.POTTED_FERN.defaultBlockState());
            b.planter(108,z+99,false);b.planter(126,z+99,false);
        }
        b.sign(115,3,z+95,Direction.SOUTH,station==1?"D7 校园通勤阅览室":station==2?"中央站 通勤服务室":"D15 通勤共创室",station==1?"CAMPUS / STUDY":station==2?"CENTRAL / CITY LINK":"D15 / CO-WORK","日常委托 · 候车 · 乘车通勤");
    }
    static Column at(int x,int z){return Layout.COLUMNS.get(key(x,z));}
    static void initialize(BlockPos pos,BlockEntity entity){
        if(entity instanceof LecternBlockEntity lectern&&Layout.BOOKS.containsKey(pos))lectern.setBook(CityGoods.referenceBook(Layout.BOOKS.get(pos)));
        var n=Layout.NOTICES.get(pos);
        if(n!=null&&entity instanceof SignBlockEntity sign)sign.getPersistentData().putBoolean("academy:life_sign_pending",true);
    }
    /** Sign setters send level updates: complete their one-time worldgen text after the chunk is attached. */
    @net.neoforged.bus.api.SubscribeEvent public static void signs(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event){
        if(!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel world)||!world.dimension().equals(cn.academy.port.train.TrainManager.CITY)||world.getGameTime()%20!=0)return;
        Layout.NOTICES.forEach((pos,n)->{
            if(!world.hasChunkAt(pos)||!(world.getBlockEntity(pos) instanceof SignBlockEntity sign)||!sign.getPersistentData().getBoolean("academy:life_sign_pending"))return;
            var text=new SignText().setColor(DyeColor.CYAN).setHasGlowingText(true).setMessage(0,Component.literal(n.first)).setMessage(1,Component.literal(n.second)).setMessage(2,Component.literal(n.third));
            sign.setText(text,true);sign.setText(text,false);sign.setWaxed(true);
            sign.getPersistentData().remove("academy:life_sign_pending");sign.setChanged();
        });
    }
}
