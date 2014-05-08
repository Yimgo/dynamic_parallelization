package fr.yimgo.testasm;

import java.util.Iterator;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class ClassPimp {
  protected MethodPimp mp;
  public ClassPimp() {
    mp = new MethodPimp();
  }

  public void transform (ClassNode cn) {
    Iterator<MethodNode> i = cn.methods.iterator();

    cn.name += "_Parallelized";

    while (i.hasNext()) {
      MethodNode mn = i.next();
      if (mn.name.equals("sequential_sqrt")) {
        System.out.println(mn.name);
        mp.transform(mn, cn.name);
      } else if (mn.name.equals("parallel_sqrt")) {
        System.out.println(mn.name);
        mp.printOpcodes(mn.instructions);
      }
    }
  }
}
