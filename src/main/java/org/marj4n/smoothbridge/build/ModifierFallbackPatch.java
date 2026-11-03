package org.marj4n.smoothbridge.build;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Registry values require unique identities, including fallback values for unused native-mod Forge data. */
public final class ModifierFallbackPatch {
    public static byte[] patch(byte[] original) {
        ClassNode c = new ClassNode();
        new ClassReader(original).accept(c, 0);
        int count = 0;
        for (MethodNode m : c.methods)
            for (AbstractInsnNode n : m.instructions.toArray()) {
                if (!(n instanceof FieldInsnNode f)
                        || f.getOpcode() != Opcodes.GETSTATIC
                        || !f.name.equals("INSTANCE")
                        || !(f.owner.equals("net/minecraftforge/common/world/NoneBiomeModifier")
                                || f.owner.equals(
                                        "net/minecraftforge/common/world/NoneStructureModifier")))
                    continue;
                AbstractInsnNode next = n.getNext();
                while (next != null && next.getOpcode() < 0) next = next.getNext();
                if (!(next instanceof MethodInsnNode call)
                        || !call.owner.equals("com/mojang/serialization/Codec"))
                    throw new IllegalStateException("Unexpected modifier fallback ABI");
                if (call.name.equals("orElse")
                        && call.desc.equals(
                                "(Ljava/util/function/Consumer;Ljava/lang/Object;)Lcom/mojang/serialization/Codec;")) {
                    call.name = "orElseGet";
                    call.desc =
                            "(Ljava/util/function/Consumer;Ljava/util/function/Supplier;)Lcom/mojang/serialization/Codec;";
                } else if (call.name.equals("unit")
                        && call.desc.equals(
                                "(Ljava/lang/Object;)Lcom/mojang/serialization/Codec;")) {
                    call.desc = "(Ljava/util/function/Supplier;)Lcom/mojang/serialization/Codec;";
                } else
                    throw new IllegalStateException(
                            "Unexpected fallback codec invocation: " + call.name + call.desc);
                Handle metafactory =
                        new Handle(
                                Opcodes.H_INVOKESTATIC,
                                "java/lang/invoke/LambdaMetafactory",
                                "metafactory",
                                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                                false);
                var factory =
                        new InvokeDynamicInsnNode(
                                "get",
                                "()Ljava/util/function/Supplier;",
                                metafactory,
                                Type.getMethodType("()Ljava/lang/Object;"),
                                new Handle(
                                        Opcodes.H_NEWINVOKESPECIAL,
                                        f.owner,
                                        "<init>",
                                        "()V",
                                        false),
                                Type.getMethodType("()L" + f.owner + ";"));
                m.instructions.set(n, factory);
                count++;
            }
        if (count != 4)
            throw new IllegalStateException(
                    "Expected four biome/structure fallback codec sites, got " + count);
        ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        c.accept(out);
        return out.toByteArray();
    }
}
