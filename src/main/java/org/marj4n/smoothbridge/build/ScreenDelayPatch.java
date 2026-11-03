package org.marj4n.smoothbridge.build;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Consume the deferred screen callback before invoking it: Quick Play can re-enter resource loading. */
public final class ScreenDelayPatch {
    public static byte[] patch(byte[] original) {
        ClassNode c = new ClassNode();
        new ClassReader(original).accept(c, 0);
        int count = 0;
        for (MethodNode m : c.methods)
            if (m.name.equals("kilt$setScreenAfterLoading")) {
                for (AbstractInsnNode n : m.instructions.toArray())
                    if (n instanceof MethodInsnNode call
                            && call.owner.equals("java/lang/Runnable")
                            && call.name.equals("run")) {
                        AbstractInsnNode a = n.getNext();
                        while (a != null && a.getOpcode() < 0) a = a.getNext();
                        AbstractInsnNode b = a == null ? null : a.getNext();
                        while (b != null && b.getOpcode() < 0) b = b.getNext();
                        AbstractInsnNode d = b == null ? null : b.getNext();
                        while (d != null && d.getOpcode() < 0) d = d.getNext();
                        if (!(a instanceof VarInsnNode v)
                                || v.getOpcode() != Opcodes.ALOAD
                                || v.var != 0
                                || b == null
                                || b.getOpcode() != Opcodes.ACONST_NULL
                                || !(d instanceof FieldInsnNode f)
                                || f.getOpcode() != Opcodes.PUTFIELD
                                || !f.name.equals("kilt$setScreen")
                                || !f.desc.equals("Ljava/lang/Runnable;"))
                            throw new IllegalStateException(
                                    "Unexpected upstream deferred screen callback ABI");
                        InsnList clear = new InsnList();
                        clear.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        clear.add(new InsnNode(Opcodes.ACONST_NULL));
                        clear.add(new FieldInsnNode(Opcodes.PUTFIELD, f.owner, f.name, f.desc));
                        m.instructions.insertBefore(n, clear);
                        m.instructions.remove(a);
                        m.instructions.remove(b);
                        m.instructions.remove(d);
                        count++;
                    }
            }
        if (count != 1)
            throw new IllegalStateException(
                    "Expected exactly one deferred screen Runnable invocation; found " + count);
        ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        c.accept(out);
        return out.toByteArray();
    }
}
