package fr.yimgo.testasm;

import java.util.Iterator;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class ClassPimp {
	public ClassPimp() {

	}

	public void transform (ClassNode cn) {
		Iterator<MethodNode> i = cn.methods.iterator();
		while (i.hasNext()) {
			MethodNode mn = i.next();
			System.out.println(mn.name);
		}
	}
}