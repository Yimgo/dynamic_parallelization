package fr.yimgo.testasm;

import java.util.Arrays;
import java.util.ListIterator;
/* ugly, fix this asap */
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public class TestASM {
  static public ClassNode createTestClass() {
    ClassNode cn = new ClassNode();

    /* standard attributes */
    cn.version = Opcodes.V1_8;
    cn.access = Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER;
    cn.name = "fr/yimgo/testasm/Test";
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
  public static void main(String... args) {
    ClassWriter cw = new ClassWriter(0);
    createTestClass().accept(cw);
    MyClassLoader cl = MyClassLoader.getInstance();

    try {
        Class<Object> c = cl.defineClass("fr.yimgo.testasm.Test", cw.toByteArray());
        Object test = c.newInstance();
        System.out.println(c);
        System.out.println(test);
    } catch (Throwable t) {
        System.err.println(t);
        t.printStackTrace(System.err);
    }
  }
}
