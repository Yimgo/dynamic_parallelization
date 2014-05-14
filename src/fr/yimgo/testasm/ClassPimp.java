package fr.yimgo.testasm;

import java.util.Iterator;
import java.util.ListIterator;
import java.util.concurrent.Callable;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import org.pmw.tinylog.Logger;

public class ClassPimp {
  public ClassPimp() { }

  public void transform (ClassNode cn, String methodName) {
    Iterator<MethodNode> i = cn.methods.iterator();

    while (i.hasNext()) {
      MethodNode mn = i.next();
      if (methodName.equals(mn.name)) {
        Logger.info("Pimpin {0}.{1}", cn.name, methodName);
        try {
          // determine loop bounddaries
          ListIterator<AbstractInsnNode> ii = mn.instructions.iterator();
          AbstractInsnNode beforeLoop = null, loopStart = null, loopEnd = null, afterLoop = null;
          while (ii.hasNext()) {
            AbstractInsnNode in = ii.next();
            if (in instanceof JumpInsnNode) {
              if (loopStart == null) {
                loopStart = in;
              } else {
                loopEnd = in;
              }
            } else if (in instanceof LabelNode) {
              if (beforeLoop == null) {
                beforeLoop = in;
              } else {
                afterLoop = in;
              }
            }
          }
          Logger.trace(beforeLoop);
          Logger.trace(loopStart);
          Logger.trace(loopEnd);
          Logger.trace(afterLoop);
          // add futures in initialization
          addFuturesArray(mn, beforeLoop);
          // TODO: put inner loop code in inner class
          registerClass(createInnerClass());
          // TODO: replace inner loop code by calls to innerClass.call()

          // TODO: manage futures after the loop

          // consistency check
          Class<?> pimped = registerClass(cn);
          Object pimpedInstance = (Object) pimped.getConstructor().newInstance();
          Logger.trace(pimped.getMethod(methodName, int.class).invoke(pimpedInstance, 2));
        } catch (Throwable t) {
          Logger.error(t);
        }
        break;
      }
    }
  }

  public void addFuturesArray(MethodNode mn, AbstractInsnNode beforeLoop) {
    Logger.info("Adding futures array to {0}()", mn.name);
    ListIterator<AbstractInsnNode> i = mn.instructions.iterator(mn.instructions.indexOf(beforeLoop) - 1);

    // List<Future<Double>> futures = new ArrayList<Future<Double>>();
    i.add(new TypeInsnNode(Opcodes.NEW, "java/util/ArrayList"));
    i.add(new InsnNode(Opcodes.DUP));
    i.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false));
    i.add(new VarInsnNode(Opcodes.ASTORE, 3));
    mn.maxLocals += 1;
    mn.maxStack += 2;
  }

  public void replaceInnerCode(MethodNode mn, AbstractInsnNode loopStart, AbstractInsnNode loopEnd) {
    return;
  }

  public void addMethod(ClassNode cn) {
    Logger.info("Adding {0}.call()", cn.name);
    MethodNode callNode = new MethodNode(Opcodes.ACC_PUBLIC, "call", "()V", null, null);
    // Class <?> inner = MyClassLoader.getInstance().loadClass("fr.yimgo.testasm.TestInner");
    //  MyClassLoader.getInstance()
    callNode.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "fr/yimgo/testasm/MyClassLoader", "getInstance", "()Lfr/yimgo/testasm/MyClassLoader;", false));
    //  .loadClass("fr.yimgo.testasm.TestInner");
    callNode.instructions.add(new LdcInsnNode("fr.yimgo.testasm.TestInner"));
    callNode.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "fr/yimgo/testasm/MyClassLoader", "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;", false));
    callNode.instructions.add(new VarInsnNode(Opcodes.ASTORE, 1));
    callNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
    // Callable innerInstance = (Callable) inner.getConstructor(int.class).newInstance(2);
    //  inner.getConstructor(int.class)
    callNode.instructions.add(new InsnNode(Opcodes.ICONST_1));
    callNode.instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Class"));
    callNode.instructions.add(new InsnNode(Opcodes.DUP));
    callNode.instructions.add(new InsnNode(Opcodes.ICONST_0));
    callNode.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/Integer", "TYPE", "Ljava/lang/Class;"));
    callNode.instructions.add(new InsnNode(Opcodes.AASTORE));
    callNode.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getConstructor", "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;", false));
    //  .newInstance(2);
    callNode.instructions.add(new InsnNode(Opcodes.ICONST_1));
    callNode.instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
    callNode.instructions.add(new InsnNode(Opcodes.DUP));
    callNode.instructions.add(new InsnNode(Opcodes.ICONST_0));
    callNode.instructions.add(new InsnNode(Opcodes.ICONST_2));
    callNode.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
    callNode.instructions.add(new InsnNode(Opcodes.AASTORE));
    callNode.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Constructor", "newInstance", "([Ljava/lang/Object;)Ljava/lang/Object;", false));
    callNode.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/util/concurrent/Callable"));
    callNode.instructions.add(new VarInsnNode(Opcodes.ASTORE, 2));

    // System.out.println(inner.getMethod("call").invoke(innerInstance));
    callNode.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"));
    //  inner.getMethod("call")
    callNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
    callNode.instructions.add(new LdcInsnNode("call"));
    callNode.instructions.add(new InsnNode(Opcodes.ICONST_0));
    callNode.instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Class"));
    callNode.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false));
    //  .invoke(innerInstance)
    callNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
    callNode.instructions.add(new InsnNode(Opcodes.ICONST_0));
    callNode.instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
    callNode.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Method", "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false));

    //  System.out.println();
    callNode.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false));

    callNode.instructions.add(new InsnNode(Opcodes.RETURN));

    callNode.maxStack = 5;
    callNode.maxLocals = 3;
    cn.methods.add(callNode);
  }

  public ClassNode createInnerClass() {
    Logger.info("Creating inner class fr/yimgo/testasm/TestInner");

    ClassNode cn = new ClassNode();
    cn.version = Opcodes.V1_8;
    cn.access = Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL + Opcodes.ACC_SUPER;
    cn.name = "fr/yimgo/testasm/TestInner";
    cn.superName = "java/lang/Object";
    cn.signature = "Ljava/lang/Object;Ljava/util/concurrent/Callable<Ljava/lang/Double;>;";
    cn.interfaces.add("java/util/concurrent/Callable");
    /* TODO: determine all the values accessed by the instructions in the inner-loop. */
    cn.fields.add(new FieldNode(Opcodes.ACC_FINAL, "val$base", "I", null, null));

    /* constructor */
    MethodNode constructorNode = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "(I)V", null, null);
    constructorNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    constructorNode.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
    constructorNode.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, cn.name, "val$base", "I"));
    constructorNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    constructorNode.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
    constructorNode.instructions.add(new InsnNode(Opcodes.RETURN));
    constructorNode.maxStack = 2;
    constructorNode.maxLocals = 2;
    cn.methods.add(constructorNode);
    /* call() */
    MethodNode callNode = new MethodNode(Opcodes.ACC_PUBLIC, "call", "()Ljava/lang/Double;", null, null);
    callNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    callNode.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, cn.name, "val$base", "I"));
    callNode.instructions.add(new InsnNode(Opcodes.I2D));
    callNode.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "sqrt", "(D)D", false));
    callNode.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false));
    callNode.instructions.add(new InsnNode(Opcodes.ARETURN));
    callNode.maxStack = 2;
    callNode.maxLocals = 1;
    cn.methods.add(callNode);

    return cn;
  }

  public static Class<?> registerClass(ClassNode cn) throws Throwable {
    MyClassLoader cl = MyClassLoader.getInstance();
    ClassWriter cw = new ClassWriter(0);
    cn.accept(cw);
    return cl.defineClass(cn.name.replace("/", "."), cw.toByteArray());
  }
}
