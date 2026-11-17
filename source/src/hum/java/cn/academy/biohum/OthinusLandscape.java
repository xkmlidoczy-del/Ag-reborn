package cn.academy.biohum;

import java.util.NavigableMap;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Designs for the rewritten terrain. Positions are relative to the same anchor in every phase. */
final class OthinusLandscape {
    private static final int TILE=32;
    private static final Block AIR=Blocks.AIR;
    private static final BlockState STILL_WATER=Blocks.LIGHT.defaultBlockState()
            .setValue(LightBlock.LEVEL,0).setValue(LightBlock.WATERLOGGED,true);
    private final TreeMap<BlockPos,BlockState> cells=new TreeMap<>();
    private final BlockPos anchor;
    private final int radius;
    private final int minX, maxX, minZ, maxZ;

    static NavigableMap<BlockPos,BlockState> chunk(OthinusPhase phase,BlockPos anchor,int radius,int cx,int cz) {
        var layout=new OthinusLandscape(anchor,radius,cx,cz);
        layout.draw(phase);
        return layout.cells;
    }

    private OthinusLandscape(BlockPos anchor,int radius,int cx,int cz) {
        this.anchor=anchor;
        this.radius=radius;
        minX=Math.max(-radius,(cx<<4)-anchor.getX());
        maxX=Math.min(radius,(cx<<4)+15-anchor.getX());
        minZ=Math.max(-radius,(cz<<4)-anchor.getZ());
        maxZ=Math.min(radius,(cz<<4)+15-anchor.getZ());
    }

    private void draw(OthinusPhase phase) {
        switch(phase) {
            case DARK, SPACE -> voidWorld(phase==OthinusPhase.SPACE);
            case ALPHA -> city(true,false);
            case BETA -> city(false,false);
            case OMEGA -> city(false,true);
            case BURIAL -> burial();
            case SEA -> sea();
            case DUEL -> duel();
            case RETURN -> { }
        }
    }

    private void put(int x,int y,int z,Block block) {
        put(x,y,z,block.defaultBlockState());
    }

    private void put(int x,int y,int z,BlockState state) {
        if(x<minX || x>maxX || z<minZ || z>maxZ || x*x+z*z>radius*radius)return;
        cells.put(anchor.offset(x,y,z),state);
    }

    private void box(int x1,int y1,int z1,int x2,int y2,int z2,Block material) {
        for(int z=Math.max(z1,minZ);z<=Math.min(z2,maxZ);z++)
            for(int x=Math.max(x1,minX);x<=Math.min(x2,maxX);x++)
                for(int y=y1;y<=y2;y++)put(x,y,z,material);
    }

    private void voidWorld(boolean space) {
        for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++){
            int extent=Math.max(Math.abs(x),Math.abs(z));
            if(extent<=5) put(x,-1,z,extent==5&&space?Blocks.CRYING_OBSIDIAN:Blocks.BLACK_CONCRETE);
            if(space && extent>=18 && Math.floorMod(noise(x,z),281)==0)
                put(x,9+Math.floorMod(noise(z,x),18),z,Blocks.PEARLESCENT_FROGLIGHT);
        }
    }

    private void city(boolean ruined,boolean festival) {
        for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++){
            boolean avenue=Math.abs(x)<=4||Math.abs(z)<=4||Math.floorMod(x+4,TILE)<6
                    ||Math.floorMod(z+4,TILE)<6;
            Block paving=avenue?Blocks.GRAY_CONCRETE:ruined?Blocks.COBBLED_DEEPSLATE:Blocks.SMOOTH_STONE;
            if(avenue && (x==0 && Math.floorMod(z,7)<3 || z==0 && Math.floorMod(x,7)<3)){
                paving=ruined?Blocks.CRACKED_STONE_BRICKS:Blocks.YELLOW_CONCRETE;
            }
            if(festival&&Math.abs(x)<32&&Math.abs(z)<32&&!avenue)paving=Blocks.MOSS_BLOCK;
            put(x,-1,z,paving);
            if(ruined && Math.floorMod(noise(x,z),33)==0)
                put(x,0,z,Math.floorMod(noise(z,x),4)==0?Blocks.MAGMA_BLOCK:Blocks.COBBLESTONE);
        }
        // A navigable street grid with staggered towers and glass-fronted midrises.
        for(int z=Math.floorDiv(minZ,TILE)*TILE-TILE;z<=maxZ+TILE;z+=TILE)
            for(int x=Math.floorDiv(minX,TILE)*TILE-TILE;x<=maxX+TILE;x+=TILE){
                int cx=x+16, cz=z+16;
                if(Math.abs(cx)<28 && Math.abs(cz)<28)continue;
                int h=11+Math.floorMod(noise(cx,cz),26);
                tower(cx,cz,h,ruined,festival);
            }
        if(ruined) station();
        else if(festival) festival();
        else classroom();
    }

    private void tower(int cx,int cz,int h,boolean ruined,boolean festival) {
        int width=5+Math.floorMod(noise(cz,cx),3);
        int depth=5+Math.floorMod(noise(cx,cz),4);
        Block frame=ruined?Blocks.DEEPSLATE_BRICKS:festival?Blocks.WHITE_CONCRETE:Blocks.SMOOTH_QUARTZ;
        Block glass=ruined?Blocks.BROWN_STAINED_GLASS:festival?Blocks.LIGHT_BLUE_STAINED_GLASS:Blocks.CYAN_STAINED_GLASS;
        for(int x=Math.max(cx-width,minX);x<=Math.min(cx+width,maxX);x++)
            for(int z=Math.max(cz-depth,minZ);z<=Math.min(cz+depth,maxZ);z++){
                int dx=Math.abs(x-cx),dz=Math.abs(z-cz);
                boolean edge=dx==width||dz==depth;
                boolean column=dx==width&&dz==depth||edge&&Math.floorMod(x+z,6)==0;
                int top=ruined?Math.max(5,h-Math.floorMod(noise(x,z),13)):h;
                for(int y=0;y<=top;y++){
                    if(ruined&&edge&&y>2&&Math.floorMod(noise(x+y,z),11)<3)continue;
                    if(edge)put(x,y,z,column||y%4==0?frame:glass);
                    else if(y%8==0||y==top)put(x,y,z,frame);
                }
            }
        if(!ruined){
            box(cx-2,h+1,cz-2,cx+2,h+2,cz+2,Blocks.GRAY_CONCRETE);
            box(cx,h+3,cz,cx,h+6,cz,Blocks.IRON_BARS);
        }
    }

    private void station() {
        box(-24,-1,-34,24,-1,-11,Blocks.POLISHED_ANDESITE);
        box(-25,0,-31,-23,8,-12,Blocks.CRACKED_STONE_BRICKS);
        box(23,0,-34,25,11,-12,Blocks.CRACKED_STONE_BRICKS);
        box(-24,8,-31,23,8,-31,Blocks.DEEPSLATE_TILES);
        for(int x=-21;x<=21;x+=7){
            box(x,0,-14,x+1,6,-13,Blocks.GRAY_CONCRETE);
            box(x,6,-24,x+1,6,-12,Blocks.DEEPSLATE_TILES);
        }
        for(int z=Math.max(-33,minZ);z<=Math.min(-14,maxZ);z++)for(int x=Math.max(-20,minX);x<=Math.min(20,maxX);x++){
            if(x*x*.52+(z+24)*(z+24)>125)continue;
            int depth=2+Math.floorMod(noise(x,z),2);
            for(int y=-1;y>=-depth;y--)put(x,y,z,AIR);
            put(x,-depth-1,z,Blocks.DEEPSLATE_TILES);
        }
        for(int x=-18;x<=18;x+=9){
            put(x,0,-11,Blocks.MAGMA_BLOCK);
            put(x,0,-35,Blocks.MAGMA_BLOCK);
        }
        box(-5,1,-12,-2,2,-12,Blocks.RED_CONCRETE);
        box(2,1,-12,5,2,-12,Blocks.ORANGE_CONCRETE);
    }

    private void classroom() {
        box(-18,-1,-34,18,-1,13,Blocks.SMOOTH_STONE);
        box(-17,-1,-16,17,-1,9,Blocks.BIRCH_PLANKS);
        box(-17,0,-16,17,7,-16,Blocks.SMOOTH_QUARTZ);
        box(-17,0,-15,-17,7,9,Blocks.SMOOTH_QUARTZ);
        box(-18,0,-33,-18,10,-18,Blocks.WHITE_CONCRETE);
        box(18,0,-33,18,10,-18,Blocks.WHITE_CONCRETE);
        box(-18,8,-33,18,8,-18,Blocks.LIGHT_GRAY_CONCRETE);
        box(-16,1,-15,16,4,-15,Blocks.GREEN_TERRACOTTA);
        box(-12,2,-15,-4,2,-15,Blocks.WHITE_CONCRETE);
        box(4,3,-15,10,3,-15,Blocks.LIME_CONCRETE);
        for(int z=-10;z<=8;z+=4){
            box(-17,2,z,-17,5,z+2,Blocks.LIGHT_BLUE_STAINED_GLASS);
            for(int x=-12;x<=12;x+=6){
                put(x,0,z,Blocks.SPRUCE_STAIRS);
                put(x,0,z-2,Blocks.BIRCH_FENCE);
                put(x,1,z-2,Blocks.SMOOTH_SANDSTONE_SLAB);
            }
        }
        box(-4,0,-13,0,1,-12,Blocks.SPRUCE_PLANKS);
        for(int x=-15;x<=15;x+=6){
            box(x,6,-16,x,6,9,Blocks.WHITE_CONCRETE);
            put(x,5,-6,Blocks.SEA_LANTERN);
        }
    }

    private void festival() {
        box(-29,-1,-37,29,-1,18,Blocks.POLISHED_DIORITE);
        box(-5,-1,-40,5,-1,20,Blocks.SMOOTH_SANDSTONE);
        Block[] colors={Blocks.RED_WOOL,Blocks.PINK_WOOL,Blocks.ORANGE_WOOL,Blocks.LIME_WOOL,Blocks.CYAN_WOOL};
        for(int row=0;row<4;row++){
            int z=-29+row*12;
            for(int side:new int[]{-1,1}){
                int x=side*19;
                for(int dx:new int[]{-4,4})for(int dz:new int[]{-3,3})
                    box(x+dx,0,z+dz,x+dx,4,z+dz,Blocks.STRIPPED_SPRUCE_LOG);
                for(int dx=-5;dx<=5;dx++){
                    Block cloth=(dx%2==0)?colors[row+(side==1?1:0)]:Blocks.WHITE_WOOL;
                    box(x+dx,4,z-4,x+dx,4,z+4,cloth);
                }
                box(x-3,0,z+2,x+3,1,z+2,Blocks.SPRUCE_PLANKS);
                put(x,3,z-4,Blocks.LANTERN);
            }
        }
        for(int z=-31;z<=17;z+=12){
            for(int x:new int[]{-9,9}){
                box(x,0,z,x,7,z,Blocks.DARK_OAK_FENCE);
                put(x,7,z,Blocks.GLOWSTONE);
            }
            for(int x=-8;x<=8;x++){
                int y=5+Math.abs(x)/4;
                put(x,y,z,colors[Math.floorMod(x+z,5)]);
            }
        }
        for(int x:new int[]{-31,31})for(int z=-31;z<=17;z+=16)tree(x,z);
        box(-5,0,-37,5,1,-33,Blocks.WARPED_PLANKS);
        box(-4,2,-36,4,2,-34,Blocks.QUARTZ_BLOCK);
        // The network remembers in a quiet white room off the festival route.
        box(5,-1,5,14,-1,14,Blocks.QUARTZ_BLOCK);
        box(5,0,14,14,4,14,Blocks.WHITE_CONCRETE);
        box(14,0,5,14,4,14,Blocks.WHITE_CONCRETE);
        put(11,0,12,Blocks.SEA_LANTERN);
    }

    private void tree(int x,int z){
        box(x,0,z,x,5,z,Blocks.CHERRY_LOG);
        box(x-3,4,z-2,x+3,5,z+2,Blocks.CHERRY_LEAVES);
        box(x-2,6,z-3,x+2,6,z+3,Blocks.CHERRY_LEAVES);
        box(x-1,7,z-1,x+1,7,z+1,Blocks.CHERRY_LEAVES);
        put(x+2,0,z+1,Blocks.PINK_PETALS);
    }

    private void burial(){
        box(minX,-1,minZ,maxX,-1,maxZ,Blocks.DEEPSLATE_BRICKS);
        for(int row=-3;row<=3;row++)for(int col=-3;col<=3;col++){
            if(Math.abs(row)+Math.abs(col)<2)continue;
            int x=col*23,z=row*23;
            box(x-2,0,z-3,x+2,0,z+3,Blocks.POLISHED_BLACKSTONE);
            box(x-1,0,z-3,x+1,2,z-3,Blocks.CHISELED_DEEPSLATE);
        }
        box(-6,-1,-6,6,-1,6,Blocks.POLISHED_BLACKSTONE_BRICKS);
        box(-6,0,-6,6,4,-6,Blocks.BLACKSTONE);
        box(-6,0,-5,-6,4,6,Blocks.BLACKSTONE);
        box(6,0,-5,6,4,6,Blocks.BLACKSTONE);
        box(-6,0,6,6,4,6,Blocks.BLACKSTONE);
        box(-6,4,-6,6,4,6,Blocks.BLACKSTONE);
        put(5,1,-5,Blocks.SOUL_LANTERN);
    }

    private void sea(){
        box(minX,-3,minZ,maxX,-1,maxZ,Blocks.BLUE_ICE);
        for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++){
            int sq=x*x+z*z;
            put(x,0,z,sq>(radius-2)*(radius-2)?Blocks.PACKED_ICE.defaultBlockState():STILL_WATER);
        }
        box(-8,0,-5,8,0,5,Blocks.SPRUCE_PLANKS);
        box(-5,1,-4,5,1,-4,Blocks.DARK_OAK_FENCE);
        box(-5,1,4,5,1,4,Blocks.DARK_OAK_FENCE);
        box(0,1,-3,0,9,-3,Blocks.STRIPPED_SPRUCE_LOG);
        box(-5,5,-3,-1,8,-3,Blocks.WHITE_WOOL);
        for(int z=-44;z<=39;z+=26){
            int x=21+Math.floorMod(z,12);
            box(x,0,z,x+6,0,z+3,Blocks.DARK_OAK_PLANKS);
            box(-x,0,z+4,-x+5,0,z+7,Blocks.SPRUCE_PLANKS);
        }
    }

    private void duel(){
        for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++){
            int m=Math.max(Math.abs(x),Math.abs(z+12));
            Block floor=m<=37?Blocks.GRAY_CONCRETE:Blocks.BLACK_CONCRETE;
            if(m<=37 && (Math.abs(x)==26||Math.abs(z+12)==26||x==0))floor=Blocks.WHITE_CONCRETE;
            put(x,-1,z,floor);
            if(m==38)put(x,0,z,Blocks.IRON_BARS);
        }
        box(-42,0,-44,-35,14,13,Blocks.WHITE_CONCRETE);
        box(35,0,-44,42,14,13,Blocks.WHITE_CONCRETE);
        for(int z=-40;z<=8;z+=8)for(int y=3;y<=11;y+=4){
            box(-35,y,z,-35,y+2,z+3,Blocks.BLUE_STAINED_GLASS);
            box(35,y,z,35,y+2,z+3,Blocks.BLUE_STAINED_GLASS);
        }
        for(int x:new int[]{-28,28})for(int z:new int[]{-38,14}){
            box(x,0,z,x,15,z,Blocks.IRON_BARS);
            box(x-1,15,z,x+1,15,z,Blocks.SEA_LANTERN);
        }
    }

    private static int noise(int x,int z){
        int value=x*73428767^z*912931;
        value=(value^(value>>>15))*0x2c1b3c6d;
        return value^(value>>>12);
    }

}
