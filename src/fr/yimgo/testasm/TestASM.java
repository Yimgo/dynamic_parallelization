package fr.yimgo.testasm;

import java.util.Arrays;
import java.util.ListIterator;
/* ugly, fix this asap */
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public class TestASM {
  static public ClassNode createInnerClass() {
    ClassNode cn = new ClassNode();

    /* standard attributes */
    cn.version = Opcodes.V1_8;
    cn.access = Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER;
    cn.name = "fr/yimgo/testasm/TestInner";
    cn.superName = "java/lang/Object";

    /* constructor */
    MethodNode constructorNode = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    constructorNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    constructorNode.instructions.add(new InsnNode(Opcodes.DUP));
    constructorNode.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
    constructorNode.instructions.add(new InsnNode(Opcodes.RETURN));
    constructorNode.maxStack = 2;
    constructorNode.maxLocals = 1;
    cn.methods.add(constructorNode);

    return cn;
  }
  static public ClassNode createOuterClass() {
    ClassNode cn = new ClassNode();

    /* standard attributes */
    cn.version = Opcodes.V1_8;
    cn.access = Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER;
    cn.name = "fr/yimgo/testasm/TestOuter";
    cn.superName = "java/lang/Object";

    /* constructor */
    MethodNode constructorNode = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    constructorNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    constructorNode.instructions.add(new InsnNode(Opcodes.DUP));
    constructorNode.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
    constructorNode.instructions.add(new InsnNode(Opcodes.RETURN));
    constructorNode.maxStack = 2;
    constructorNode.maxLocals = 1;
    cn.methods.add(constructorNode);

    MethodNode callNode = new MethodNode(Opcodes.ACC_PUBLIC, "call", "()V", null, null);
    callNode.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "fr/yimgo/testasm/MyClassLoader", "getInstance", "()Lfr/yimgo/testasm/MyClassLoader;", false));
    callNode.instructions.add(new LdcInsnNode("fr.yimgo.testasm.TestInner"));
    callNode.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "fr/yimgo/testasm/MyClassLoader", "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;", false));
    callNode.instructions.add(new VarInsnNode(Opcodes.ASTORE, 1));
    callNode.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"));
    callNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
    callNode.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false));
    callNode.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"));
    callNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
    callNode.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "newInstance", "()Ljava/lang/Object;", false));
    callNode.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false));
    callNode.instructions.add(new InsnNode(Opcodes.RETURN));
    callNode.maxStack = 4;
    callNode.maxLocals = 2;
    cn.methods.add(callNode);

    return cn;
  }
  static Class<?> registerClass(ClassNode cn) throws Throwable {
    MyClassLoader cl = MyClassLoader.getInstance();
    ClassWriter cw = new ClassWriter(0);
    cn.accept(cw);
    return cl.defineClass(cn.name.replace("/", "."), cw.toByteArray());
  }
  public static void main(String... args) {
    try {
        Class<?> inner = registerClass(createInnerClass());
        Class<?> outer = registerClass(createOuterClass());
        System.out.println(inner);
        System.out.println(outer);
        Object iInstance = (Object) inner.newInstance();
        Object oInstance = (Object) outer.newInstance();
        System.out.println(iInstance);
        System.out.println(oInstance);
        outer.getMethod("call").invoke(oInstance);
    } catch (Throwable t) {
        t.printStackTrace(System.err);
    }
  }
}
