package cn.academy.port.client.canon;

import java.util.Map;

/**
 * Layout of the FX4 texture pack (textures/fx4, 68 PNGs). Generated from delivery/codex-20261029-fx4-textures/manifest.json;
 * regenerate if the pack changes. Grids are row-major from the top left; frames = 0 means the cells are variants, not an animation.
 */
final class Fx4Atlas {
    record Grid(String name,int width,int height,int cols,int rows,int frames,boolean additive,boolean data) {
        int cells(){return cols*rows;}
        /** UV rectangle of one cell, pulled in by half a texel so bilinear filtering never bleeds into the next cell. */
        float[] cell(int i){
            int c=Math.floorMod(i,cells());int col=c%cols,row=c/cols;float cw=width/(float)cols,ch=height/(float)rows;
            return new float[]{(col*cw+.5f)/width,(row*ch+.5f)/height,((col+1)*cw-.5f)/width,((row+1)*ch-.5f)/height};
        }
        /** Animation frame for progress 0..1 over the effect's real life: min(N-1, floor(p*N)). */
        int frame(double progress){int n=Math.max(1,frames>0?frames:cells());return (int)Math.min(n-1,Math.floor(Math.max(0,Math.min(1,progress))*n));}
    }
    private static Grid g(String n,int w,int h,int c,int r,int f,boolean add,boolean data){return new Grid(n,w,h,c,r,f,add,data);}
    static final Map<String,Grid> ALL=java.util.stream.Stream.of(
        g("common_noise_tile",512,512,1,1,0,false,true),
        g("common_flow_map",512,512,1,1,0,false,true),
        g("common_dissolve_ramp",256,16,1,1,0,false,false),
        g("common_shock_ring_sheet",1024,1024,8,8,64,true,false),
        g("common_hit_flash_sheet",512,512,4,4,16,true,false),
        g("common_ground_mask",512,512,1,1,0,false,true),
        g("common_light_pillar",128,512,1,1,0,true,false),
        g("common_speedline",512,64,1,1,0,true,false),
        g("em_arc_sheet",512,512,1,8,0,true,false),
        g("em_spark_sheet",512,512,4,4,16,true,false),
        g("em_fieldline",512,64,1,1,0,true,false),
        g("em_ironsand",256,256,4,4,0,false,false),
        g("em_scorch_decal",512,512,1,1,0,false,false),
        g("md_beam_core",512,64,1,1,0,true,false),
        g("md_beam_halo",512,128,1,1,0,true,false),
        g("md_charge_sheet",1024,1024,8,8,64,true,false),
        g("md_hex_plate",256,256,1,1,0,false,false),
        g("md_molten_decal",512,512,1,1,0,false,false),
        g("md_ember_sheet",256,256,4,4,0,false,false),
        g("tp_rift_sheet",512,1024,4,4,16,false,false),
        g("tp_lattice",512,512,1,1,0,true,false),
        g("tp_reticle",512,512,1,1,0,true,false),
        g("tp_afterimage_mask",256,256,1,1,0,false,true),
        g("tp_fold_decal",512,512,1,1,0,false,false),
        g("vm_arrow_field",512,128,1,1,0,true,false),
        g("vm_pressure_sheet",1024,1024,8,8,64,true,false),
        g("vm_debris_atlas",256,256,4,4,0,false,false),
        g("vm_crack_decal",512,512,1,1,0,false,false),
        g("vm_shear_streak",512,64,1,1,0,true,false),
        g("mo_star_blink_sheet",512,512,4,4,16,false,false),
        g("mo_thread",512,32,1,1,0,true,false),
        g("mo_holo_panel",256,256,1,1,0,true,false),
        g("mo_signal_ring_sheet",512,512,4,4,16,true,false),
        g("mo_status_arc",256,256,1,1,0,true,false),
        g("dm_feather_atlas",1024,512,4,2,0,false,false),
        g("dm_iri_ramp",256,16,1,1,0,false,false),
        g("dm_feather_fall_sheet",1024,1024,8,8,64,false,false),
        g("dm_refraction_normal",256,256,1,1,0,false,true),
        g("dm_shard_atlas",256,256,4,4,0,false,false),
        g("na_shell_rim",256,256,1,1,0,true,false),
        g("na_frost_decal",512,512,1,1,0,false,false),
        g("na_compress_sheet",1024,1024,8,8,64,true,false),
        g("na_frost_atlas",256,256,4,4,0,false,false),
        g("na_vapor_sheet",512,512,4,4,16,true,false),
        g("ah_stream",512,128,1,1,0,true,false),
        g("ah_vortex_sheet",512,512,4,4,16,true,false),
        g("ah_dust_sheet",512,512,4,4,16,false,false),
        g("ah_cone",256,256,1,1,0,true,false),
        g("hh_water_ribbon",512,128,1,1,0,false,false),
        g("hh_splash_sheet",1024,1024,8,8,64,false,false),
        g("hh_drop_atlas",256,256,4,4,0,false,false),
        g("hh_ripple_sheet",512,512,4,4,16,true,false),
        g("hh_curtain",256,512,1,1,0,false,false),
        g("hh_wet_decal",512,512,1,1,0,false,false),
        g("fd_bubble_atlas",512,512,4,4,0,false,false),
        g("fd_slice_ring",512,512,1,1,0,true,false),
        g("fd_column",128,512,1,1,0,true,false),
        g("fd_pop_sheet",256,256,4,4,16,true,false),
        g("ls_ink_sheet",1024,1024,8,8,64,false,false),
        g("ls_wet_ribbon",512,128,1,1,0,false,false),
        g("ls_drip_sheet",256,512,2,4,8,false,false),
        g("ls_pool_decal",512,512,1,1,0,false,false),
        g("ls_tendril",512,64,1,1,0,false,false),
        g("pp_sheet_atlas",1024,512,4,2,0,false,false),
        g("pp_fold_normal",256,256,1,1,0,false,true),
        g("pp_scrap_sheet",1024,1024,8,8,64,false,false),
        g("pp_fiber",256,256,1,1,0,false,false),
        g("pp_edge_shadow",256,16,1,1,0,false,true),
        g("of_mind_neural",1024,1024,1,1,0,false,false),
        g("of_mind_thread",1024,128,1,1,0,false,false),
        g("of_mind_impact",1024,1024,4,4,16,false,false),
        g("of_float_shell",1024,1024,1,1,0,false,false),
        g("of_float_column",256,1024,1,1,0,false,false),
        g("of_float_crush",1024,1024,4,4,16,false,false),
        g("of_paper_blade",1024,512,1,1,0,false,false),
        g("of_paper_fiber",512,512,1,1,0,false,false),
        g("of_paper_wheel",1024,1024,1,1,0,false,false),
        g("of_ink_blade",1024,512,1,1,0,false,false),
        g("of_ink_ripple",1024,1024,4,4,16,false,false),
        g("of_ink_filament",1024,128,1,1,0,false,false),
        g("of_air_crescent",1024,1024,1,1,0,false,false),
        g("of_air_streak",1024,128,1,1,0,false,false),
        g("of_air_impact",1024,1024,4,4,16,false,false)
    ).collect(java.util.stream.Collectors.toUnmodifiableMap(Grid::name,x->x));
    private Fx4Atlas() {}
    static Grid get(String name){Grid grid=ALL.get(name);if(grid==null)throw new IllegalArgumentException("unknown fx4 texture "+name);return grid;}
}
