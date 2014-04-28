package fr.yimgo.testasm;

import java.util.Arrays;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.tree.ClassNode;

public class TestASM {
  public static void main(String... args) throws Throwable {
    /*ClassWriter cw = new ClassWriter(0);
    ClassPrinter cp = new ClassPrinter((ClassVisitor)cw);
    ClassReader cr = new ClassReader("fr.yimgo.testasm.SequentialSqrt");
    cr.accept(cp, 0);
    System.out.println(Arrays.toString(cw.toByteArray()));*/
    ClassReader cr = new ClassReader("fr.yimgo.testasm.SequentialSqrt");
    ClassNode cn = new ClassNode();

    cr.accept(cn, 0);

    ClassPimp cp = new ClassPimp();
    cp.transform(cn);
  }
}