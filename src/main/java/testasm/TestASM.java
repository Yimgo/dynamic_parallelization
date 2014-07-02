package testasm;

import java.util.concurrent.Executors;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import org.pmw.tinylog.Logger;

public class TestASM {
  public static void main(String... args) {
    try {
      /*ClassReader cr = new ClassReader("testasm.SequentialSqrt");
      ClassNode cn = new ClassNode();
      cr.accept(cn, 0);
      ClassPimp cp = new ClassPimp();
      cp.transform(cn, "sqrt");*/

      /*SequentialSqrt p = new SequentialSqrt();
      Logger.trace(p.sqrt(100));*/

      Base b = new Base(Executors.newFixedThreadPool(2));
      b.doRun();
      b.pool.shutdown();
    } catch (Throwable t) {
      Logger.error(t);
    }
  }
}
