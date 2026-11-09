package cn.academy.port.city.defense;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/** Incident identities survive server saves. Unloaded officers are never replaced with duplicates. */
public final class CityDefenseData extends SavedData {
    public static final class Team {
        public final UUID id,victim;
        public final Vec3 center;
        public final long dispatched,expires;
        public long lastHarm,quietSince=-1,closedAt=-1;
        public boolean civilianLost,o5Called;
        public final String name;
        public final List<UUID> officers=new ArrayList<>(),vehicles=new ArrayList<>(),threats=new ArrayList<>();
        public final List<UUID> casualties=new ArrayList<>(),operatives=new ArrayList<>();
        public Team(UUID id,UUID victim,Vec3 center,long now,long expires,String name){this.id=id;this.victim=victim;this.center=center;dispatched=now;lastHarm=now;this.expires=expires;this.name=name;}
        public boolean active(long now){return closedAt<0&&now<expires;}
        CompoundTag save(){var t=new CompoundTag();t.putUUID("Id",id);t.putUUID("Victim",victim);t.putDouble("X",center.x);t.putDouble("Y",center.y);t.putDouble("Z",center.z);
            t.putLong("Dispatched",dispatched);t.putLong("Expires",expires);t.putLong("Harm",lastHarm);t.putLong("Quiet",quietSince);t.putLong("Closed",closedAt);t.putString("Name",name);
            t.putBoolean("CivilianLost",civilianLost);t.putBoolean("O5Called",o5Called);
            uuids(t,"Casualties",casualties);uuids(t,"Operatives",operatives);
            uuids(t,"Officers",officers);uuids(t,"Vehicles",vehicles);uuids(t,"Threats",threats);return t;}
        static Team load(CompoundTag t){var team=new Team(t.getUUID("Id"),t.getUUID("Victim"),new Vec3(t.getDouble("X"),t.getDouble("Y"),t.getDouble("Z")),t.getLong("Dispatched"),t.getLong("Expires"),t.getString("Name"));
            team.lastHarm=t.getLong("Harm");team.quietSince=t.getLong("Quiet");team.closedAt=t.getLong("Closed");
            team.civilianLost=t.getBoolean("CivilianLost");team.o5Called=t.getBoolean("O5Called");read(t,"Casualties",team.casualties);read(t,"Operatives",team.operatives);
            read(t,"Officers",team.officers);read(t,"Vehicles",team.vehicles);read(t,"Threats",team.threats);return team;}
        private static void uuids(CompoundTag t,String key,List<UUID> values){var l=new ListTag();for(var id:values){var e=new CompoundTag();e.putUUID("U",id);l.add(e);}t.put(key,l);}
        private static void read(CompoundTag t,String key,List<UUID> values){for(var raw:t.getList(key,Tag.TAG_COMPOUND)){var e=(CompoundTag)raw;if(e.hasUUID("U"))values.add(e.getUUID("U"));}}
    }
    public final Map<UUID,Team> teams=new LinkedHashMap<>();
    private static final Factory<CityDefenseData> FACTORY=new Factory<>(CityDefenseData::new,(tag,lookup)->load(tag));
    public static CityDefenseData get(ServerLevel level){return level.getDataStorage().computeIfAbsent(FACTORY,"academy_city_defense");}
    public Team team(UUID id){return teams.get(id);}
    public static CityDefenseData load(CompoundTag tag){var data=new CityDefenseData();for(var raw:tag.getList("Teams",Tag.TAG_COMPOUND)){var team=Team.load((CompoundTag)raw);data.teams.put(team.id,team);}return data;}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup){var list=new ListTag();teams.values().forEach(t->list.add(t.save()));tag.put("Teams",list);return tag;}
}
