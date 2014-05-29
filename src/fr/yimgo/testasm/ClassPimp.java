package fr.yimgo.testasm;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import org.pmw.tinylog.Logger;

public class ClassPimp {
  private int futuresListPosition, localesListPosition;
  private List<Object> local;

  public ClassPimp() { }

  public void transform (ClassNode cn, String methodName) {
    Iterator<MethodNode> i = cn.methods.iterator();

    while (i.hasNext()) {
      MethodNode mn = i.next();
      if (methodName.equals(mn.name)) {
        Logger.info("Pimpin {0}.{1}", cn.name, methodName);
        try {
          // determine loop boundaries
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

          // put inner loop code in inner class
          registerClass(createInnerClass(mn, loopStart, loopEnd));
          // remove inner code
          removeInnerCode(mn, loopStart, loopEnd);
          // add futures in initialization
          addFuturesArray(mn, beforeLoop);
          // dumping context
          dumpLocalsToList(mn, beforeLoop);
          adaptFrameAppend(mn, beforeLoop);
          // replace inner loop code by calls to innerClass.call()
          replaceInnerCode(cn, mn, loopStart, loopEnd);
          // manage futures after the loop
          adaptAfterCode(mn, afterLoop);

          // consistency check
          Class<?> pimped = registerClass(cn);
          Object pimpedInstance = (Object) pimped.getConstructor().newInstance();
          ExecutorService pool = Executors.newFixedThreadPool(2);
          pimped.getDeclaredField("pool").set(pimpedInstance, pool);
          Logger.trace(pimped.getMethod(methodName, Integer.class).invoke(pimpedInstance, 5));
          pool.shutdown();
        } catch (Throwable t) {
          Logger.error(t);
        }
        break;
      }
    }
  }

  public void addFuturesArray(MethodNode mn, AbstractInsnNode beforeLoop) {
    Logger.info("Adding futures array to {0}()", mn.name);

    // insert the list initialization before the main loop
    ListIterator<AbstractInsnNode> i = mn.instructions.iterator(mn.instructions.indexOf(beforeLoop));

    // List<Future<?>> futures = new ArrayList<Future<?>>();
    i.add(new TypeInsnNode(Opcodes.NEW, "java/util/ArrayList"));
    i.add(new InsnNode(Opcodes.DUP));
    i.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false));
    i.add(new VarInsnNode(Opcodes.ASTORE, mn.maxLocals));

    futuresListPosition = mn.maxLocals++;
  }

  public void dumpLocalsToList(MethodNode mn, AbstractInsnNode loopStart) {
    Logger.info("Dumping local frame to List");

    ListIterator<AbstractInsnNode> it = mn.instructions.iterator(mn.instructions.indexOf(loopStart));
    //it.next();

    it.add(new TypeInsnNode(Opcodes.NEW, "java/util/concurrent/CopyOnWriteArrayList"));
    it.add(new InsnNode(Opcodes.DUP));
    it.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/util/concurrent/CopyOnWriteArrayList", "<init>", "()V", false));
    it.add(new VarInsnNode(Opcodes.ASTORE, mn.maxLocals));

    for (int i = 0; i < mn.maxLocals; i += 1) {
      it.add(new VarInsnNode(Opcodes.ALOAD, mn.maxLocals));
      it.add(new VarInsnNode(Opcodes.ALOAD, i));
      it.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true));
      it.add(new InsnNode(Opcodes.POP));
    }

    localesListPosition = mn.maxLocals++;
  }

  public void adaptFrameAppend(MethodNode mn, AbstractInsnNode beforeLoop) {
    Logger.info("Taking into account the new lists into the F_APPEND instructions");

    ListIterator<AbstractInsnNode> i = mn.instructions.iterator(mn.instructions.indexOf(beforeLoop));

    i.next(); // seek for the frame node
    try {
      List oldLocal = ((FrameNode) i.next()).local;
      List newLocal = new ArrayList<Object>(oldLocal);
      newLocal.add(0, "fr/yimgo/testasm/SequentialSqrt");
      newLocal.add(1, "java/lang/Integer");
      newLocal.add("java/util/List");
      newLocal.add("java/util/List");
      local = newLocal;
      i.set(new FrameNode(Opcodes.F_FULL, newLocal.size(), newLocal.toArray(), 0, new Object[] {}));
    } catch (Throwable t) {
      Logger.warn("Frame node not found");
      Logger.trace(t);
    }
  }

  public void removeInnerCode(MethodNode mn, AbstractInsnNode loopStart, AbstractInsnNode loopEnd) {
    Logger.info("Removing old inner code");

    ListIterator<AbstractInsnNode> it = mn.instructions.iterator(mn.instructions.indexOf(loopStart));
    it.next();

    // FIXME: don't remove the increment instructions
    while (it.hasNext() && it.nextIndex() < mn.instructions.indexOf(loopEnd)) {
      it.next();
      it.remove();
    }
  }

  public void replaceInnerCode(ClassNode cn, MethodNode mn, AbstractInsnNode loopStart, AbstractInsnNode loopEnd) {
    Logger.info("Adding {0}.pool as a method member", cn.name);
    cn.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "pool", "Ljava/util/concurrent/ExecutorService;", null, null));

    int classPosition, runnablePosition;

    Logger.info("Replacing inner code");
    ListIterator<AbstractInsnNode> i = mn.instructions.iterator(mn.instructions.indexOf(loopEnd));
    i.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "fr/yimgo/testasm/MyClassLoader", "getInstance", "()Lfr/yimgo/testasm/MyClassLoader;", false));
    i.add(new LdcInsnNode("fr.yimgo.testasm.TestInner"));
    i.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "fr/yimgo/testasm/MyClassLoader", "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;", false));
    i.add(new VarInsnNode(Opcodes.ASTORE, mn.maxLocals));
    classPosition = mn.maxLocals++;
    i.add(new VarInsnNode(Opcodes.ALOAD, classPosition));
    i.add(new InsnNode(Opcodes.ICONST_1));
    i.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Class"));
    i.add(new InsnNode(Opcodes.DUP));
    i.add(new InsnNode(Opcodes.ICONST_0));
    i.add(new LdcInsnNode("java.util.List"));
    i.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false));
    i.add(new InsnNode(Opcodes.AASTORE));
    i.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getConstructor", "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;", false));
    i.add(new InsnNode(Opcodes.ICONST_1));
    i.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
    i.add(new InsnNode(Opcodes.DUP));
    i.add(new InsnNode(Opcodes.ICONST_0));
    i.add(new VarInsnNode(Opcodes.ALOAD, localesListPosition));
    i.add(new InsnNode(Opcodes.AASTORE));
    i.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Constructor", "newInstance", "([Ljava/lang/Object;)Ljava/lang/Object;", false));
    i.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Runnable"));
    i.add(new VarInsnNode(Opcodes.ASTORE, mn.maxLocals));
    runnablePosition = mn.maxLocals++;
    i.add(new VarInsnNode(Opcodes.ALOAD, futuresListPosition));
    i.add(new VarInsnNode(Opcodes.ALOAD, 0));
    i.add(new FieldInsnNode(Opcodes.GETFIELD, cn.name, "pool", "Ljava/util/concurrent/ExecutorService;"));
    i.add(new VarInsnNode(Opcodes.ALOAD, runnablePosition));
    i.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/concurrent/ExecutorService", "submit", "(Ljava/lang/Runnable;)Ljava/util/concurrent/Future;", true));
    i.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true));
    i.add(new InsnNode(Opcodes.POP));
    i.add(new VarInsnNode(Opcodes.ALOAD, 3));
    i.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false));
    i.add(new InsnNode(Opcodes.ICONST_1));
    i.add(new InsnNode(Opcodes.IADD));
    i.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
    i.add(new VarInsnNode(Opcodes.ASTORE, 3)); // FIXME: 3 ?

    mn.maxLocals += 2;
    mn.maxStack += 1;
  }

  public void adaptAfterCode(MethodNode mn, AbstractInsnNode afterLoop) {
    Logger.info("Waiting for futures termination");
    ListIterator<AbstractInsnNode> i = mn.instructions.iterator(mn.instructions.indexOf(afterLoop));

    int iteratorPosition, futurePosition;

    i.next();
    i.next();
    i.set(new FrameNode(Opcodes.F_CHOP, 0, null, 0, null));

    i.add(new VarInsnNode(Opcodes.ALOAD, futuresListPosition));
    i.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true));
    iteratorPosition = futuresListPosition + 2;
    i.add(new VarInsnNode(Opcodes.ASTORE, iteratorPosition));
    LabelNode ln1 = new LabelNode(new Label());
    i.add(ln1);
    i.add(new FrameNode(Opcodes.F_APPEND, 1, new Object[] {"java/util/Iterator"}, 0, null));
    i.add(new VarInsnNode(Opcodes.ALOAD, iteratorPosition));
    i.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true));
    LabelNode ln2 = new LabelNode(new Label());
    i.add(new JumpInsnNode(Opcodes.IFEQ, ln2));
    i.add(new VarInsnNode(Opcodes.ALOAD, iteratorPosition));
    i.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true));
    i.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/util/concurrent/Future"));
    futurePosition = iteratorPosition + 1;
    i.add(new VarInsnNode(Opcodes.ASTORE, futurePosition));
    i.add(new VarInsnNode(Opcodes.ALOAD, futurePosition));
    i.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/concurrent/Future", "get", "()Ljava/lang/Object;", true));
    i.add(new InsnNode(Opcodes.POP));
    i.add(new JumpInsnNode(Opcodes.GOTO, ln1));
    i.add(ln2);
    i.add(new FrameNode(Opcodes.F_CHOP, 1, null, 0, null));

    for (int j = 0; j < local.size() - 1; ++j) {
      i.add(new VarInsnNode(Opcodes.ALOAD, localesListPosition));
      i.add(new LdcInsnNode(new Integer(j)));
      i.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true));
      i.add(new TypeInsnNode(Opcodes.CHECKCAST, (String) local.get(j)));
      i.add(new VarInsnNode(Opcodes.ASTORE, j));

    }
  }

  public ClassNode createInnerClass(MethodNode mn, AbstractInsnNode loopStart, AbstractInsnNode loopEnd) {
    Logger.info("Creating inner class fr/yimgo/testasm/TestInner");

    ClassNode cn = new ClassNode();
    cn.version = Opcodes.V1_8;
    cn.access = Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER;
    cn.name = "fr/yimgo/testasm/TestInner";
    cn.superName = "java/lang/Object";
    cn.interfaces.add("java/lang/Runnable");
    /* TODO: determine all the values accessed by the instructions in the inner-loop. */
    cn.fields.add(new FieldNode(Opcodes.ACC_PRIVATE + Opcodes.ACC_FINAL, "frame", "Ljava/util/List;", "Ljava/util/List<Ljava/lang/Object;>;", null));

    /* constructor */
    MethodNode constructorNode = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/util/List;)V", "(Ljava/util/List<Ljava/lang/Object;>;)V", null);
    constructorNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    constructorNode.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
    constructorNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    constructorNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
    constructorNode.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, cn.name, "frame", "Ljava/util/List;"));
    constructorNode.instructions.add(new InsnNode(Opcodes.RETURN));
    constructorNode.maxStack = 2;
    constructorNode.maxLocals = 2;
    cn.methods.add(constructorNode);

    /* run() */
    MethodNode runNode = new MethodNode(Opcodes.ACC_PUBLIC, "run", "()V", null, null);

    ListIterator<AbstractInsnNode> i = mn.instructions.iterator(mn.instructions.indexOf(loopStart));
    i.next();

    runNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    runNode.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, cn.name, "frame", "Ljava/util/List;"));
    runNode.instructions.add(new InsnNode(Opcodes.DUP));
    runNode.instructions.add(new VarInsnNode(Opcodes.ASTORE, 2));
    runNode.instructions.add(new InsnNode(Opcodes.MONITORENTER));


    while (i.hasNext() && i.nextIndex() < mn.instructions.indexOf(loopEnd)) {
      // FIXME: don't copy the increment instructions
      AbstractInsnNode in = i.next();
      if (in.getOpcode() == Opcodes.ALOAD) {
        runNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        runNode.instructions.add(new LdcInsnNode(new Integer(((VarInsnNode) in).var)));
        runNode.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true));
      } else if (in.getOpcode() == Opcodes.ASTORE) {
        runNode.instructions.add(new VarInsnNode(Opcodes.ASTORE, 1));
        runNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        runNode.instructions.add(new LdcInsnNode(new Integer(((VarInsnNode) in).var)));
        runNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        runNode.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "set", "(ILjava/lang/Object;)Ljava/lang/Object;", true));
        runNode.instructions.add(new InsnNode(Opcodes.POP));
      } else {
        if (in.getOpcode() == Opcodes.INVOKEVIRTUAL && (((MethodInsnNode) in).owner.equals("java/lang/Double") || ((MethodInsnNode) in).owner.equals("java/lang/Integer"))) {
          runNode.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, ((MethodInsnNode) in).owner));
        }
        runNode.instructions.add(in.clone(null));
      }
    }

    runNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
    runNode.instructions.add(new InsnNode(Opcodes.MONITOREXIT));

    runNode.instructions.add(new InsnNode(Opcodes.RETURN));
    runNode.maxStack = 10;
    runNode.maxLocals = 10;
    cn.methods.add(runNode);

    return cn;
  }

  public static Class<?> registerClass(ClassNode cn) throws Throwable {
    MyClassLoader cl = MyClassLoader.getInstance();
    ClassWriter cw = new ClassWriter(0);
    cn.accept(cw);
    return cl.defineClass(cn.name.replace("/", "."), cw.toByteArray());
  }

  public static void printOpcodes(InsnList instructions) {
    ListIterator<AbstractInsnNode> i = instructions.iterator();
    while (i.hasNext()) {
      System.out.println(i.next().getOpcode());
    }
  }
}
