/*
 * Source-derived from AcademyCraft 1.0.7 ClientRuntime and KeyDelegate.
 * Original copyright (c) Lambda Innovation, 2013-2016; licensed under GPLv3.
 * This isolated candidate deliberately has no game, skill, transport, or session adapter.
 */
package cn.academy.port.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Engine-free owner of the classic client's delegate groups, physical-key history,
 * activation-handler stack, and START-tick/default-preset ordering.
 *
 * <p>The host supplies the source predicates, including their distinction between
 * raw activation, category-gated activation, and ability usability. Predicting a
 * raw activation request is separate from receiving an activation observer event.
 * The coordinator never infers a group rebuild from a flag change.</p>
 *
 * <p>Normal clears retain physical-key history. They abort every active key, even
 * when clearing an unrelated or empty group; a delegate's default abort is a no-op.
 * Concrete skill/context termination and modern player/session/transport ownership
 * belong to a later adapter.</p>
 */
public final class ClassicClientRuntimeCoordinator {
    public static final String DEFAULT_GROUP = "def";
    public static final String OVERRIDE_GROUP = "AC_ClientRuntime";

    /** Source KeyDelegate's four callbacks are all optional and default to no-op. */
    public interface Delegate {
        default void onKeyDown() {}
        default void onKeyTick() {}
        default void onKeyUp() {}
        default void onKeyAbort() {}
    }

    /**
     * Engine-free host facts/actions evaluated at the corresponding source call.
     * inCooldown delegates skill/identifier lookup to the owner without adding a
     * concrete skill or identifier registry here.
     */
    public interface Inputs {
        default boolean runtimeAvailable() { return true; }
        boolean playerInGame();

        /** Source CPData.isActivated(): hasCategory AND the raw activation flag. */
        boolean categoryActivated();

        /** Source raw activation AND overloadFine AND !interfering, without a category gate. */
        boolean canUseAbility();
        boolean terminalOpen();
        boolean keyDown(int keyID);
        boolean inCooldown(Delegate delegate);

        /**
         * Source client prediction: assign the raw flag before the outbound request,
         * without invoking a local activation/deactivation observer. Actual transport
         * belongs to the later owner adapter.
         */
        void requestRawActivation(boolean state);

        /** Replace this owner's AC_ClientRuntime override set with the given keys. */
        void replaceOverrides(int[] keys);
    }

    public interface ActivateHandler {
        String ENDSPECIAL = "endspecial";
        boolean handles();
        void onKeyDown();
        String getHint();
    }

    /**
     * Source node equality is delegate identity only, irrespective of physical key.
     * Hashing intentionally calls delegate.hashCode(), as the original does.
     */
    public static final class DelegateNode {
        public final Delegate delegate;
        public final int keyID;

        private DelegateNode(Delegate delegate, int keyID) {
            this.delegate = delegate;
            this.keyID = keyID;
        }

        public Delegate delegate() { return delegate; }
        public int keyID() { return keyID; }

        @Override
        public boolean equals(Object other) {
            return other instanceof DelegateNode node && node.delegate == delegate;
        }

        @Override
        public int hashCode() { return delegate.hashCode(); }
    }

    public record KeyStateSnapshot(boolean state, boolean realState) {}

    private static final class KeyState {
        boolean state;
        boolean realState;
    }

    private final Inputs inputs;
    private final Map<Integer, DelegateNode> delegates = new TreeMap<>();
    private final Multimap<String, DelegateNode> delegateGroups = ArrayListMultimap.create();
    private final Map<Integer, KeyState> keyStates = new HashMap<>();
    private final LinkedList<ActivateHandler> activateHandlers = new LinkedList<>();
    private Runnable defaultGroupRebuilder = () -> {};
    private boolean ctrlDirty = true;
    private boolean requireFlush;
    private int[] lastOverrides = new int[0];

    public ClassicClientRuntimeCoordinator(Inputs inputs) {
        this.inputs = Objects.requireNonNull(inputs, "inputs");
        installDefaultActivateHandlers();
    }

    public void addKey(int keyID, Delegate delegate) {
        addKey(DEFAULT_GROUP, keyID, delegate);
    }

    public void addKey(String group, int keyID, Delegate delegate) {
        // Preserve the source's integer lookup in a String-keyed group multimap.
        // It does not reject duplicate physical keys: TreeMap.put replaces the
        // winner while the old group node remains. Clearing cannot restore it.
        Preconditions.checkState(!delegateGroups.containsKey(keyID));
        DelegateNode node = new DelegateNode(delegate, keyID);
        delegates.put(keyID, node);
        delegateGroups.put(group, node);
        ctrlDirty = true;
    }

    public Collection<Delegate> getDelegates(String group) {
        return delegateGroups.get(group).stream()
                .map(node -> node.delegate)
                .collect(Collectors.toList());
    }

    /** Source raw view; modifying it has undefined results, as in ClientRuntime. */
    public Multimap<String, DelegateNode> getDelegateRawData() {
        return delegateGroups;
    }

    public void clearKeys(String group) {
        Collection<DelegateNode> nodes = delegateGroups.get(group);
        abortDelegates();
        delegates.values().removeAll(nodes);
        delegateGroups.removeAll(group);
        ctrlDirty = true;
        rebuildOverrides();
    }

    public void clearAllKeys() {
        List<String> all = new ArrayList<>();
        all.addAll(delegateGroups.keySet());
        for (String group : all) {
            clearKeys(group);
        }
        rebuildOverrides();
    }

    public boolean hasActiveDelegate() {
        return delegates.values().stream().anyMatch(node -> getKeyState(node.keyID).state);
    }

    /** State clears before the callback here, unlike the tick's abort branch. */
    public void abortDelegates() {
        keyStates.entrySet().stream()
                .filter(entry -> entry.getValue().state)
                .forEach(entry -> {
                    KeyState state = entry.getValue();
                    state.state = false;
                    if (delegates.containsKey(entry.getKey())) {
                        delegates.get(entry.getKey()).delegate.onKeyAbort();
                    }
                });
    }

    private KeyState getKeyState(int keyID) {
        if (keyStates.containsKey(keyID)) {
            return keyStates.get(keyID);
        }
        KeyState state = new KeyState();
        keyStates.put(keyID, state);
        return state;
    }

    /** Newest registered matching handler wins. */
    public void addActivateHandler(ActivateHandler handler) {
        activateHandlers.addFirst(handler);
    }

    /** Remove only the first equal occurrence, including duplicate registrations. */
    public void removeActiveHandler(ActivateHandler handler) {
        activateHandlers.remove(handler);
    }

    public ActivateHandler getActivateHandler() {
        for (ActivateHandler handler : activateHandlers) {
            if (handler.handles()) {
                return handler;
            }
        }
        throw new RuntimeException();
    }

    /** Called by the later activation-key timing adapter after a short release. */
    public void activateKeyDown() {
        getActivateHandler().onKeyDown();
    }

    /** Returns the source hint token: endspecial, endskill, custom token, or null. */
    public String activationHint() {
        return getActivateHandler().getHint();
    }

    /** Engine-free rendering of source IActivateHandler.getHintTranslated(). */
    public Optional<String> activationHintTranslated(String keyName, Function<String, String> translator) {
        String hint = activationHint();
        return hint == null ? Optional.empty()
                : Optional.of("[" + keyName + "]: " + translator.apply("ac.activate_key." + hint + ".desc"));
    }

    public static ActivateHandler terminatesContext(BooleanSupplier alive, Runnable terminate) {
        return new ActivateHandler() {
            @Override public boolean handles() { return alive.getAsBoolean(); }
            @Override public void onKeyDown() { terminate.run(); }
            @Override public String getHint() { return ENDSPECIAL; }
        };
    }

    private void installDefaultActivateHandlers() {
        addActivateHandler(new ActivateHandler() {
            @Override public boolean handles() { return true; }
            @Override public void onKeyDown() {
                inputs.requestRawActivation(!inputs.categoryActivated());
            }
            @Override public String getHint() { return null; }
        });
        addActivateHandler(new ActivateHandler() {
            @Override public boolean handles() { return hasActiveDelegate(); }
            @Override public void onKeyDown() { abortDelegates(); }
            @Override public String getHint() { return "endskill"; }
        });
    }

    /**
     * Owner callback for the current preset's mapped slots, in source slot order.
     * It must perform registrations through addKey; no skill adapter is built here.
     */
    public void setDefaultGroupRebuilder(Runnable rebuilder) {
        defaultGroupRebuilder = Objects.requireNonNull(rebuilder, "rebuilder");
    }

    /** Clear only def (aborting all active keys), then activate current preset slots. */
    public void updateDefaultGroup() {
        clearKeys(DEFAULT_GROUP);
        defaultGroupRebuilder.run();
    }

    /** Source preset-switch observer is unconditional. */
    public void presetSwitch() { updateDefaultGroup(); }

    /** Client-side call only; the owner applies source SideHelper's event guard. */
    public void presetEdit() { updateDefaultGroup(); }

    /** Repeated source activation observations each rebuild def; they set no flag. */
    public void observerActivate() { updateDefaultGroup(); }

    /** Repeated source deactivation observations each clear all; they set no flag. */
    public void observerDeactivate() { clearAllKeys(); }

    /** Deferred flush stays pending when no client runtime is available. */
    public void requestFlush() { requireFlush = true; }

    /**
     * Source ClientTickEvent START: delegates, dead states, dirty overrides, flush.
     * Each callback precedes its corresponding state change. The four conditions
     * remain separate so callback mutations have the source's subsequent effects.
     */
    public void clientTickStart() {
        if (!inputs.runtimeAvailable()) {
            return;
        }
        for (DelegateNode node : delegates.values()) {
            KeyState state = getKeyState(node.keyID);
            boolean keyDown = inputs.keyDown(node.keyID);
            boolean shouldAbort = !inputs.playerInGame()
                    || inputs.inCooldown(node.delegate)
                    || !inputs.canUseAbility()
                    || inputs.terminalOpen();
            Delegate delegate = node.delegate;

            if (keyDown && state.state && !shouldAbort) {
                delegate.onKeyTick();
            }
            if (keyDown && !state.state && !state.realState && !shouldAbort) {
                delegate.onKeyDown();
                state.state = true;
            }
            if (!keyDown && state.state && !shouldAbort) {
                delegate.onKeyUp();
                state.state = false;
            }
            if (state.state && shouldAbort) {
                delegate.onKeyAbort();
                state.state = false;
            }
            state.realState = keyDown;
        }

        Iterator<Map.Entry<Integer, KeyState>> iter = keyStates.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Integer, KeyState> entry = iter.next();
            if (!entry.getValue().realState && !delegates.containsKey(entry.getKey())) {
                iter.remove();
            }
        }
        if (ctrlDirty) {
            rebuildOverrides();
        }
        if (requireFlush) {
            requireFlush = false;
            updateDefaultGroup();
        }
    }

    private void rebuildOverrides() {
        ctrlDirty = false;
        int[] set = inputs.categoryActivated()
                ? delegates.values().stream().mapToInt(node -> node.keyID).toArray()
                : new int[0];
        lastOverrides = set.clone();
        inputs.replaceOverrides(set);
    }

    /** Read-only diagnostics for a source oracle; histories include removed keys. */
    public Map<Integer, KeyStateSnapshot> keyStateSnapshot() {
        Map<Integer, KeyStateSnapshot> result = new HashMap<>();
        keyStates.forEach((key, state) -> result.put(key, new KeyStateSnapshot(state.state, state.realState)));
        return Collections.unmodifiableMap(result);
    }

    public Map<Integer, Delegate> keyRegistrationSnapshot() {
        Map<Integer, Delegate> result = new TreeMap<>();
        delegates.forEach((key, node) -> result.put(key, node.delegate));
        return Collections.unmodifiableMap(result);
    }

    public Map<String, List<DelegateNode>> groupSnapshot() {
        Map<String, List<DelegateNode>> result = new LinkedHashMap<>();
        for (String group : delegateGroups.keySet()) {
            result.put(group, Collections.unmodifiableList(new ArrayList<>(delegateGroups.get(group))));
        }
        return Collections.unmodifiableMap(result);
    }

    public int[] overrideSnapshot() { return lastOverrides.clone(); }
    public boolean controlDirty() { return ctrlDirty; }
    public boolean flushPending() { return requireFlush; }

}
