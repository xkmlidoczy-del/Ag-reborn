package cn.academy.port.highpower;

import cn.academy.port.highpower.mixin.NativeAnchorPostMixinPlugin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.EventBus;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** A one-use identity receipt for NEW422 landing and NEW432 side-absorption native damage only. */
public final class AnchorDamagePostProof {
    private static final ThreadLocal<Frame> ACTIVE = new ThreadLocal<>();
    private static final int MAX_STACK_FRAMES = 128;
    private static final String DISPATCH = "(Lnet/neoforged/bus/api/Event;[Lnet/neoforged/bus/api/EventListener;)Lnet/neoforged/bus/api/Event;";
    private AnchorDamagePostProof() {}

    private static final class Frame {
        final LivingEntity entity;
        final DamageContainer container;
        LivingDamageEvent.Post event;
        boolean dispatched, consumed, invalid;
        int dispatchDepth = -1;
        Frame(LivingEntity entity, DamageContainer container) {
            this.entity = entity;
            this.container = container;
        }
    }

    public static boolean available() { return NativeAnchorPostMixinPlugin.isReviewedPostSupported(); }

    /** Called only around certified LivingEntity/Player.actuallyHurt -> CommonHooks invocations. */
    public static void nativePost(LivingEntity entity, DamageContainer container, Operation<Void> original) {
        Frame parent = ACTIVE.get();
        Frame frame = null;
        try {
            if (parent != null) {
                // Reentry refuses the defense, while the outer native Post still needs its
                // own identity proof for ordinary exact accounting after the nested hurt.
                HighPowerCombat.abandonAnchorFall(parent.entity);
                HighPowerCombat.abandonAnchorFall(entity);
            }
            if (HighPowerCombat.anchorPostGuardPending(entity)) {
                if (available() && entity != null && container != null) frame = new Frame(entity, container);
                else HighPowerCombat.abandonAnchorPostGuard(entity);
            }
        } catch (RuntimeException | LinkageError failure) {
            NativeAnchorPostMixinPlugin.disableAfterCallbackFailure(failure);
            HighPowerCombat.abandonAnchorPostGuard(entity);
            if (parent != null) abandon(parent);
        }
        if (frame == null && parent == null) {
            original.call(entity, container);
            return;
        }
        if (frame == null) ACTIVE.remove(); else ACTIVE.set(frame);
        try {
            original.call(entity, container);
        } finally {
            // Nested native dispatch has its own scope. Subscriber exceptions cannot leak it,
            // and refusing a defense never removes the outer accounting identity proof.
            if (frame != null) { frame.invalid = true; frame.event = null; }
            if (parent == null) ACTIVE.remove(); else ACTIVE.set(parent);
        }
    }

    /** Called at the single reviewed bus post, after the native Post constructor has returned. */
    public static Event nativeBusPost(IEventBus bus, Event event, Operation<Event> original,
                                     LivingEntity entity, DamageContainer container) {
        Frame frame = ACTIVE.get();
        if (frame == null) return original.call(bus, event);
        if (frame.dispatched) {
            // A subscriber-created nested CommonHooks event must still meet the outer identity
            // guard. Suspend its receipt without abandoning the pending high-power hurt frame.
            LivingDamageEvent.Post outer = frame.event;
            frame.event = null;
            try { return original.call(bus, event); }
            finally { if (!frame.invalid) frame.event = outer; }
        }
        try {
            if (!frame.invalid && !frame.dispatched && available() && frame.entity == entity
                    && frame.container == container && bus == NeoForge.EVENT_BUS && bus.getClass() == EventBus.class
                    && event != null && event.getClass() == LivingDamageEvent.Post.class) {
                int depth = dispatchDepth(MAX_STACK_FRAMES / 2);
                if (depth < 0) abandon(frame);
                else {
                    frame.dispatchDepth = depth;
                    frame.dispatched = true; // Claim before invoking any subscriber.
                    frame.event = (LivingDamageEvent.Post)event;
                }
            } else abandon(frame);
        } catch (RuntimeException | LinkageError failure) {
            NativeAnchorPostMixinPlugin.disableAfterCallbackFailure(failure);
            abandon(frame);
        }
        try {
            return original.call(bus, event);
        } finally {
            frame.event = null;
            frame.invalid = true;
        }
    }

    /** Equal field values, another object, reentry and replay never authenticate a Post. */
    public static boolean matches(LivingDamageEvent.Post event) {
        Frame frame = ACTIVE.get();
        if (frame == null || frame.invalid || frame.consumed || !frame.dispatched
                || frame.event != event || event == null) return false;
        if (!available()) { abandon(frame); return false; }
        try {
            int depth = dispatchDepth(MAX_STACK_FRAMES);
            if (depth < 0) {
                // A deliberately deep same-object repost must not release the accounting gate.
                // Refuse its defense; the bounded original dispatch can still settle normally.
                HighPowerCombat.abandonAnchorFall(frame.entity);
                return false;
            }
            if (depth != frame.dispatchDepth + 1) return false;
        } catch (RuntimeException | LinkageError failure) {
            NativeAnchorPostMixinPlugin.disableAfterCallbackFailure(failure);
            abandon(frame);
            return false;
        }
        frame.consumed = true;
        return true;
    }

    /** A direct repost of the same object adds a second actual bus dispatch, not a new receipt. */
    private static int dispatchDepth(int maximumFrames) {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> {
            var iterator = frames.iterator();
            int visited = 0, depth = 0;
            while (iterator.hasNext()) {
                if (++visited > maximumFrames) return -1;
                StackWalker.StackFrame frame = iterator.next();
                if (frame.getDeclaringClass() == EventBus.class && frame.getMethodName().equals("post")
                        && frame.getDescriptor().equals(DISPATCH)) depth++;
            }
            return depth;
        });
    }

    private static void abandon(Frame frame) {
        frame.invalid = true;
        frame.event = null;
        HighPowerCombat.abandonAnchorPostGuard(frame.entity);
    }
}
