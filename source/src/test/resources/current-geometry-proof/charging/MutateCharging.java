import java.nio.file.*;
import org.objectweb.asm.*;

/** Negative controls operate only on temporary copies of the actual compiled renderer. */
public final class MutateCharging {
    public static void main(String[] args) throws Exception {
        ClassReader reader = new ClassReader(Files.readAllBytes(Path.of(args[0])));
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        int[] edits = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor visitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, visitor) {
                    @Override public void visitCode() {
                        super.visitCode();
                        if (name.equals("renderPaths") && args[2].equals("rng")) {
                            // Consume the exact RNG whose stability the unchanged frozen harness asserts.
                            super.visitLdcInsn(Type.getObjectType("cn/academy/port/client/ClassicArcGeometry"));
                            super.visitLdcInsn("ROTATION_RANDOM");
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", false);
                            super.visitInsn(Opcodes.DUP);
                            super.visitInsn(Opcodes.ICONST_1);
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Field", "setAccessible", "(Z)V", false);
                            super.visitInsn(Opcodes.ACONST_NULL);
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Field", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                            super.visitTypeInsn(Opcodes.CHECKCAST, "java/util/Random");
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Random", "nextLong", "()J", false);
                            super.visitInsn(Opcodes.POP2); edits[0]++;
                        }
                    }
                    @Override public void visitFieldInsn(int opcode, String owner, String field, String desc) {
                        if (name.equals("vertex") && args[2].equals("coordinate") && owner.equals("net/minecraft/world/phys/Vec3") && field.equals("x")) {
                            field = "z"; edits[0]++;
                        }
                        super.visitFieldInsn(opcode, owner, field, desc);
                    }
                    @Override public void visitVarInsn(int opcode, int index) {
                        if (name.equals("vertex") && args[2].equals("uv") && opcode == Opcodes.FLOAD && index == 3) {
                            index = 4; edits[0]++;
                        }
                        super.visitVarInsn(opcode, index);
                    }
                };
            }
        }, 0);
        if (edits[0] != 1) throw new AssertionError("Mutation must hit exactly one instruction: " + edits[0]);
        Files.createDirectories(Path.of(args[1]).getParent()); Files.write(Path.of(args[1]), writer.toByteArray());
        System.out.println("MUTATION " + args[2] + " actual-bytecode-edits=" + edits[0]);
    }
}
