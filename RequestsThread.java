import java.util.*;

/**
 * <p>Title: RequestsThread </p>
 * <p>Description: It keeps all the "register" requests. Every time a new entry is entered
 * in the system, this thread is notified and process them.
 * It is powered by a producers/consumer mechanism </p>
 * @authors Yael Weinberg and Marcel Apfelbaum
 */
public class RequestsThread
    extends Thread {

  /** All registered requests */
  private HashMap requests;

  /** thread caches */
  private List requestsCache;
  private List objectsCache;

  /** temporar lists */
  private List tempRequests, tempObjects;

  public RequestsThread() {
    requests = new HashMap();
    requestsCache = Collections.synchronizedList(new ArrayList());
    objectsCache = Collections.synchronizedList(new ArrayList());
  }

  /** Adds a new request to cache */
  public void addRequest(SpaceObj template, RemoteEventListener l) {
    Object[] request = {
        template, l};
    synchronized (requestsCache) {
      requestsCache.add(request);
    }
  }

  /** When a new object is entered, is added to the cache, then  the thread is notified*/
  public void addObj(SpaceObj obj) {
    synchronized (objectsCache) {
      objectsCache.add(obj);
      objectsCache.notify();
    }
  }

  /** Process the requests from data structure and the cache */
  private void doRequests(SpaceObj writtenObj) throws Exception {
    List requestsToRemove = new ArrayList();
    List classesToRemove = new ArrayList();
    Iterator i1 = requests.entrySet().iterator();
    while (i1.hasNext()) {
      Map.Entry maping1 = (Map.Entry) i1.next();
      if ( ( (Class) maping1.getKey()).isAssignableFrom(writtenObj.getObjClass())) {
        List l = (List) maping1.getValue();
        Iterator i2 = l.iterator();
        while (i2.hasNext()) {
          Object[] o = ( (Object[]) i2.next());
          if (SpaceObj.correlates(writtenObj, (SpaceObj) o[0])) {
            ( (RemoteEventListener) o[1]).notify(writtenObj.getEntry());
            requestsToRemove.add(o);
          }
        }
        l.removeAll(requestsToRemove);
        requestsToRemove.clear();
        if (l.size() == 0) {
          classesToRemove.add(maping1.getKey());
        }
      }
    }
    // remove empty classes from the  map
    if (!classesToRemove.isEmpty()) {
      Iterator i = classesToRemove.iterator();
      while (i.hasNext()) {
        requests.remove( (Class) i.next());
      }
      classesToRemove.clear();
    }

    // notify also the new requests from cache
    Iterator i = tempRequests.iterator();
    while (i.hasNext()) {
      Object[] o = ( (Object[]) i.next());
      if (SpaceObj.correlates(writtenObj, (SpaceObj) o[0])) {
        ( (RemoteEventListener) o[1]).notify(writtenObj.getEntry());
        requestsToRemove.add(o);
      }
    }
    tempRequests.removeAll(requestsToRemove);
    requestsToRemove.clear();
  }

  /** Is runned every time a new entry is entered in the space.*/
  public void run() {
    tempRequests = new ArrayList();
    for (; ; ) {
      synchronized (objectsCache) {
        synchronized (requestsCache) {
          tempObjects = Arrays.asList(objectsCache.toArray());
          tempRequests.addAll(Arrays.asList(requestsCache.toArray()));
          objectsCache.clear();
          requestsCache.clear();
        }
        if (tempObjects.size() == 0) {
          try {
            objectsCache.wait();
          }
          catch (InterruptedException ex) {
            System.out.println("Requests handling Error " + ex.toString());
            ex.printStackTrace();
          }
          continue;
        }
      }
      Iterator i = tempObjects.iterator();
      // for each new object, notify the mached requests
      while (i.hasNext()) {
        try {
          doRequests( (SpaceObj) i.next());
        }
        catch (Exception ex1) {
          System.out.println("RequestsThread: Error verifing requests!" +
                             ex1.toString());
          ex1.printStackTrace();
        }
      }
      i = tempRequests.iterator();
      // put the requests from cache to the data base
      while (i.hasNext()) {
        Object[] o = (Object[]) i.next();
        SpaceObj requestedTemplate = (SpaceObj) o[0];
        List classList = (List) requests.get( (Class) requestedTemplate.
                                             getObjClass());
        //class already exists in map
        if (classList == null) {
          classList = new ArrayList();
          requests.put(requestedTemplate.getObjClass(), classList);
        }
        classList.add(o);
      }
      tempRequests.clear();
    }
  }

}
