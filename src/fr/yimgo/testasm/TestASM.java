package fr.yimgo.testasm;

import java.util.Arrays;
import java.util.ListIterator;
/* ugly, fix this asap */
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.pmw.tinylog.Logger;

public class TestASM {
  public static void main(String... args) {
    try {
      ClassReader cr = new ClassReader("fr.yimgo.testasm.SequentialSqrt");
      ClassNode cn = new ClassNode();
      cr.accept(cn, 0);
      ClassPimp cp = new ClassPimp();
      cp.transform(cn, "sqrt");
    } catch (Throwable t) {
      Logger.error(t);
    }
  }
}
