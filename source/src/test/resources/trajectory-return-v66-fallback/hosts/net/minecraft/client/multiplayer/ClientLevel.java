package net.minecraft.client.multiplayer;
import java.util.*;import net.minecraft.world.entity.Entity;import net.minecraft.core.BlockPos;import net.minecraft.world.level.ClipContext;import net.minecraft.world.phys.HitResult;
/** Controlled loaded-chunk and opaque-plane collision responses at the platform boundary. */
public class ClientLevel {
 public final Map<Integer,Entity> entities=new LinkedHashMap<>();public boolean chunks=true;
 public final Set<Long> unavailableChunks=new HashSet<>();public int clipQueries;public double wallZ=Double.NaN;
 public Entity getEntity(int id){return entities.get(id);}
 public boolean hasChunkAt(BlockPos p){return chunks&&!unavailableChunks.contains(((long)Math.floorDiv(p.x(),16)<<32)^(Math.floorDiv(p.z(),16)&0xffffffffL));}
 public HitResult clip(ClipContext c){clipQueries++;boolean hit=Double.isFinite(wallZ)&&(c.from().z-wallZ)*(c.to().z-wallZ)<=0;return new HitResult(hit?HitResult.Type.BLOCK:HitResult.Type.MISS,c.to());}
}
