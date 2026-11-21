package cn.academy.port.highpower;

import static org.objectweb.asm.Opcodes.*;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Reads a narrow set of damage expressions once per entity class. Unknown expressions use native
 * damage.
 */
final class DamageShape {
    private static final String HURT = "(Lnet/minecraft/world/damagesource/DamageSource;F)Z";
    private static final String KEY = "Lnet/minecraft/network/syncher/EntityDataAccessor;";
    private static final String DATA = "net/minecraft/network/syncher/SynchedEntityData";
    static final Shape UNKNOWN = new Shape(List.of(), null);
    private static final ClassValue<Shape> CACHE =
            new ClassValue<>() {
                @Override
                protected Shape computeValue(Class<?> type) {
                    try {
                        return inspect(type);
                    } catch (Exception | LinkageError unavailable) {
                        return UNKNOWN;
                    }
                }
            };

    static Shape of(LivingEntity target) {
        return CACHE.get(target.getClass());
    }

    interface Amount {
        float get(LivingEntity target, DamageSource source, float raw, boolean open)
                throws ReflectiveOperationException;
    }

    record Shape(List<Amount> chain, EntityDataAccessor<Float> vitality) {
        float restore(LivingEntity target, DamageSource source, float raw, float incoming) {
            if (!(incoming > 0 && incoming < raw) || chain.isEmpty()) return incoming;
            float closed = raw, open = raw;
            try {
                for (Amount step : chain) {
                    closed = step.get(target, source, closed, false);
                    open = step.get(target, source, open, true);
                }
            } catch (ReflectiveOperationException inaccessible) {
                return incoming;
            }
            return closed == incoming && Float.isFinite(open) && open >= closed ? open : incoming;
        }
    }

    private static ClassNode read(Class<?> owner) throws Exception {
        try (InputStream in =
                owner.getResourceAsStream("/" + Type.getInternalName(owner) + ".class")) {
            if (in == null) throw new IllegalArgumentException("missing class");
            var node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static MethodNode method(ClassNode node, String name, String desc) {
        return node.methods.stream()
                .filter(m -> m.name.equals(name) && m.desc.equals(desc))
                .findFirst()
                .orElse(null);
    }

    private static List<AbstractInsnNode> ops(MethodNode m) {
        var list = new ArrayList<AbstractInsnNode>();
        for (var op : m.instructions) if (op.getOpcode() >= 0) list.add(op);
        return list;
    }

    private static Shape inspect(Class<?> type) throws Exception {
        List<Amount> steps = new ArrayList<>();
        Field health =
                healthKey(
                        type,
                        type.getMethod("getHealth").getDeclaringClass(),
                        "getHealth",
                        new HashSet<>());
        boolean ownDebit = false;
        for (Class<?> owner = type;
                owner != null && !owner.getName().startsWith("net.minecraft.");
                owner = owner.getSuperclass()) {
            if (!LivingEntity.class.isAssignableFrom(owner)) break;
            var node = read(owner);
            var m = method(node, "hurt", HURT);
            if (m == null) continue;
            var calc = new Reader(owner, node, m);
            steps.add(calc.amount());
            if (health != null && calc.writesVitality(health)) ownDebit = true;
        }
        @SuppressWarnings("unchecked")
        var key = ownDebit ? (EntityDataAccessor<Float>) health.get(null) : null;
        return new Shape(List.copyOf(steps), key);
    }

    /**
     * Only accepts a public getter (possibly forwarded) which unboxes a single synchronized float.
     */
    private static Field healthKey(Class<?> type, Class<?> owner, String name, Set<String> seen)
            throws Exception {
        if (owner == LivingEntity.class
                || !seen.add(owner.getName() + "." + name)
                || seen.size() > 5) return null;
        var m = method(read(owner), name, "()F");
        if (m == null) return null;
        var o = ops(m);
        if (o.size() < 3 || o.getFirst().getOpcode() != ALOAD || o.getLast().getOpcode() != FRETURN)
            return null;
        if (o.size() == 3
                && o.get(1) instanceof MethodInsnNode c
                && c.getOpcode() == INVOKEVIRTUAL
                && c.desc.equals("()F"))
            return healthKey(type, type.getMethod(c.name).getDeclaringClass(), c.name, seen);
        if (o.size() != 7
                || !(o.get(2) instanceof FieldInsnNode k)
                || k.getOpcode() != GETSTATIC
                || !k.desc.equals(KEY)
                || !(o.get(3) instanceof MethodInsnNode g)
                || !g.owner.equals(DATA)
                || !g.name.equals("get")
                || !(o.get(4) instanceof TypeInsnNode t)
                || !t.desc.equals("java/lang/Float")
                || !(o.get(5) instanceof MethodInsnNode f)
                || !f.name.equals("floatValue")) return null;
        boolean storage =
                o.get(1) instanceof FieldInsnNode fld
                        && fld.getOpcode() == GETFIELD
                        && fld.desc.equals("L" + DATA + ";");
        storage |=
                o.get(1) instanceof MethodInsnNode getter
                        && getter.name.equals("getEntityData")
                        && getter.desc.equals("()L" + DATA + ";");
        return storage ? field(owner, k) : null;
    }

    private static Field field(Class<?> type, FieldInsnNode k) throws Exception {
        Class<?> decl = Class.forName(k.owner.replace('/', '.'), false, type.getClassLoader());
        Field f = decl.getDeclaredField(k.name);
        if (!f.trySetAccessible()) throw new IllegalAccessException();
        return f;
    }

    private record Expr(boolean input, Amount amount) {}

    private static Expr constant(float n) {
        return new Expr(false, (t, s, a, o) -> n);
    }

    private static final class Reader {
        final Class<?> owner;
        final ClassNode node;
        final MethodNode method;
        final List<AbstractInsnNode> ops;
        final Frame<SourceValue>[] frames;
        final MethodInsnNode call;
        final List<Amount> assignments = new ArrayList<>();

        Reader(Class<?> owner, ClassNode node, MethodNode m) throws Exception {
            this.owner = owner;
            this.node = node;
            this.method = m;
            ops = ops(m);
            frames = new Analyzer<>(new SourceInterpreter()).analyze(node.name, m);
            var calls =
                    ops.stream()
                            .filter(x -> x instanceof MethodInsnNode c && c.desc.equals(HURT))
                            .toList();
            if (calls.size() != 1)
                throw new IllegalArgumentException("ambiguous damage delegation");
            call = (MethodInsnNode) calls.getFirst();
            var frame = frame(call);
            if (!variable(frame.getStack(frame.getStackSize() - 3), ALOAD, 0)
                    || !variable(frame.getStack(frame.getStackSize() - 2), ALOAD, 1))
                throw new IllegalArgumentException("different receiver/source");
            if (!(call.getOpcode() == INVOKESPECIAL && call.name.equals("hurt"))) {
                var helper = method(node, call.name, HURT);
                if (helper == null) throw new IllegalArgumentException("unverified damage helper");
                var code = ops(helper);
                boolean nativeStart = false;
                for (int i = 0; i < code.size(); i++) {
                    var x = code.get(i);
                    if (x instanceof VarInsnNode v && v.getOpcode() == FSTORE && v.var == 2) break;
                    if (x instanceof MethodInsnNode c
                            && c.owner.equals(
                                    "net/neoforged/neoforge/common/damagesource/DamageContainer")
                            && c.name.equals("<init>")) {
                        nativeStart =
                                i >= 2
                                        && isVar(code.get(i - 1), FLOAD, 2)
                                        && isVar(code.get(i - 2), ALOAD, 1);
                        break;
                    }
                }
                if (!nativeStart) throw new IllegalArgumentException("custom damage pipeline");
            }
            for (int i = 0; i < ops.indexOf(call); i++)
                if (isVar(ops.get(i), FSTORE, 2)) assignments.add(assignment(i));
        }

        private Frame<SourceValue> frame(AbstractInsnNode n) {
            return frames[method.instructions.indexOf(n)];
        }

        private static boolean isVar(AbstractInsnNode n, int op, int index) {
            return n instanceof VarInsnNode v && v.getOpcode() == op && v.var == index;
        }

        private static boolean variable(SourceValue v, int op, int index) {
            return v.insns.size() == 1 && isVar(v.insns.iterator().next(), op, index);
        }

        private Expr argument(SourceValue v, int depth) throws Exception {
            if (depth > 24 || v.insns.isEmpty())
                throw new IllegalArgumentException("unknown expression");
            if (v.insns.size() == 2) return alternative(v, depth);
            if (v.insns.size() != 1) throw new IllegalArgumentException("ambiguous expression");
            var n = v.insns.iterator().next();
            var f = frame(n);
            int op = n.getOpcode();
            if (isVar(n, FLOAD, 2))
                return new Expr(
                        true,
                        (t, s, a, open) -> {
                            for (Amount clamp : assignments) a = clamp.get(t, s, a, open);
                            return a;
                        });
            if (n instanceof VarInsnNode var && op == FLOAD)
                return argument(f.getLocal(var.var), depth + 1);
            if (op == FSTORE || op == I2F)
                return argument(f.getStack(f.getStackSize() - 1), depth + 1);
            if (n instanceof LdcInsnNode l && l.cst instanceof Number num)
                return constant(num.floatValue());
            if (op >= FCONST_0 && op <= FCONST_2) return constant(op - FCONST_0);
            if (op >= ICONST_M1 && op <= ICONST_5) return constant(op - ICONST_0);
            if (n instanceof IntInsnNode i && (op == BIPUSH || op == SIPUSH))
                return constant(i.operand);
            if (n instanceof FieldInsnNode k
                    && op == GETFIELD
                    && variable(f.getStack(f.getStackSize() - 1), ALOAD, 0)) {
                Field fld = field(owner, k);
                if (fld.getType() == float.class || fld.getType() == int.class)
                    return new Expr(false, (t, s, a, o) -> fld.getFloat(t));
            }
            boolean min =
                    n instanceof MethodInsnNode c
                            && c.owner.equals("java/lang/Math")
                            && c.name.equals("min")
                            && c.desc.equals("(FF)F");
            if (min || op == FMUL || op == FDIV) {
                Expr l = argument(f.getStack(f.getStackSize() - 2), depth + 1),
                        r = argument(f.getStack(f.getStackSize() - 1), depth + 1);
                if (l.input == r.input) throw new IllegalArgumentException("unproven bound");
                return new Expr(
                        true,
                        (t, s, a, o) -> {
                            float left = l.amount.get(t, s, a, o), right = r.amount.get(t, s, a, o);
                            if (min) return o ? (l.input ? left : right) : Math.min(left, right);
                            return op == FMUL ? left * right : left / right;
                        });
            }
            throw new IllegalArgumentException("unrecognized damage arithmetic");
        }

        private Expr alternative(SourceValue v, int depth) throws Exception {
            var branches = v.insns.stream().sorted(Comparator.comparingInt(ops::indexOf)).toList();
            int a = ops.indexOf(branches.getFirst()), b = ops.indexOf(branches.getLast());
            for (int i = a - 1; i >= 2; i--) {
                if (!(ops.get(i) instanceof JumpInsnNode branch)
                        || (branch.getOpcode() != IFEQ && branch.getOpcode() != IFNE)) continue;
                int dest = method.instructions.indexOf(branch.label);
                if (dest <= method.instructions.indexOf(ops.get(a))
                        || dest > method.instructions.indexOf(ops.get(b))) continue;
                if (!(ops.get(i - 1) instanceof FieldInsnNode k)
                        || !k.desc.equals("Z")
                        || !isVar(ops.get(i - 2), ALOAD, 0)) continue;
                if (a + 1 >= ops.size()
                        || !(ops.get(a + 1) instanceof JumpInsnNode go)
                        || go.getOpcode() != GOTO
                        || method.instructions.indexOf(go.label)
                                <= method.instructions.indexOf(ops.get(b))) continue;
                var flag = field(owner, k);
                var first = argument(new SourceValue(1, branches.getFirst()), depth + 1);
                var other = argument(new SourceValue(1, branches.getLast()), depth + 1);
                return new Expr(
                        first.input && other.input,
                        (t, s, raw, o) ->
                                (flag.getBoolean(t) == (branch.getOpcode() == IFEQ) ? first : other)
                                        .amount.get(t, s, raw, o));
            }
            throw new IllegalArgumentException("unverified branch");
        }

        private Amount assignment(int i) throws Exception {
            if (i < 6
                    || !isVar(ops.get(i - 3), FLOAD, 2)
                    || (ops.get(i - 1).getOpcode() != FMUL && ops.get(i - 1).getOpcode() != FDIV))
                return conditionalCap(i);
            if (!(ops.get(i - 4) instanceof JumpInsnNode guard)
                    || guard.getOpcode() != IFEQ
                    || method.instructions.indexOf(guard.label)
                            <= method.instructions.indexOf(ops.get(i))
                    || method.instructions.indexOf(guard.label)
                            > method.instructions.indexOf(ops.get(i + 1))
                    || !isVar(ops.get(i - 6), ALOAD, 0))
                throw new IllegalArgumentException("unknown scale guard");
            Amount active;
            if (ops.get(i - 5) instanceof FieldInsnNode field
                    && field.getOpcode() == GETFIELD
                    && field.desc.equals("Z")) {
                var flag = field(owner, field);
                active = (t, s, a, o) -> flag.getBoolean(t) ? 1 : 0;
            } else if (ops.get(i - 5) instanceof MethodInsnNode predicate
                    && predicate.getOpcode() == INVOKEVIRTUAL
                    && predicate.desc.equals("()Z")) {
                var query = owner.getMethod(predicate.name);
                var definition = method(read(query.getDeclaringClass()), predicate.name, "()Z");
                if (definition == null) throw new IllegalArgumentException("unknown phase guard");
                for (var op : ops(definition)) {
                    int code = op.getOpcode();
                    if (op instanceof MethodInsnNode get) {
                        if (!get.desc.equals("()F")
                                || !(get.name.equals("getMaxHealth")
                                        || get.name.equals("getHealth")
                                        || healthKey(
                                                        owner,
                                                        owner.getMethod(get.name)
                                                                .getDeclaringClass(),
                                                        get.name,
                                                        new HashSet<>())
                                                != null))
                            throw new IllegalArgumentException("unknown phase query");
                    } else if (!(op instanceof JumpInsnNode jump
                                    && definition.instructions.indexOf(jump.label)
                                            > definition.instructions.indexOf(op)
                            || code == ALOAD
                            || code == GETFIELD
                            || code == ICONST_0
                            || code == ICONST_1
                            || code == LDC
                            || code == FCONST_0
                            || code == FCMPG
                            || code == FCMPL
                            || code == FDIV
                            || code == FMUL
                            || code == IRETURN))
                        throw new IllegalArgumentException("phase guard has side effects");
                }
                active =
                        (t, s, a, o) -> {
                            if (!t.getClass().getMethod(query.getName()).equals(query))
                                throw new IllegalAccessException("overridden guard");
                            return Boolean.TRUE.equals(query.invoke(t)) ? 1 : 0;
                        };
            } else throw new IllegalArgumentException("unknown phase condition");
            var scale =
                    argument(
                            frame(ops.get(i - 1))
                                    .getStack(frame(ops.get(i - 1)).getStackSize() - 1),
                            0);
            if (scale.input) throw new IllegalArgumentException("nonconstant phase scale");
            return (target, source, amount, open) -> {
                if (active.get(target, source, amount, open) == 0) return amount;
                float factor = scale.amount.get(target, source, amount, open);
                return ops.get(i - 1).getOpcode() == FMUL ? amount * factor : amount / factor;
            };
        }

        private Amount conditionalCap(int i) throws Exception {
            if (i < 5
                    || !(ops.get(i - 1) instanceof LdcInsnNode value)
                    || !(value.cst instanceof Float limit)
                    || !(ops.get(i - 2) instanceof JumpInsnNode j)
                    || (j.getOpcode() != IFLE && j.getOpcode() != IFLT)
                    || (ops.get(i - 3).getOpcode() != FCMPL && ops.get(i - 3).getOpcode() != FCMPG)
                    || !(ops.get(i - 4) instanceof LdcInsnNode cmp)
                    || !value.cst.equals(cmp.cst)
                    || !isVar(ops.get(i - 5), FLOAD, 2)
                    || i + 1 >= ops.size()
                    || method.instructions.indexOf(j.label)
                            > method.instructions.indexOf(ops.get(i + 1))
                    || method.instructions.indexOf(j.label)
                            <= method.instructions.indexOf(ops.get(i)))
                throw new IllegalArgumentException("unverified assignment");
            Class<?> exempt = null;
            if (i >= 8
                    && ops.get(i - 6) instanceof JumpInsnNode gate
                    && gate.label == j.label
                    && gate.getOpcode() == IFNE
                    && ops.get(i - 7) instanceof TypeInsnNode test
                    && test.getOpcode() == INSTANCEOF
                    && isVar(ops.get(i - 8), ALOAD, 1))
                exempt = Class.forName(test.desc.replace('/', '.'), false, owner.getClassLoader());
            Class<?> bypass = exempt;
            return (t, s, a, open) ->
                    open || bypass != null && bypass.isInstance(s) ? a : Math.min(a, limit);
        }

        Amount amount() throws Exception {
            var f = frame(call);
            return argument(f.getStack(f.getStackSize() - 1), 0).amount;
        }

        boolean writesVitality(Field key) throws Exception {
            int debits = 0, writes = 0;
            var input = frame(call).getStack(frame(call).getStackSize() - 1);
            for (int i = ops.indexOf(call) + 1; i < ops.size(); i++) {
                if (!(ops.get(i) instanceof MethodInsnNode write)) continue;
                var operands = frame(write);
                if (operands == null) continue;
                int top = operands.getStackSize() - 1;
                SourceValue amount;
                if (write.owner.equals(DATA) && write.name.equals("set")) {
                    if (top < 2 || !exactKey(operands.getStack(top - 1), key)) continue;
                    writes++;
                    var box = single(operands.getStack(top));
                    if (!(box instanceof MethodInsnNode boxed)
                            || !boxed.owner.equals("java/lang/Float")
                            || !boxed.name.equals("valueOf")
                            || !boxed.desc.equals("(F)Ljava/lang/Float;")) continue;
                    amount = frame(box).getStack(frame(box).getStackSize() - 1);
                } else {
                    if (!write.desc.equals("(F)V")) continue;
                    var setter = method(node, write.name, write.desc);
                    if (setter == null) continue;
                    var body = ops(setter);
                    if (body.size() != 7
                            || !usesKey(body, key)
                            || !isVar(body.get(0), ALOAD, 0)
                            || !isVar(body.get(3), FLOAD, 1)
                            || !(body.get(4) instanceof MethodInsnNode box)
                            || !box.owner.equals("java/lang/Float")
                            || !box.name.equals("valueOf")
                            || !(body.get(5) instanceof MethodInsnNode set)
                            || !set.owner.equals(DATA)
                            || !set.name.equals("set")
                            || !variable(operands.getStack(top - 1), ALOAD, 0)) continue;
                    writes++;
                    amount = operands.getStack(top);
                }
                var maximum = single(amount);
                if (!(maximum instanceof MethodInsnNode max)
                        || !max.owner.equals("java/lang/Math")
                        || !max.name.equals("max")
                        || !max.desc.equals("(FF)F")) continue;
                var args = frame(maximum);
                var left = single(args.getStack(args.getStackSize() - 2));
                var right = single(args.getStack(args.getStackSize() - 1));
                if (left == null || right == null) continue;
                var subtraction =
                        left.getOpcode() == FCONST_0
                                ? right
                                : right.getOpcode() == FCONST_0 ? left : null;
                if (subtraction == null || subtraction.getOpcode() != FSUB) continue;
                var difference = frame(subtraction);
                var old = single(difference.getStack(difference.getStackSize() - 2));
                var debit = single(difference.getStack(difference.getStackSize() - 1));
                if (!(old instanceof MethodInsnNode get)
                        || !get.desc.equals("()F")
                        || !variable(frame(old).getStack(frame(old).getStackSize() - 1), ALOAD, 0)
                        || !(debit instanceof VarInsnNode local)
                        || local.getOpcode() != FLOAD
                        || !variable(input, FLOAD, local.var)) continue;
                var owned =
                        healthKey(
                                owner,
                                owner.getMethod(get.name).getDeclaringClass(),
                                get.name,
                                new HashSet<>());
                if (key.equals(owned)) debits++;
            }
            return writes == 1 && debits == 1;
        }

        private static AbstractInsnNode single(SourceValue value) {
            return value.insns.size() == 1 ? value.insns.iterator().next() : null;
        }

        private static boolean exactKey(SourceValue value, Field key) {
            return single(value) instanceof FieldInsnNode f
                    && f.getOpcode() == GETSTATIC
                    && f.owner.equals(Type.getInternalName(key.getDeclaringClass()))
                    && f.name.equals(key.getName());
        }

        static boolean usesKey(List<AbstractInsnNode> body, Field key) {
            return body.stream()
                    .anyMatch(
                            n ->
                                    n instanceof FieldInsnNode v
                                            && v.getOpcode() == GETSTATIC
                                            && v.name.equals(key.getName())
                                            && v.owner.equals(
                                                    Type.getInternalName(key.getDeclaringClass())));
        }
    }
}
