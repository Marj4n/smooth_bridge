package org.marj4n.smoothbridge.build;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.objectweb.asm.*;
import java.util.concurrent.atomic.AtomicInteger;

class ScreenDelayPatchTest {
    static byte[] fixture() {
        ClassWriter c = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        c.visit(61, Opcodes.ACC_PUBLIC, "ScreenFixture", null, "java/lang/Object", null);
        c.visitField(Opcodes.ACC_PUBLIC, "kilt$setScreen", "Ljava/lang/Runnable;", null, null)
                .visitEnd();
        var ctor = c.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();
        var m = c.visitMethod(Opcodes.ACC_PUBLIC, "kilt$setScreenAfterLoading", "()V", null, null);
        m.visitCode();
        Label done = new Label();
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitFieldInsn(
                Opcodes.GETFIELD, "ScreenFixture", "kilt$setScreen", "Ljava/lang/Runnable;");
        m.visitJumpInsn(Opcodes.IFNULL, done);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitFieldInsn(
                Opcodes.GETFIELD, "ScreenFixture", "kilt$setScreen", "Ljava/lang/Runnable;");
        m.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/lang/Runnable", "run", "()V", true);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitInsn(Opcodes.ACONST_NULL);
        m.visitFieldInsn(
                Opcodes.PUTFIELD, "ScreenFixture", "kilt$setScreen", "Ljava/lang/Runnable;");
        m.visitLabel(done);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        c.visitEnd();
        return c.toByteArray();
    }

    static class Loader extends ClassLoader {
        Class<?> load(byte[] b) {
            return defineClass(null, b, 0, b.length);
        }
    }

    @Test
    void reentrantCallbackRunsOnceAndRetainsNextCallback() throws Exception {
        var type = new Loader().load(ScreenDelayPatch.patch(fixture()));
        var instance = type.getConstructor().newInstance();
        var field = type.getField("kilt$setScreen");
        var drain = type.getMethod("kilt$setScreenAfterLoading");
        var runs = new AtomicInteger();
        var next = new AtomicInteger();
        field.set(
                instance,
                (Runnable)
                        () -> {
                            runs.incrementAndGet();
                            try {
                                drain.invoke(instance);
                                field.set(instance, (Runnable) next::incrementAndGet);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
        drain.invoke(instance);
        assertEquals(1, runs.get());
        assertNotNull(field.get(instance));
        drain.invoke(instance);
        assertEquals(1, next.get());
        assertNull(field.get(instance));
        drain.invoke(instance);
        assertEquals(1, next.get());
    }

    @Test
    void rejectsAlreadyPatchedOrUnknownBytecode() {
        assertThrows(
                IllegalStateException.class,
                () -> ScreenDelayPatch.patch(ScreenDelayPatch.patch(fixture())));
    }
}
