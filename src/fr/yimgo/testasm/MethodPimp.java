package fr.yimgo.testasm;

import java.util.ListIterator;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import java.util.concurrent.Callable;

public class MethodPimp {
  public MethodPimp() { }

  public void transform(MethodNode mn, String className) {
    AbstractInsnNode beforeLoop = null, loopStart = null, loopEnd = null, afterLoop = null;

    System.out.println("Original instructions:");
    printOpcodes(mn.instructions);

    /*
      only consider the main loop.
      Loop ===
        label1:
          load i, n
        if_icmpge i, n goto label2
        ... intructions ...
        goto label1
        label2:
          ... end of function ...
    */

    @SuppressWarnings("unchecked") ListIterator<AbstractInsnNode> i = mn.instructions.iterator();
    while (i.hasNext()) {
      AbstractInsnNode in = i.next();
      if (in instanceof JumpInsnNode) {
        System.out.println("JumpInsnNode found");
        if (loopStart == null) {
          loopStart = in;
        } else {
          loopEnd = in;
        }
      } else if (in instanceof LabelNode) {
        System.out.println("LabelNode found");
        if (beforeLoop == null) {
          beforeLoop = in;
        } else {
          afterLoop = in;
        }
      }
    }

    /* insert pre-loop instructions before label1 */
    transformInit(mn, 0, mn.instructions.indexOf(beforeLoop));
    transformInnerLoop(className, mn, mn.instructions.indexOf(loopStart), mn.instructions.indexOf(loopEnd));

    System.out.println("Pimped instructions:");
    printOpcodes(mn.instructions);

    try {ClassWriter cw = new ClassWriter(0);
    createInnerClass(className, mn, 0, 0).accept(cw);

    MyClassLoader cl = new MyClassLoader();
    Class c = cl.defineClass("fr.yimgo.testasm.SequentialSqrt_Parallelized$1", cw.toByteArray());
    Callable test = (Callable) c.getConstructors()[0].newInstance(2);
    System.out.println(test.call());
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  public void transformInit(MethodNode mn, int begin, int end) {
    System.out.println("Initialization from " + begin + " to " + end);
    @SuppressWarnings("unchecked") ListIterator<AbstractInsnNode> i = mn.instructions.iterator(begin);
    /*
      = List<Future<Double>> futures = new ArrayList<Future<Double>>();
    */
    i.add(new TypeInsnNode(Opcodes.NEW, "java/util/ArrayList"));
    i.add(new InsnNode(Opcodes.DUP));
    i.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false));
    i.add(new VarInsnNode(Opcodes.ASTORE, 3));
    mn.maxLocals += 1;
    mn.maxStack += 2;
  }

  public void transformInnerLoop(String className, MethodNode mn, int begin, int end) {
    System.out.println("Inner loop from " + begin + " to " + end);
    @SuppressWarnings("unchecked") ListIterator<AbstractInsnNode> i = mn.instructions.iterator(end);

    /* remove all the instructions within the inner loop. */
    while(i.hasPrevious() && i.previousIndex() > begin) {
      i.previous();
      i.remove();
    }

    /*
      final int base = i;
      futures.add(pool.submit(new Callable<Double> () {
          public Double call() throws Exception {
              return Double.valueOf(Math.sqrt((double) base));
          }
      }));
    */
    i.add(new VarInsnNode(Opcodes.ILOAD, 2));
    i.add(new VarInsnNode(Opcodes.ISTORE, 4));
    i.add(new VarInsnNode(Opcodes.ALOAD, 1));
    i.add(new FieldInsnNode(Opcodes.GETSTATIC, className, "pool", "Ljava/util/concurrent/ExecutorService;"));
    i.add(new TypeInsnNode(Opcodes.NEW, "fr/yimgo/testasm/ParallelSqrt$1"));
    i.add(new InsnNode(Opcodes.DUP));
    i.add(new VarInsnNode(Opcodes.ILOAD, 4));
    i.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "fr/yimgo/testasm/ParallelSqrt$1", "<init>", "(I)V", false));
    i.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/concurrent/ExecutorService", "submit", "(Ljava/util/concurrent/Callable;)Ljava/util/concurrent/Future;", false));
    i.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true));
    i.add(new InsnNode(Opcodes.POP));
    i.add(new IincInsnNode(3, 1));
    mn.maxLocals += 1;
    mn.maxStack += 2;
  }

  /*
    TODO:
     * detect the number of fields needed
     * dynamically determine the maxs
     * set the inner outer class/methods
    @returns {org.objectweb.asm.tree.ClassNode} the inner class handling @params{instructions] bound by @params{begin} and @params{end}.
  */
  public ClassNode createInnerClass(String className, MethodNode mn, int begin, int end) {
    ClassNode cn = new ClassNode();
    cn.version = Opcodes.V1_8;
    //cn.access = Opcodes.ACC_FINAL + Opcodes.ACC_SUPER;
    cn.access = Opcodes.ACC_PUBLIC;
    cn.name = className + "$1";
    cn.superName = "java/lang/Object";
    cn.signature = "Ljava/lang/Object;Ljava/util/concurrent/Callable<Ljava/lang/Double;>;";
    cn.interfaces.add("java/util/concurrent/Callable");
    /* TODO: determine all the values accessed by the instructions in the inner-loop. */
    cn.fields.add(new FieldNode(Opcodes.ACC_FINAL + Opcodes.ACC_SYNTHETIC, "val$base", "I", null, null));

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
    /* call() bridge */
    MethodNode bridgeNode = new MethodNode(Opcodes.ACC_PUBLIC + Opcodes.ACC_BRIDGE + Opcodes.ACC_SYNTHETIC, "call", "()Ljava/lang/Object;", null, new String[]{"java/lang/Exception"});
    bridgeNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    bridgeNode.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, cn.name, "call", "()Ljava/lang/Double;", false));
    bridgeNode.instructions.add(new InsnNode(Opcodes.ARETURN));
    bridgeNode.maxStack = 1;
    bridgeNode.maxLocals = 1;
    cn.methods.add(bridgeNode);

    return cn;
  }

  public void printOpcodes(InsnList instructions) {
    @SuppressWarnings("unchecked") ListIterator<AbstractInsnNode> i = instructions.iterator();
    while (i.hasNext()) {
      System.out.println(i.next().getOpcode());
    }
  }
}
