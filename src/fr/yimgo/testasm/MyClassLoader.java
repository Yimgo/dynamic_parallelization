package fr.yimgo.testasm;

import java.util.Map;
import java.util.HashMap;

public class MyClassLoader extends ClassLoader {
  private static MyClassLoader instance = null;
  private Map<String, Class<?>> map;
  protected MyClassLoader() {
    map = new HashMap<String, Class<?>>();
  }
  public static MyClassLoader getInstance() {
    /* not thread-safe, for sure. */
    if (instance == null) {
      instance = new MyClassLoader();
    }
    return instance;
  }
  @Override
  public Class<?> loadClass(String name) throws ClassNotFoundException {
    return findClass(name);
  }
  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    if (map.containsKey(name)) {
      return map.get(name);
    }
    return findSystemClass(name);
  }
  public Class<?> defineClass(String name, byte[] b) throws Throwable {
    Class<?> definition = defineClass(name, b, 0, b.length);
    map.put(name, definition);
    return definition;
  }
}
