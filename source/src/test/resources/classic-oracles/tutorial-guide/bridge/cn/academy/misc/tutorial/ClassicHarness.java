package cn.academy.misc.tutorial;

import cn.academy.terminal.App;
import cn.academy.terminal.AppEnvironment;
import cn.academy.terminal.AppRegistry;
import cn.lambdalib.util.datapart.EntityData;
import cn.lambdalib.util.generic.RandUtils;
import cpw.mods.fml.common.gameevent.PlayerEvent.*;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import java.lang.reflect.Field;
import java.util.*;

/** Test bridge; all condition evaluation, state mutation, scheduler and module declarations run upstream. */
public final class ClassicHarness {
    private final EntityPlayer player = new EntityPlayer();
    private TutorialData data;
    private final Conditions conditions;

    public ClassicHarness(boolean give, String appOrder) throws Exception {
        cn.academy.core.AcademyCraft.config.enabled = give;
        RandUtils.RNG.setSeed(107L);
        for (String name : appOrder.split(",")) {
            App app = new App(name) {
                @Override public AppEnvironment createEnvironment() { return new AppEnvironment(); }
            };
            if (name.equals("settings")) app.setPreInstalled();
            AppRegistry.register(app);
        }
        ModuleTutorial.itemTutorial = new ItemTutorial();
        var init = ModuleTutorial.class.getDeclaredMethod("initConditions");
        init.setAccessible(true);
        init.invoke(null);
        var ctor = Conditions.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        conditions = ctor.newInstance();
        data = TutorialData.get(player);
    }

    public List<String> ids() {
        return TutorialRegistry.enumeration().stream().map(t -> t.id).toList();
    }
    public List<String> defaults() {
        return TutorialRegistry.enumeration().stream().filter(ACTutorial::isDefaultInstalled).map(t -> t.id).toList();
    }
    public Map<String, List<String>> previews() {
        var result = new LinkedHashMap<String, List<String>>();
        TutorialRegistry.enumeration().forEach(t -> result.put(t.id, t.getPreview().stream().map(v -> v.descriptor).toList()));
        return result;
    }
    public List<String> conditionTargets() throws Exception {
        var indexed = (List<?>) field(Conditions.class, "indexedConditions").get(null);
        var result = new String[indexed.size()];
        for (String event : List.of("craft", "pickup", "smelt")) {
            @SuppressWarnings("unchecked")
            var map = (com.google.common.collect.Multimap<Item, Object>) field(Conditions.class, event + "Conds").get(null);
            for (var item : List.copyOf(OracleSupport.items.values())) {
                for (Object info : map.get(item)) {
                    Object condition = field(info.getClass(), "cond").get(info);
                    int index = field(condition.getClass(), "index").getInt(condition);
                    result[index] = item.id + ":" + event;
                }
            }
        }
        return List.of(result);
    }
    public boolean visible(String id) { return TutorialRegistry.getTutorial(id).isActivated(player); }
    public boolean activated(String id) throws Exception { return activatedIds().contains(id); }
    @SuppressWarnings("unchecked") public Set<String> activatedIds() throws Exception {
        return Set.copyOf((Set<String>) field(TutorialData.class, "activatedTuts").get(data));
    }
    public long[] bits() throws Exception { return ((BitSet) field(TutorialData.class, "savedConditions").get(data)).toLongArray(); }
    public boolean dirty() throws Exception { return field(TutorialData.class, "dirty").getBoolean(data); }
    public boolean acquired() throws Exception { return field(TutorialData.class, "tutorialAcquired").getBoolean(data); }
    public int misakaID() { return data.getMisakaID(); }
    public boolean record(String itemId, String event, int meta, boolean client) throws Exception {
        var item = OracleSupport.items.get(itemId);
        if (item == null) item = new Item(itemId);
        var stack = new ItemStack(item, meta);
        long[] before = bits();
        player.worldObj.isRemote = client;
        switch (event) {
            case "CRAFT" -> conditions.onItemCraft(new ItemCraftedEvent(player, stack));
            case "PICKUP" -> conditions.onItemPickup(new ItemPickupEvent(player, new EntityItem(player.worldObj, 0, 0, 0, stack)));
            case "SMELT" -> conditions.onItemSmelt(new ItemSmeltedEvent(player, stack));
            default -> throw new IllegalArgumentException(event);
        }
        player.worldObj.isRemote = false;
        return !Arrays.equals(before, bits());
    }
    public void tick() { data.tick(); }
    public List<String> log() { return List.copyOf(OracleSupport.logs); }
    public void clearLog() { OracleSupport.logs.clear(); }
    public void changeConfig(boolean give) { cn.academy.core.AcademyCraft.config.enabled = give; }
    public Map<String,Object> save() {
        var tag = new NBTTagCompound();
        data.toNBT(tag);
        return new HashMap<>(tag.values);
    }
    public void restore(long[] bits, Collection<String> ids, boolean acquired) {
        var tag = new NBTTagCompound();
        tag.values.put("savedConditions", BitSet.valueOf(bits));
        tag.values.put("activatedTuts", new HashSet<>(ids));
        tag.values.put("tutorialAcquired", acquired);
        tag.values.put("misakaID", data.getMisakaID());
        EntityData.forget(player);
        data = TutorialData.get(player);
        data.fromNBT(tag);
    }
    public void restart() {
        var tag = new NBTTagCompound();
        data.toNBT(tag);
        EntityData.forget(player);
        data = TutorialData.get(player);
        data.fromNBT(tag);
    }
    public List<Double> dropPosition() {
        if (player.worldObj.spawned.isEmpty()) return List.of();
        var entity = player.worldObj.spawned.get(0);
        return List.of(entity.x, entity.y, entity.z);
    }
    public boolean rightClick(boolean client) {
        player.worldObj.isRemote = client;
        var stack = new ItemStack(ModuleTutorial.itemTutorial);
        var result = ModuleTutorial.itemTutorial.onItemRightClick(stack, player.worldObj, player);
        player.worldObj.isRemote = false;
        return result == stack;
    }
    /** Extra finite conditions prove meta wildcard and meta-filter boundaries without replacing their logic. */
    public List<Boolean> checkMetaConditions() throws Exception {
        var item = new Item("meta-probe");
        Condition craftWild = Conditions.itemCrafted(item);
        Condition craft7 = Conditions.itemCrafted(item, 7);
        Condition pickup7 = Conditions.itemPickup(item, 7);
        Condition smelt7 = Conditions.itemSmelted(item, 7);
        record("meta-probe", "CRAFT", 3, false);
        var result = new ArrayList<Boolean>();
        result.add(craftWild.test(player));
        result.add(craft7.test(player));
        record("meta-probe", "CRAFT", 7, true);
        result.add(craft7.test(player));
        record("meta-probe", "CRAFT", 7, false);
        result.add(craft7.test(player));
        result.add(pickup7.test(player));
        result.add(smelt7.test(player));
        record("meta-probe", "PICKUP", 7, false);
        result.add(pickup7.test(player));
        result.add(smelt7.test(player));
        record("meta-probe", "SMELT", 7, false);
        result.add(smelt7.test(player));
        return result;
    }
    public List<Integer> schedulerProbe() {
        var scheduler = new cn.lambdalib.util.helper.TickScheduler();
        var result = new ArrayList<Integer>();
        int[] tick = {0};
        scheduler.every(3).atOnly(Side.SERVER).run(() -> result.add(tick[0]));
        scheduler.every(10).atOnly(Side.SERVER).run(() -> result.add(-tick[0]));
        scheduler.every(1).atOnly(Side.CLIENT).run(() -> result.add(999));
        for (tick[0]=1; tick[0]<=30; tick[0]++) scheduler.runTick();
        return result;
    }
    private static Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
