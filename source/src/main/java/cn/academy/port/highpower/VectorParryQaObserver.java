package cn.academy.port.highpower;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Opt-in read-only isolated-client witness; never grants an input, damage, resource or visual receipt. */
@EventBusSubscriber(modid="academy")
public final class VectorParryQaObserver {
    private static final boolean ENABLED=Boolean.getBoolean("academy.highpower.vectorParryDiagnostic");
    private static final Map<UUID,String> LAST=new HashMap<>();
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("academy.vector_parry_qa");
    private static int emitted;
    private VectorParryQaObserver(){}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!ENABLED||emitted>=128)return;
        for(var player:event.getServer().getPlayerList().getPlayers()){
            boolean active=VectorParry.active(player);
            if(!active&&!LAST.containsKey(player.getUUID()))continue;
            var exact=HighPowerCombat.exactStateSnapshot(player);
            String value="active="+active+" remaining="+VectorParry.remainingHits(player)+" precise="+VectorParry.precisionAvailable(player)
                    +" hp="+exact.map(s->s.health().toPlainString()).orElse("unavailable")
                    +" shield="+exact.map(s->s.shield().toPlainString()).orElse("unavailable");
            if(!value.equals(LAST.get(player.getUUID()))&&emitted<128){
                emitted++;LOG.info("NEW412_QA tick={} entity={} {}",player.level().getGameTime(),player.getId(),value);
            }
            if(active){if(LAST.size()<16||LAST.containsKey(player.getUUID()))LAST.put(player.getUUID(),value);}
            else LAST.remove(player.getUUID());
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){LAST.clear();emitted=0;}
}
