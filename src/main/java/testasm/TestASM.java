package testasm;

import java.util.Arrays;
import java.util.ListIterator;
/* ugly, fix this asap */
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import org.pmw.tinylog.Logger;

public class TestASM {
  public static void main(String... args) {
    try {
      ClassReader cr = new ClassReader("testasm.SequentialSqrt");
      ClassNode cn = new ClassNode();
      cr.accept(cn, 0);
      ClassPimp cp = new ClassPimp();
      cp.transform(cn, "sqrt");

      /*SequentialSqrt p = new SequentialSqrt();
      Logger.trace(p.sqrt(100));*/
    } catch (Throwable t) {
      Logger.error(t);
    }
  }
}
