package testasm;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.AbstractMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.objectweb.asm.Opcodes.*;

import org.pmw.tinylog.Logger;

public class ClassPimp {
  private int futuresListPosition, localesListPosition;

  public ClassPimp() { }

  public void transform (ClassNode cn, String methodName) {
    Iterator<MethodNode> i = cn.methods.iterator();

    while (i.hasNext()) {
      MethodNode mn = i.next();
      if (methodName.equals(mn.name)) {
        Logger.info("Pimpin {0}.{1}().", cn.name, methodName);
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

          Map.Entry<AbstractInsnNode, AbstractInsnNode> independantBlock = staticAnalysis(cn, mn, beforeLoop, loopStart, loopEnd, afterLoop);
          List<Object> localTypes = resolveLocalTypes(cn, mn, beforeLoop);

          registerClass(generateInnerClass(cn, mn, loopStart, loopEnd, localTypes));


          /*removeInnerCode(cn, mn, beforeLoop, loopStart, loopEnd, independantBlock);
          addFuturesArray(mn, beforeLoop);
          dumpLocalsToList(mn, beforeLoop);
          adaptFrameAppend(cn, mn, beforeLoop, localTypes);
          replaceInnerCode(cn, mn, loopStart, loopEnd);
          adaptAfterCode(mn, afterLoop, localTypes);

          // consistency check
          Class<?> pimped = registerClass(cn);
          Object pimpedInstance = (Object) pimped.getConstructor().newInstance();
          ExecutorService pool = Executors.newFixedThreadPool(2);
          pimped.getDeclaredField("pool").set(pimpedInstance, pool);
          Logger.trace(pimped.getMethod(methodName, Integer.class).invoke(pimpedInstance, 5));
          pool.shutdown();*/
        } catch (Throwable t) {
          Logger.error(t);
        }
        break;
      }
    }
  }

  public Map.Entry<AbstractInsnNode, AbstractInsnNode> staticAnalysis(ClassNode cn, MethodNode mn, AbstractInsnNode beforeLoop, AbstractInsnNode loopStart, AbstractInsnNode loopEnd, AbstractInsnNode afterLoop) {
    Logger.info("Proceeding to static analysis of the method");

    /* compute frames */
    Analyzer a = new Analyzer(new SourceInterpreter());
    Frame[] frames;

    try {
      frames = a.analyze(cn.name, mn);
    } catch (AnalyzerException e) {
      Logger.error(e);
      return null;
    }

    /*
      Keep the instructions that handle local variables before the comparison.
      The frames matching these instructions will allow us to identify blocks that can be kept within the main loop.
     */
    Set<AbstractInsnNode> beforeComparisonVarInsnNodes = new HashSet<AbstractInsnNode>();

    for (int i = mn.instructions.indexOf(beforeLoop) + 1; i < mn.instructions.indexOf(loopStart); ++i) {
      AbstractInsnNode ain = mn.instructions.get(i);
      if (ain instanceof VarInsnNode) {
        beforeComparisonVarInsnNodes.add(ain);
      }
    }

    /*
      Compute the blocks modifying the variables accessed before the comparison.
      For each instruction previously stored, look at the instructions in the frame and determine the blocks.
     */
    List<Map.Entry<AbstractInsnNode, AbstractInsnNode>> comparisonVarUpdateBlocks = new ArrayList();

    Iterator<AbstractInsnNode> nodesIt = beforeComparisonVarInsnNodes.iterator();
    while (nodesIt.hasNext()) {
      VarInsnNode vin = (VarInsnNode) nodesIt.next();
      Set<AbstractInsnNode> insns = ((SourceValue) frames[mn.instructions.indexOf(vin)].getLocal(((VarInsnNode) vin).var)).insns;
      insns.forEach((insn) -> {
        if (mn.instructions.indexOf(insn) > mn.instructions.indexOf(beforeLoop) && mn.instructions.indexOf(insn) < mn.instructions.indexOf(afterLoop)) {
          for (int i = mn.instructions.indexOf(insn); i > mn.instructions.indexOf(loopStart); --i) {
            if (frames[i].getStackSize() == frames[mn.instructions.indexOf(insn) + 1].getStackSize()) {
              comparisonVarUpdateBlocks.add(new AbstractMap.SimpleEntry<AbstractInsnNode, AbstractInsnNode>(mn.instructions.get(i), insn));
              break;
            }
          }
        }
      });
    }

    /*
      Check the validity of the computed blocks.
      At this moment, we only keep instructions if there is only one block, at the end of the loop.
     */
    if (comparisonVarUpdateBlocks.size() == 1 && mn.instructions.indexOf(comparisonVarUpdateBlocks.get(0).getValue()) + 1 == mn.instructions.indexOf(loopEnd)) {
      Logger.info("Statically resolved the cycle count.");
      return comparisonVarUpdateBlocks.get(0);
    } else {
      Logger.info("Failed to resolve statically the cycle count.");
      return null;
    }

  }

  public List<Object> resolveLocalTypes(ClassNode cn, MethodNode mn, AbstractInsnNode beforeLoop) {
    ListIterator<AbstractInsnNode> it = mn.instructions.iterator(mn.instructions.indexOf(beforeLoop));
    it.next();

    AbstractInsnNode ain = it.next();
    if (ain instanceof FrameNode) {
      FrameNode fn = (FrameNode) ain;
      List<Object> localTypes = new ArrayList<Object>(fn.local);
      if (fn.type == Opcodes.F_APPEND) {
        localTypes.add(0, cn.name);
        List<String> argumentTypes = new ArrayList<String>();
        Arrays.asList(Type.getArgumentTypes(mn.desc)).forEach((type) -> argumentTypes.add(type.toString().substring(1, type.toString().length() - 1)));
        localTypes.addAll(1, argumentTypes);
      }
      return localTypes;
    }

    return null;
  }

  public ClassNode generateInnerClass(ClassNode cn, MethodNode mn, AbstractInsnNode loopStart, AbstractInsnNode loopEnd, List<Object> localTypes) {
    Logger.info("Generating {0}$Inner.", cn.name);

    ClassNode innerClass = new ClassNode();

    // class properties
    innerClass.version = Opcodes.V1_8;
    innerClass.access = Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER;
    innerClass.name = cn.name + "$Inner";
    innerClass.superName = "java/lang/Object";
    innerClass.signature = "Ljava/lang/Object;Ljava/util/concurrent/Callable<Ljava/lang/Integer;>;";
    innerClass.interfaces.add("java/util/concurrent/Callable");
    // private java.lang.Integer id;
    innerClass.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "id", "Ljava/lang/Integer;", null, null));
    // private testasm.SpeculativeList frame;
    innerClass.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "frame", "Ltestasm/SpeculativeList;", null, null));

    // constructor
    MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/Integer;Ltestasm/SpeculativeList;)V", null, null);

    constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
    constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
    constructor.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, innerClass.name, "id", "Ljava/lang/Integer;"));
    constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
    constructor.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, innerClass.name, "frame", "Ltestasm/SpeculativeList;"));
    constructor.instructions.add(new InsnNode(Opcodes.RETURN));

    constructor.maxStack = 2;
    constructor.maxLocals = 3;

    innerClass.methods.add(constructor);

    // synthetic
    MethodNode synthetic = new MethodNode(Opcodes.ACC_PUBLIC + Opcodes.ACC_BRIDGE + Opcodes.ACC_SYNTHETIC, "call", "()Ljava/lang/Object;", null, new String[] { "java/lang/Exception" });

    synthetic.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    synthetic.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, innerClass.name, "call", "()Ljava/lang/Integer;", false));
    synthetic.instructions.add(new InsnNode(Opcodes.ARETURN));

    synthetic.maxStack = 1;
    synthetic.maxLocals = 1;

    innerClass.methods.add(synthetic);

    // call
    MethodNode runMethod = new MethodNode(Opcodes.ACC_PUBLIC, "call", "()Ljava/lang/Integer;", null, null);

    LabelNode l0 = new LabelNode(new Label());
    LabelNode l1 = new LabelNode(new Label());
    LabelNode l2 = new LabelNode(new Label());
    runMethod.tryCatchBlocks.add(new TryCatchBlockNode(l0, l1, l2, "java/lang/Throwable"));

    runMethod.instructions.add(l0);
    runMethod.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    runMethod.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, innerClass.name, "frame", "Ljava/util/List;"));
    runMethod.instructions.add(new VarInsnNode(Opcodes.ASTORE, 1));

    ListIterator<AbstractInsnNode> lit = mn.instructions.iterator(mn.instructions.indexOf(loopStart));
    lit.next();
    while (lit.hasNext() && lit.nextIndex() < mn.instructions.indexOf(loopEnd)) {
      AbstractInsnNode ain = lit.next();
      if (ain instanceof VarInsnNode) {
        try {
          runMethod.instructions.add(varNodeToList((VarInsnNode) ain, 1, localTypes));
        } catch (Throwable t) {
          // do nothing
        }
      } else {
        runMethod.instructions.add(ain.clone(null));
      }
    }

    runMethod.instructions.add(l1);
    runMethod.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    runMethod.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, innerClass.name, "id", "Ljava/lang/Integer;"));
    runMethod.instructions.add(new InsnNode(Opcodes.ARETURN));

    runMethod.instructions.add(l2);
    runMethod.instructions.add(new FrameNode(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"}));
    runMethod.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    runMethod.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, innerClass.name, "id", "Ljava/lang/Integer;"));
    runMethod.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false));
    runMethod.instructions.add(new InsnNode(Opcodes.ICONST_1));
    runMethod.instructions.add(new InsnNode(Opcodes.ISUB));
    runMethod.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
    runMethod.instructions.add(new InsnNode(Opcodes.ARETURN));

    runMethod.maxStack = mn.maxStack > 3 ? mn.maxStack : 3;
    runMethod.maxLocals = 3;

    innerClass.methods.add(runMethod);

    return innerClass;
  }

  public void removeInnerCode(ClassNode cn, MethodNode mn, AbstractInsnNode beforeLoop, AbstractInsnNode loopStart, AbstractInsnNode loopEnd, Map.Entry<AbstractInsnNode, AbstractInsnNode> keepBlock) {
    Logger.info("Removing old inner code");

    ListIterator<AbstractInsnNode> it = mn.instructions.iterator(mn.instructions.indexOf(loopStart));
    it.next();

    while (it.hasNext() && it.nextIndex() < mn.instructions.indexOf(loopEnd)) {
      AbstractInsnNode ain = it.next();
      if (keepBlock != null && (mn.instructions.indexOf(ain) < mn.instructions.indexOf(keepBlock.getKey()) || mn.instructions.indexOf(ain) > mn.instructions.indexOf(keepBlock.getValue()))) {
        it.remove();
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

  public void adaptFrameAppend(ClassNode cn, MethodNode mn, AbstractInsnNode beforeLoop, List<Object> localTypes) {
    Logger.info("Taking into account the new locals when declaring new frame");

    ListIterator<AbstractInsnNode> it = mn.instructions.iterator(mn.instructions.indexOf(beforeLoop));
    it.next();
    it.next(); // seek for the frame node

    List<Object> newLocal = new ArrayList<Object>(localTypes);
    newLocal.add("java/util/List");
    newLocal.add("java/util/List");
    it.set(new FrameNode(Opcodes.F_FULL, newLocal.size(), newLocal.toArray(), 0, new Object[] {}));

  }

  public void replaceInnerCode(ClassNode cn, MethodNode mn, AbstractInsnNode loopStart, AbstractInsnNode loopEnd) {
    Logger.info("Adding {0}.pool as a method member", cn.name);
    cn.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "pool", "Ljava/util/concurrent/ExecutorService;", null, null));

    int classPosition, runnablePosition;

    Logger.info("Replacing inner code");
    ListIterator<AbstractInsnNode> i = mn.instructions.iterator(mn.instructions.indexOf(loopEnd));
    i.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "testasm/MyClassLoader", "getInstance", "()Ltestasm/MyClassLoader;", false));
    i.add(new LdcInsnNode(cn.name.replace("/", ".") + "$Proxy"));
    i.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "testasm/MyClassLoader", "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;", false));
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
    i.add(new VarInsnNode(Opcodes.ALOAD, runnablePosition));
    i.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "org/multiverse/api/StmUtils", "atomic", "(Ljava/lang/Runnable;)V", false));

    mn.maxLocals += 2;
    mn.maxStack += 1;
  }

  public void adaptAfterCode(MethodNode mn, AbstractInsnNode afterLoop, List<Object> localTypes) {
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

    for (int j = 0; j < localTypes.size(); ++j) {
      i.add(new VarInsnNode(Opcodes.ALOAD, localesListPosition));
      i.add(new LdcInsnNode(new Integer(j)));
      i.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true));
      i.add(new TypeInsnNode(Opcodes.CHECKCAST, (String) localTypes.get(j)));
      i.add(new VarInsnNode(Opcodes.ASTORE, j));

    }
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

  public static void printMethod(MethodNode mn, AbstractInsnNode beforeLoop, AbstractInsnNode loopStart, AbstractInsnNode loopEnd, AbstractInsnNode afterLoop) {
    ListIterator<AbstractInsnNode> lit = mn.instructions.iterator();

    Logger.trace(mn.name);
    while (lit.hasNext()) {
      AbstractInsnNode ain = lit.next();
      if (ain.equals(beforeLoop)) {
        Logger.trace("before loop");
      } else if(ain.equals(loopStart)) {
        Logger.trace("loop start");
      } else if (ain.equals(loopEnd)) {
        Logger.trace("loop end");
      } else if (ain.equals(afterLoop)) {
        Logger.trace("after loop");
      }
      Logger.trace(ain);
    }
  }

  public static InsnList varNodeToList(VarInsnNode insn, int listIndex, List<Object> localTypes) throws Exception {
    switch (insn.getOpcode()) {
    case Opcodes.ALOAD:
      return loadToGet(insn, listIndex, localTypes);
    case Opcodes.ASTORE:
      return storeToSet(insn, listIndex);
    default:
      throw new Exception("Instruction not handled.");
    }
  }

  public static InsnList loadToGet(VarInsnNode insn, int listIndex, List<Object> localTypes) {
    InsnList instructions = new InsnList();
    instructions.add(new VarInsnNode(Opcodes.ALOAD, listIndex));
    instructions.add(new LdcInsnNode(new Integer(insn.var)));
    instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true));
    instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, (String) localTypes.get(insn.var)));
    return instructions;
  }

  public static InsnList storeToSet(VarInsnNode insn, int listIndex) {
    InsnList instructions = new InsnList();
    instructions.add(new VarInsnNode(Opcodes.ALOAD, listIndex));
    instructions.add(new InsnNode(Opcodes.DUP_X1));
    instructions.add(new InsnNode(Opcodes.POP));
    instructions.add(new LdcInsnNode(new Integer(insn.var)));
    instructions.add(new InsnNode(Opcodes.DUP_X1));
    instructions.add(new InsnNode(Opcodes.POP));
    instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "set", "(ILjava/lang/Object;)Ljava/lang/Object;", true));
    instructions.add(new InsnNode(Opcodes.POP));
    return instructions;
  }
}
