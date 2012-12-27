import java.util.*;
import java.util.Set;
import java.util.List;
import java.lang.reflect.*;

/**
 * <p>Title: NoPublicFieldsThread </p>
 * <p>Description: Data base of all objects
 * Also  keeps for every class with no public fields the fields of all subclasses .
 * It is powered by a producers/consumer mechanism </p>
 * @authors Yael Weinberg and Marcel Apfelbaum
 */
public class NoPublicFieldsThread
    extends Thread {

  /** All objects with no public fields are held in a map from class to
   * list of objects   */
  private Hashtable noPublics;

  /**  Writers - Readers semaphore for noPublics */
  private RWSemaphore npSem;

  /** Map from every class with no public fields to all sons fields, if exist  */
  private Hashtable allClasses;

  /**  Writers - Readers semaphore for noPublics */
  private RWSemaphore clsSem;

  /** thread cache */
  private List classesCache;

  /** temp list taken from the cache */
  private List tempClasses;

  public NoPublicFieldsThread() {
    noPublics = new Hashtable();
    npSem = new RWSemaphore();
    allClasses = new Hashtable();
    clsSem = new RWSemaphore();
    classesCache = Collections.synchronizedList(new ArrayList());
  }

  /** Adds an object to  noPublics. */
  public void addObj(SpaceObj newObj) {
    Class objClass = newObj.getObjClass();
    npSem.aquireWriterLock();
    LinkedList classObjects = (LinkedList) noPublics.get(objClass);
    //class not found. create a new hash for the class.
    if (classObjects == null) {
      classObjects = new LinkedList();
      noPublics.put(objClass, classObjects);
      clsSem.aquireReadersLock();
      if (!allClasses.containsKey(objClass)) {
        clsSem.releaseLock();
        clsSem.aquireWriterLock();
        allClasses.put(objClass, new HashSet());
      }
      clsSem.releaseLock();
    }
    classObjects.add(newObj);
    newObj.validate();
    npSem.releaseLock();
  }

  /** Adds a class to this cache. After addition this thread is notified. */
  public void checkClass(Class cls) {
    synchronized (classesCache) {
      classesCache.add(cls);
      classesCache.notify();
    }
  }

  private void check() {
    clsSem.aquireReadersLock();
    Iterator i1 = allClasses.entrySet().iterator();
    while (i1.hasNext()) {
      Map.Entry m = (Map.Entry) i1.next();
      Class c1 = (Class) m.getKey();
      Iterator i2 = tempClasses.iterator();
      while (i2.hasNext()) {
        Class c2 = (Class) i2.next();
        if (c1.isAssignableFrom(c2)) {
          ( (Set) m.getValue()).addAll(Arrays.asList(c2.getFields()));
        }
      }
    }
    clsSem.releaseLock();
  }

  /** Finds a correlated entry to the given template.
     / * Removes the entry from the system if the TAKE flag is on. */
  public Object findCorrelatedObj(SpaceObj templateObj, boolean take) {
    npSem.aquireReadersLock();
    Iterator i = noPublics.entrySet().iterator();
    while (i.hasNext()) {
      Map.Entry m = (Map.Entry) i.next();
      Class cls = (Class) m.getKey();
      if (templateObj.getObjClass().isAssignableFrom(cls)) {
        LinkedList l = (LinkedList) noPublics.get(cls);
        SpaceObj sp = (SpaceObj) l.getFirst();
        boolean found = false;
        synchronized (sp) {
          if (sp.isValid()) {
            found = true;
            if (take) {
              sp.invalidate();
              sp.cancelTask();
            }
          }
        }
        if (found) {
          if (take) {
            npSem.releaseLock();
            npSem.aquireWriterLock();
            l.remove(sp);
            if (l.isEmpty()) {
              noPublics.remove(cls);
            }
          }
          npSem.releaseLock();
          return sp.getEntry();
        }
      }
    }
    npSem.releaseLock();
    clsSem.aquireReadersLock();
    Set s = (Set) allClasses.get(templateObj.getObjClass());
    clsSem.releaseLock();
    return s;
  }

  public List getSubClassFields(Class cls) {
    clsSem.aquireReadersLock();
    List l = Arrays.asList( ( (Set) allClasses.get(cls)).toArray());
    clsSem.releaseLock();
    return l;
  }

  /** Every time it is notified , go over the cache and update classes if needed . */
  public void run() {
    for (; ; ) {
      synchronized (classesCache) {
        tempClasses = Arrays.asList(classesCache.toArray());
        classesCache.clear();
        if (tempClasses.size() == 0) {
          try {
            classesCache.wait();
          }
          catch (InterruptedException ex) {
            System.out.println("Error " + ex.toString());
            ex.printStackTrace();
          }
          continue;
        }
      }
      Iterator i = tempClasses.iterator();
      Class c;
      while (i.hasNext()) {
        Class cls = (Class) i.next();
        c = cls.getSuperclass();
        cls = (new Object()).getClass();
        while (c != null && c != cls) {
          boolean good = true;
          Field[] f = c.getFields();
          for (int j = 0; j < f.length; j++) {
            int mod = (f[j]).getModifiers();
            if (Modifier.isPublic(mod)) {
              good = false;
              break;
            }
          }
          if (good) {
            clsSem.aquireReadersLock();
            if (allClasses.get(c) == null) {
              clsSem.releaseLock();
              clsSem.aquireWriterLock();
              allClasses.put(c, new HashSet());
            }
            clsSem.releaseLock();
          }
          c = c.getSuperclass();
        }
      }
      check();
    }
  }
}
