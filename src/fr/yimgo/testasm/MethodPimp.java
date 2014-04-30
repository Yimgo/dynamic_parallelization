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
import org.objectweb.asm.Opcodes;

public class MethodPimp {
  public MethodPimp() { }

  public void transform (MethodNode mn) {
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

    ListIterator<AbstractInsnNode> i = mn.instructions.iterator();
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
    mn.maxLocals += transformInit(mn.instructions, 0, mn.instructions.indexOf(beforeLoop));
    System.out.println("Pimped instructions:");
    printOpcodes(mn.instructions);
  }

  /*
    @returns {int} the number of local variables added.
  */
  public int transformInit(InsnList instructions, int begin, int end) {
    int localVariables = 0;
    System.out.println("Initialization from " + begin + " to " + end);
    ListIterator<AbstractInsnNode> i = instructions.iterator(end);
    /* 
      = List<Future<Double>> futures = new ArrayList<Future<Double>>(); 
    */
    i.add(new TypeInsnNode(Opcodes.NEW, "java/util/ArrayList"));
    i.add(new InsnNode(Opcodes.DUP));
    i.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false));
    i.add(new VarInsnNode(Opcodes.ASTORE, 4));
    localVariables += 1;

    return localVariables;
  }

  public void printOpcodes(InsnList instructions) {
    ListIterator<AbstractInsnNode> i = instructions.iterator();
    while (i.hasNext()) {
      System.out.println(i.next().getOpcode());
    }
  }
}
