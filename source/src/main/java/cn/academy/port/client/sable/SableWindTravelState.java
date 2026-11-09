package cn.academy.port.client.sable;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/** Read-only ordinary-world branch of the reviewed Sable weave. No pose, velocity or sublevel is changed.
 * Loaded only after the native-body projection and external API resource proofs have succeeded. */
public final class SableWindTravelState {
    private SableWindTravelState(){}
    public static boolean plain(LocalPlayer player){return blocker(player).isEmpty();}
    public static String blocker(LocalPlayer player){
        if(player==null || !(player instanceof EntityStickExtension stick)
                || !(player instanceof EntityMovementExtension movement)
                || !(player instanceof LivingEntityMovementExtension living)
                || !(player instanceof PlayerFreezeExtension freeze))return "actor_interfaces";
        var mods=net.neoforged.fml.ModList.get();
        if(!version(mods,"sable","2.0.6")||!version(mods,"sablecompanion","1.6.0")||!version(mods,"veil","4.3.2"))return "mod_versions";
        if(stick.sable$getPlotPosition()!=null || movement.sable$getTrackingSubLevel()!=null
                || freeze.sable$getFrozenToSubLevel()!=null || EntitySubLevelUtil.hasCustomEntityOrientation(player)
                || Sable.HELPER.getContaining(player)!=null || player.isPassenger())return "tracked_rotated_or_frozen";
        var inherited=living.sable$getInheritedVelocity();
        if(inherited==null || inherited.x!=0 || inherited.y!=0 || inherited.z!=0)return "inherited_velocity";
        var collision=movement.sable$getCollisionInfo();
        if(collision!=null && (collision.preTrackingSubLevel!=null || collision.trackingSubLevel!=null
                || collision.subLevelHorizontalCollision || !zero(collision.inheritedMotion)
                || collision.firstCollisions!=null&&!collision.firstCollisions.isEmpty()))return "sublevel_collision";
        var container=SubLevelContainer.getContainer(player.level());if(container==null)return "container_missing";
        // Bound the read-only scene query; a dynamic structure may not enter the fixed gas-column neighbourhood.
        if(container.getAllSubLevels().size()>4096)return "scene_budget";
        var p=player.position();var area=new BoundingBox3d(p.x-4,p.y-1,p.z-4,p.x+4,p.y+8,p.z+4);
        return container.queryIntersecting(area).iterator().hasNext()?"dynamic_body_near_column":"";
    }
    private static boolean zero(Vec3 vector){return vector==null || vector.x==0 && vector.y==0 && vector.z==0;}
    private static boolean version(net.neoforged.fml.ModList mods,String id,String expected){return mods.getModContainerById(id).map(c->c.getModInfo().getVersion().toString().equals(expected)).orElse(false);}
}
