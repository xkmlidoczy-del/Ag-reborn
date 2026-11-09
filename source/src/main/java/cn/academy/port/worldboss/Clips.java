package cn.academy.port.worldboss;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Shared authored animation events, loaded from the same V9 metadata as the renderer. */
public final class Clips {
    public record Event(int tick, String type) {}
    public record Clip(String key, String title, int frames, String kind, List<Event> events) {
        public int ticks() { return frames - 1; }
        public boolean loops() { return kind.equals("idle") || kind.equals("walk"); }
    }
    public static final int SPAWN=0,IDLE=1,WALK=2,ROAR=3,BITE=4,COMBO=5,RAM=6,LRAKE=7,RUPPER=8,TSWEEP=9,TSLAM=10,STOMP=11,TWIN=12,CHARGE=13,BREATH=14,LANCE=15,METEOR=16,NOVA=17,WORLDBREAKER=18;
    private static List<Clip> clips;
    public static synchronized List<Clip> all() {
        if (clips == null) {
            var stream=Clips.class.getResourceAsStream("/assets/academy/worldboss/clips.json");
            if(stream==null)throw new IllegalStateException("Missing World Boss animation metadata");
            try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
                var result=new ArrayList<Clip>();
                for(var entry:JsonParser.parseReader(reader).getAsJsonArray()) {
                    JsonObject row=entry.getAsJsonObject();float duration=row.get("duration").getAsFloat();
                    if(!Float.isFinite(duration)||duration<=0||duration>30)throw new IllegalArgumentException("Invalid boss clip duration");
                    int frames=Math.round(duration*20)+1;var events=new ArrayList<Event>();
                    for(var event:row.getAsJsonArray("events")) {
                        var pair=event.getAsJsonArray();int tick=Math.round(pair.get(0).getAsFloat()*20);
                        if(tick<0||tick>=frames)throw new IllegalArgumentException("Boss event outside clip");
                        events.add(new Event(tick,pair.get(1).getAsString()));
                    }
                    result.add(new Clip(row.get("key").getAsString(),row.get("title").getAsString(),frames,row.get("kind").getAsString(),List.copyOf(events)));
                }
                if(result.size()!=19)throw new IllegalStateException("Expected nineteen World Boss animations");
                clips=List.copyOf(result);
            } catch(java.io.IOException ex) {throw new IllegalStateException("Cannot read World Boss animations",ex);}
        }
        return clips;
    }
    public static Clip get(int index) {return all().get(Math.max(0,Math.min(index,all().size()-1)));}
    private Clips() {}
}
