package cn.academy.port.highpower;

import cn.academy.port.highpower.mixin.NativeBorrowedNeedleMixinPlugin;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Static admission and mutation controls. Actual death/capture callbacks are exercised by GameTests. */
public final class BorrowedNeedleDeathSeamRegressionTest {
    private static final String PLAYER = "net.minecraft.world.entity.player.Player";
    private static final String SERVER = "net.minecraft.server.level.ServerPlayer";
    private static final String INVENTORY = "net/minecraft/world/entity/player/Inventory";
    private static final String MIXIN = "cn.academy.port.highpower.mixin.PlayerBorrowedNeedleDeathMixin";
    private static final String CALLBACK = "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static int checks;

    public static void main(String[] args) throws Exception {
        check(!NativeBorrowedNeedleMixinPlugin.isReviewedDeathDropSupported(), "default admission denies borrowing before transformation");
        Map<String, ClassNode> classes = new HashMap<>();
        for (String key : NativeBorrowedNeedleMixinPlugin.reviewedMethods()) {
            String owner = key.substring(0, key.indexOf('#'));
            if (!classes.containsKey(owner)) classes.put(owner, node(owner));
        }
        check(NativeBorrowedNeedleMixinPlugin.supported(classes), "reviewed pinned native death bytecode is admitted");
        check(!NativeBorrowedNeedleMixinPlugin.supported(null), "null evidence refused");
        for (String owner : classes.keySet()) {
            Map<String, ClassNode> missing = new HashMap<>(classes); missing.remove(owner);
            check(!NativeBorrowedNeedleMixinPlugin.supported(missing), "absent native class: " + owner);
            missing.put(owner, null);
            check(!NativeBorrowedNeedleMixinPlugin.supported(missing), "null native class: " + owner);
        }
        for (String key : NativeBorrowedNeedleMixinPlugin.reviewedMethods()) {
            int split = key.indexOf('#'); String owner = key.substring(0, split), selector = key.substring(split + 1);
            mutate(classes, owner, type -> method(type, selector).instructions.insert(new InsnNode(Opcodes.NOP)), "changed native opcode " + key);
            mutate(classes, owner, type -> type.methods.remove(method(type, selector)), "missing native method " + key);
            mutate(classes, owner, type -> type.methods.add(copy(method(type, selector))), "duplicate native method " + key);
            mutate(classes, owner, type -> method(type, selector).access ^= Opcodes.ACC_STATIC, "staticness " + key);
            mutate(classes, owner, type -> method(type, selector).access = Opcodes.ACC_PRIVATE, "visibility " + key);
        }
        mutate(classes, SERVER, type -> type.methods.add(copy(method(classes.get(PLAYER), "dropEquipment()V"))), "new bypass override");
        mutate(classes, PLAYER, type -> {
            MethodNode drop = method(type, "dropEquipment()V"); MethodInsnNode site = nativeDrop(drop);
            drop.instructions.insert(site, site.clone(new HashMap<>()));
        }, "duplicate exact Inventory.dropAll target");
        mutate(classes, PLAYER, type -> {
            MethodNode drop = method(type, "dropEquipment()V"); drop.instructions.remove(nativeDrop(drop));
        }, "absent exact Inventory.dropAll target");
        mutate(classes, PLAYER, type -> {
            for (AbstractInsnNode instruction : method(type, "dropEquipment()V").instructions)
                if (instruction instanceof JumpInsnNode jump) { jump.setOpcode(Opcodes.IFEQ); break; }
        }, "inverted keepInventory branch");

        ClassNode mixin = node(MIXIN);
        check(hooksSupported(mixin), "compiled hooks are exact, noncancelling and server-only");
        for (String suffix : List.of("academy$borrowedNeedleBeginDeath", "academy$borrowedNeedleDeathDrop")) {
            mutateHook(mixin, suffix, method -> put(annotation(method, INJECT), "cancellable", true), "cancellable callback");
            for (String count : List.of("expect", "allow"))
                mutateHook(mixin, suffix, method -> put(annotation(method, INJECT), count, 2), "relaxed callback count " + count);
            mutateHook(mixin, suffix, method -> put(annotation(method, INJECT), "method", List.of("die")), "broad method hook");
            mutateHook(mixin, suffix, method -> method.tryCatchBlocks.clear(), "failure cancels ordinary death");
        }
        mutateHook(mixin, "academy$borrowedNeedleDeathDrop", method -> put(at(method), "target", "Lnet/minecraft/world/entity/player/Inventory;clearContent()V"), "wrong native invocation");
        mutateHook(mixin, "academy$borrowedNeedleDeathDrop", method -> put(at(method), "shift", new String[]{"Lorg/spongepowered/asm/mixin/injection/At$Shift;", "AFTER"}), "late transfer callback");

        // This deliberately constructed instruction fixture tests the POST-APPLY predicate.
        // It is not presented as a live Mixin transformation or real player death.
        ClassNode installed = installedFixture(classes.get(PLAYER), mixin);
        check(NativeBorrowedNeedleMixinPlugin.installedCallbacksSupported(installed), "installed exact callback witness accepted");
        check(!NativeBorrowedNeedleMixinPlugin.installedCallbacksSupported(classes.get(PLAYER)), "untransformed native Player cannot enable borrowing");
        mutateInstalled(installed, type -> type.methods.removeIf(m -> m.name.endsWith("academy$borrowedNeedleBeginDeath")), "missing committed-death callback");
        mutateInstalled(installed, type -> type.methods.removeIf(m -> m.name.endsWith("academy$borrowedNeedleDeathDrop")), "missing native transfer callback");
        mutateInstalled(installed, type -> {
            MethodNode drop = method(type, "dropEquipment()V"); MethodInsnNode callback = callEnding(drop, "academy$borrowedNeedleDeathDrop");
            drop.instructions.insert(callback, callback.clone(new HashMap<>()));
        }, "duplicate callback installation");
        mutateInstalled(installed, type -> {
            MethodNode drop = method(type, "dropEquipment()V"); MethodInsnNode callback = callEnding(drop, "academy$borrowedNeedleDeathDrop");
            drop.instructions.remove(callback); drop.instructions.insert(nativeDrop(drop), callback);
        }, "callback after native drop");
        mutateInstalled(installed, type -> {
            MethodNode drop = method(type, "dropEquipment()V");
            for (AbstractInsnNode instruction : drop.instructions)
                if (instruction instanceof JumpInsnNode jump) { jump.setOpcode(Opcodes.IFEQ); break; }
        }, "changed native retention branch");
        if (args.length > 0) {
            ClassNode actual = new ClassNode();
            new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(actual, 0);
            check(NativeBorrowedNeedleMixinPlugin.installedCallbacksSupported(actual), "actual native-run transformed Player callback witness");
            mutateInstalled(actual, type -> type.methods.removeIf(m -> m.name.endsWith("academy$borrowedNeedleBeginDeath")), "actual transform without HEAD callback");
            mutateInstalled(actual, type -> type.methods.removeIf(m -> m.name.endsWith("academy$borrowedNeedleDeathDrop")), "actual transform without branch callback");
            mutateInstalled(actual, type -> {
                MethodNode drop = method(type, "dropEquipment()V"); MethodInsnNode callback = callEnding(drop, "academy$borrowedNeedleDeathDrop");
                drop.instructions.insert(callback, callback.clone(new HashMap<>()));
            }, "actual transform with duplicate callback");
            mutateInstalled(actual, type -> {
                MethodNode drop = method(type, "dropEquipment()V"); MethodInsnNode callback = callEnding(drop, "academy$borrowedNeedleDeathDrop");
                drop.instructions.remove(callback); drop.instructions.insert(nativeDrop(drop), callback);
            }, "actual transform with late callback");
            mutateInstalled(actual, type -> {
                MethodNode drop = method(type, "dropEquipment()V");
                for (AbstractInsnNode instruction : drop.instructions)
                    if (instruction instanceof JumpInsnNode jump) { jump.setOpcode(Opcodes.IFEQ); break; }
            }, "actual transform with inverted retention branch");
            receiverTailMutations(actual, "actual native-run transform");
            System.out.println("Actual native-run Player.class postApply witness and mutation controls passed: " + args[0]);
        }
        receiverTailMutations(installed, "constructed installed fixture");
        new NativeBorrowedNeedleMixinPlugin().postApply(PLAYER, installed, MIXIN, null);
        check(!NativeBorrowedNeedleMixinPlugin.isReviewedDeathDropSupported(), "postApply without reviewed preApply never grants authority");

        String json = Files.readString(Path.of("src/main/resources/academy.highpower-borrowed-needle.mixins.json"));
        check(json.contains("\"required\": false") && json.contains("\"defaultRequire\": 0")
                && json.contains("\"mixins\": [\"PlayerBorrowedNeedleDeathMixin\"]"), "optional single narrow mixin configuration");
        String manifest = Files.readString(Path.of("src/main/resources/META-INF/neoforge.mods.toml"));
        check(manifest.split("academy.highpower-borrowed-needle.mixins.json", -1).length == 2, "one mod registration");
        System.out.println("Borrowed needle death seam: " + checks + " static checks passed; native death callback proof is covered separately by runtime GameTests");
    }

    private static boolean hooksSupported(ClassNode type) {
        List<MethodNode> hooks = type.methods.stream().filter(m -> annotation(m, INJECT) != null).toList();
        if (hooks.size() != 2) return false;
        for (MethodNode hook : hooks) {
            AnnotationNode inject = annotation(hook, INJECT);
            if (!hook.desc.equals(CALLBACK) || hook.access != Opcodes.ACC_PRIVATE
                    || !List.of("dropEquipment()V").equals(value(inject, "method")) || Boolean.TRUE.equals(value(inject, "cancellable"))
                    || !Integer.valueOf(0).equals(value(inject, "require")) || !Integer.valueOf(1).equals(value(inject, "expect"))
                    || !Integer.valueOf(1).equals(value(inject, "allow"))) return false;
            AnnotationNode at = at(hook); if (at == null) return false;
            boolean begin = hook.name.equals("academy$borrowedNeedleBeginDeath");
            if (begin) {
                if (!"HEAD".equals(value(at, "value"))) return false;
            } else if (!hook.name.equals("academy$borrowedNeedleDeathDrop") || !"INVOKE".equals(value(at, "value"))
                    || !"Lnet/minecraft/world/entity/player/Inventory;dropAll()V".equals(value(at, "target"))
                    || !(value(at, "shift") instanceof String[] shift) || !shift[1].equals("BEFORE")) return false;
            long guards = 0, admissions = 0, calls = 0;
            for (AbstractInsnNode instruction : hook.instructions) {
                if (instruction instanceof TypeInsnNode check && check.getOpcode() == Opcodes.INSTANCEOF && check.desc.equals(SERVER.replace('.', '/'))) guards++;
                if (instruction instanceof MethodInsnNode call) {
                    if (call.name.equals("cancel") && call.owner.endsWith("/CallbackInfo")) return false;
                    if (call.owner.equals("cn/academy/port/highpower/mixin/NativeBorrowedNeedleMixinPlugin") && call.name.equals("isReviewedDeathDropSupported")) admissions++;
                    if (call.owner.equals("cn/academy/port/highpower/BorrowedNeedleEscrow")) {
                        if (!call.name.equals(begin ? "beginNativeDeath" : "deathDrop") || call.getOpcode() != Opcodes.INVOKESTATIC
                                || !call.desc.equals("(Lnet/minecraft/server/level/ServerPlayer;)V")) return false;
                        calls++;
                    }
                }
            }
            if (guards != 1 || admissions != 1 || calls != 1 || hook.tryCatchBlocks.size() != 2
                    || hook.tryCatchBlocks.stream().noneMatch(t -> "java/lang/RuntimeException".equals(t.type))
                    || hook.tryCatchBlocks.stream().noneMatch(t -> "java/lang/LinkageError".equals(t.type))) return false;
        }
        return true;
    }
    private static ClassNode installedFixture(ClassNode nativePlayer, ClassNode mixin) {
        ClassNode result = copy(nativePlayer); MethodNode drop = method(result, "dropEquipment()V");
        for (MethodNode hook : mixin.methods) {
            if (annotation(hook, INJECT) == null) continue;
            MethodNode merged = copy(hook); result.methods.add(merged);
            MethodInsnNode callback = new MethodInsnNode(Opcodes.INVOKESPECIAL, result.name, merged.name, CALLBACK, false);
            if (hook.name.endsWith("BeginDeath")) drop.instructions.insert(callback);
            else drop.instructions.insertBefore(nativeInventoryField(drop), callback);
        }
        return result;
    }
    private static void receiverTailMutations(ClassNode source, String context) {
        mutateInstalled(source, type -> nativeInventoryField(method(type, "dropEquipment()V")).name = "foreignInventory", context + " wrong receiver field");
        mutateInstalled(source, type -> nativeInventoryField(method(type, "dropEquipment()V")).owner = "net/minecraft/world/entity/LivingEntity", context + " wrong receiver owner");
        mutateInstalled(source, type -> nativeInventoryField(method(type, "dropEquipment()V")).desc = "Ljava/lang/Object;", context + " wrong receiver descriptor");
        mutateInstalled(source, type -> {
            MethodNode drop = method(type, "dropEquipment()V"); drop.instructions.insert(callEnding(drop, "academy$borrowedNeedleDeathDrop"), new InsnNode(Opcodes.NOP));
        }, context + " extra opcode between callback and receiver");
        mutateInstalled(source, type -> {
            MethodNode drop = method(type, "dropEquipment()V"); drop.instructions.remove(nativeInventoryField(drop));
        }, context + " missing native receiver GETFIELD");
        mutateInstalled(source, type -> {
            MethodNode drop = method(type, "dropEquipment()V"); FieldInsnNode field = nativeInventoryField(drop); drop.instructions.insert(field, field.clone(new HashMap<>()));
        }, context + " duplicate native receiver GETFIELD");
        mutateInstalled(source, type -> {
            MethodNode drop = method(type, "dropEquipment()V"); MethodInsnNode callback = callEnding(drop, "academy$borrowedNeedleDeathDrop");
            drop.instructions.remove(callback); drop.instructions.insert(callback);
        }, context + " callback outside keepInventory branch");
    }
    private static FieldInsnNode nativeInventoryField(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions)
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                    && field.owner.equals(PLAYER.replace('.', '/')) && field.name.equals("inventory")
                    && field.desc.equals("Lnet/minecraft/world/entity/player/Inventory;")) return field;
        throw new AssertionError("Missing native Player.inventory receiver");
    }
    private static void mutate(Map<String, ClassNode> source, String owner, Consumer<ClassNode> mutation, String why) {
        Map<String, ClassNode> changed = new HashMap<>(source); ClassNode type = copy(source.get(owner)); changed.put(owner, type); mutation.accept(type);
        check(!NativeBorrowedNeedleMixinPlugin.supported(changed), why + " fails closed");
    }
    private static void mutateHook(ClassNode source, String suffix, Consumer<MethodNode> mutation, String why) {
        ClassNode changed = copy(source); mutation.accept(method(changed, suffix + CALLBACK)); check(!hooksSupported(changed), why + " rejected");
    }
    private static void mutateInstalled(ClassNode source, Consumer<ClassNode> mutation, String why) {
        ClassNode changed = copy(source); mutation.accept(changed);
        check(!NativeBorrowedNeedleMixinPlugin.installedCallbacksSupported(changed), why + " denies admission");
    }
    private static MethodInsnNode nativeDrop(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.equals(INVENTORY) && call.name.equals("dropAll") && call.desc.equals("()V")) return call;
        throw new AssertionError("Missing native Inventory.dropAll");
    }
    private static MethodInsnNode callEnding(MethodNode method, String suffix) {
        for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.name.endsWith(suffix)) return call;
        throw new AssertionError("Missing callback " + suffix);
    }
    private static ClassNode node(String name) throws Exception {
        try (var stream = BorrowedNeedleDeathSeamRegressionTest.class.getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
            if (stream == null) throw new AssertionError("Missing native/compiled class: " + name);
            ClassNode result = new ClassNode(); new ClassReader(stream).accept(result, 0); return result;
        }
    }
    private static MethodNode method(ClassNode type, String selector) { return type.methods.stream().filter(m -> (m.name + m.desc).equals(selector)).findFirst().orElseThrow(); }
    private static ClassNode copy(ClassNode source) { ClassNode result = new ClassNode(); source.accept(result); return result; }
    private static MethodNode copy(MethodNode source) { MethodNode result = new MethodNode(source.access, source.name, source.desc, source.signature, source.exceptions.toArray(String[]::new)); source.accept(result); return result; }
    private static AnnotationNode annotation(MethodNode method, String descriptor) {
        List<AnnotationNode> all = new ArrayList<>(); if (method.visibleAnnotations != null) all.addAll(method.visibleAnnotations); if (method.invisibleAnnotations != null) all.addAll(method.invisibleAnnotations);
        return all.stream().filter(a -> a.desc.equals(descriptor)).findFirst().orElse(null);
    }
    private static AnnotationNode at(MethodNode hook) { Object ats = value(annotation(hook, INJECT), "at"); return ats instanceof List<?> list && list.size() == 1 && list.getFirst() instanceof AnnotationNode at ? at : null; }
    private static Object value(AnnotationNode annotation, String key) {
        if (annotation == null || annotation.values == null) return null;
        for (int i = 0; i < annotation.values.size(); i += 2) if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1); return null;
    }
    private static void put(AnnotationNode annotation, String key, Object value) {
        for (int i = 0; i < annotation.values.size(); i += 2) if (key.equals(annotation.values.get(i))) { annotation.values.set(i + 1, value); return; }
        annotation.values.add(key); annotation.values.add(value);
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
