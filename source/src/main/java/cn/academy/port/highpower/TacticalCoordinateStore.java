package cn.academy.port.highpower;

import cn.academy.port.highpower.core.TacticalCoordinateRules;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Strict server-owned NEW353 persistence. A malformed root is never repaired into an empty store. */
final class TacticalCoordinateStore {
    static final String KEY = "academy:tactical_coordinate_formation";
    private static final Set<String> ROOT_KEYS = Set.of("schema", "generation", "request", "cooldown", "templates");
    private static final Set<String> ROW_KEYS = Set.of("index", "id", "revision", "dimension", "x", "y", "z", "yaw", "pitch");
    record State(long generation, int request, int cooldown, List<TacticalCoordinateFormation.Template> templates) {
        State { templates = List.copyOf(templates); }
        TacticalCoordinateFormation.Template at(int index) {
            return templates.stream().filter(row -> row.index() == index).findFirst().orElse(null);
        }
        State fences(int request, int cooldown) { return new State(generation, request, cooldown, templates); }
        State advance() { return fences(Math.max(0, request - 1), Math.max(0, cooldown - 1)); }
    }
    private TacticalCoordinateStore() {}
    static State read(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(KEY)) return new State(0, 0, 0, List.of());
        if (!data.contains(KEY, Tag.TAG_COMPOUND)) return null;
        return decode(data.getCompound(KEY));
    }
    static State decode(CompoundTag root) {
        if (!root.getAllKeys().equals(ROOT_KEYS) || !root.contains("schema", Tag.TAG_INT) || root.getInt("schema") != 1
                || !root.contains("generation", Tag.TAG_LONG) || !root.contains("request", Tag.TAG_INT)
                || !root.contains("cooldown", Tag.TAG_INT) || !root.contains("templates", Tag.TAG_LIST)) return null;
        long generation = root.getLong("generation");
        int request = root.getInt("request"), cooldown = root.getInt("cooldown");
        if (generation < 0 || generation == Long.MAX_VALUE || request < 0 || request > TacticalCoordinateRules.REQUEST_INTERVAL_TICKS
                || cooldown < 0 || cooldown > 60) return null;
        ListTag rows = (ListTag) root.get("templates");
        if (rows == null || rows.size() > TacticalCoordinateRules.MAX_TEMPLATES
                || !rows.isEmpty() && rows.getElementType() != Tag.TAG_COMPOUND) return null;
        var templates = new ArrayList<TacticalCoordinateFormation.Template>();
        var slots = new HashSet<Integer>(); var ids = new HashSet<java.util.UUID>(); var revisions = new HashSet<Long>();
        for (int i = 0; i < rows.size(); i++) {
            CompoundTag row = rows.getCompound(i);
            if (!row.getAllKeys().equals(ROW_KEYS) || !row.contains("index", Tag.TAG_INT) || !row.hasUUID("id")
                    || !row.contains("revision", Tag.TAG_LONG) || !row.contains("dimension", Tag.TAG_STRING)
                    || !row.contains("x", Tag.TAG_DOUBLE) || !row.contains("y", Tag.TAG_DOUBLE)
                    || !row.contains("z", Tag.TAG_DOUBLE) || !row.contains("yaw", Tag.TAG_FLOAT)
                    || !row.contains("pitch", Tag.TAG_FLOAT)) return null;
            int index = row.getInt("index"); long revision = row.getLong("revision");
            String dimension = row.getString("dimension");
            ResourceLocation location = ResourceLocation.tryParse(dimension);
            Vec3 feet = new Vec3(row.getDouble("x"), row.getDouble("y"), row.getDouble("z"));
            float yaw = row.getFloat("yaw"), pitch = row.getFloat("pitch");
            if (index < 0 || index >= TacticalCoordinateRules.MAX_TEMPLATES || !slots.add(index)
                    || !ids.add(row.getUUID("id")) || row.getUUID("id").equals(new java.util.UUID(0, 0))
                    || !revisions.add(revision) || revision <= 0 || revision > generation
                    || dimension.length() > 256 || location == null || !location.toString().equals(dimension)
                    || !finite(feet) || !Float.isFinite(yaw) || yaw < -180 || yaw >= 180
                    || !Float.isFinite(pitch) || pitch < -90 || pitch > 90) return null;
            templates.add(new TacticalCoordinateFormation.Template(index, row.getUUID("id"), revision, dimension, feet, yaw, pitch));
        }
        templates.sort(java.util.Comparator.comparingInt(TacticalCoordinateFormation.Template::index));
        return new State(generation, request, cooldown, templates);
    }
    static CompoundTag encode(State state) {
        CompoundTag root = new CompoundTag(); root.putInt("schema", 1); root.putLong("generation", state.generation);
        root.putInt("request", state.request); root.putInt("cooldown", state.cooldown);
        ListTag rows = new ListTag();
        for (var template : state.templates) rows.add(row(template));
        root.put("templates", rows); return root;
    }
    static CompoundTag row(TacticalCoordinateFormation.Template template) {
        CompoundTag row = new CompoundTag(); row.putInt("index", template.index()); row.putUUID("id", template.id());
        row.putLong("revision", template.revision()); row.putString("dimension", template.dimension());
        row.putDouble("x", template.feet().x); row.putDouble("y", template.feet().y); row.putDouble("z", template.feet().z);
        row.putFloat("yaw", template.yaw()); row.putFloat("pitch", template.pitch()); return row;
    }
    /** Compare the complete read view before replacing it; never overwrite a corrupt or changed record. */
    static boolean write(ServerPlayer player, State expected, State replacement) {
        State current = read(player);
        if (current == null || !current.equals(expected)) return false;
        CompoundTag encoded = encode(replacement);
        if (decode(encoded) == null) return false;
        player.getPersistentData().put(KEY, encoded); return true;
    }
    static boolean finite(Vec3 point) {
        return point != null && TacticalCoordinateRules.validCoordinates(point.x, point.y, point.z);
    }
}
