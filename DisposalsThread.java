import java.util.*;

/**
 * <p>Title: DisposalsThread </p>
 * <p>Description: Cleans the lower levels from a Space Server.
 * It is powered by a producers/consumer mechanism </p>
 * @authors Yael Weinberg and Marcel Apfelbaum
 */
public class DisposalsThread
    extends Thread {

  /** thread caches */
  private List objectsCache;

  public DisposalsThread() {
    objectsCache = Collections.synchronizedList(new ArrayList());
  }

  /** Adds an object to be disposed to this cache. After addition this thread is notified. */
  public void addObj(SpaceObj obj) {
    synchronized (objectsCache) {
      objectsCache.add(obj);
      objectsCache.notify();
    }
  }

  /** Every time it is notified , go over the cache and dispose the objects. */
  public void run() {
    List tempObjects;
    for (; ; ) {
      synchronized (objectsCache) {
        tempObjects = Arrays.asList(objectsCache.toArray());
        objectsCache.clear();
        if (tempObjects.size() == 0) {
          try {
            objectsCache.wait();
          }
          catch (InterruptedException ex) {
            System.out.println("Disposals Error " + ex.toString());
            ex.printStackTrace();
          }
          continue;
        }
      }
      Iterator i = tempObjects.iterator();
      while (i.hasNext()) {
        ( (SpaceObj) i.next()).dispose();
      }
    }
  }
}
