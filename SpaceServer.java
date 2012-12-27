import java.rmi.server.*;
import java.rmi.*;
import java.rmi.registry.*;
import java.util.*;
import java.lang.reflect.*;

/**
 * <p>Title:SpaceServer </p>
 * <p>Description: A JavaSpaces like system. </p>
 * @authors Yael Weinberg and Marcel Apfelbaum
 */
public class SpaceServer
    extends UnicastRemoteObject
    implements ISpaceServer {

  /**  Writers - Readers semaphore that protects the database */
  private RWSemaphore dbSem;

  /** A  thread powered by consumers/producer mechanism
   *  that holds requests and notify if a desired object was written */
  private RequestsThread requestsThread;

  /** A  thread powered by consumers/producer mechanism that removes non-valid objects */
  private DisposalsThread disposalsThread;

  /** A  thread powered by consumers/producer mechanism that removes non-valid objects */
  private NoPublicFieldsThread npfThread;

  /**A  task powered at long fixed intervals that  removes upper levels */
  private CleaningTask cleaningTask;

  /** All entries are hold in 3 top hashing levels and a bottom map level*/
  private Hashtable db;

  /** A background Timer thread for scheduling tasks */
  private Timer timer;

  private static final boolean TAKE = true;
  private static final int MINUTE = 60 * 1000;
  private static final int PERIOD = 15 * 60 * 1000;

  public SpaceServer() throws RemoteException {
  }

  /** Initialize the system */
  public void init(Registry r) {
    try {
      r.rebind("Space", this);
    }
    catch (Exception ex) {
      System.out.println("Error binding the server: " + ex.toString());
      ex.printStackTrace();
      System.exit(1);
    }
    // system initialization
    db = new Hashtable();
    dbSem = new RWSemaphore();
    requestsThread = new RequestsThread();
    requestsThread.start();
    disposalsThread = new DisposalsThread();
    disposalsThread.start();
    npfThread = new NoPublicFieldsThread();
    npfThread.start();

    timer = new Timer();
    cleaningTask = new CleaningTask(this);
    timer.schedule(cleaningTask, PERIOD, PERIOD);

    //allows all threads finish initial work.
    try {
      Thread.currentThread().sleep(1000);
    }
    catch (InterruptedException ex1) {
      System.out.println("Error binding the server: " + ex1.toString());
      ex1.printStackTrace();
      System.exit(1);
    }
  }

  /** Writes a new entry to the space system */
  public void write(Entry obj, Integer min) throws RemoteException {
    if (obj == null) {
      return;
    }
    SpaceObj newObj;
    try {
      newObj = new SpaceObj(obj);
      RemoveObjTask rot = new RemoveObjTask(newObj, disposalsThread);
      newObj.setRemoveTask(rot);
      timer.schedule(rot, min.intValue() * MINUTE);
      npfThread.checkClass(newObj.getObjClass());
      requestsThread.addObj(newObj);
      // here we give it to the new class
    }
    catch (Exception ex) {
      System.out.println("Error writing entry " + ex.toString());
      ex.printStackTrace();
      return;
    }
    // no public fields
    if (newObj.getEntryFields().isEmpty()) {
      npfThread.addObj(newObj);
      return;
    }
    List publicFields = newObj.getEntryFields();
    LinkedList fieldToCreate = new LinkedList();
    LinkedList fieldToUpdate = new LinkedList();
    Iterator i1 = publicFields.iterator();
    dbSem.aquireReadersLock();
    while (i1.hasNext()) {
      Field field = (Field) i1.next();
      FieldKey key = new FieldKey(field);
      SpaceField sf = (SpaceField) db.get(key);
      if (sf == null) {
        fieldToCreate.add(field);
        continue;
      }
      fieldToUpdate.add(sf);
    }
    dbSem.releaseLock();
    if (!fieldToCreate.isEmpty()) {
      dbSem.aquireWriterLock();
      Iterator i2 = fieldToCreate.iterator();
      while (i2.hasNext()) {
        Field field = (Field) i2.next();
        FieldKey key = new FieldKey(field);
        SpaceField sf = (SpaceField) db.get(key);
        if (sf == null) {
          sf = new SpaceField(field, disposalsThread, cleaningTask);
          db.put(key, sf);
        }
        fieldToUpdate.add(sf);
      }
      dbSem.releaseLock();
    }
    Iterator i3 = fieldToUpdate.iterator();
    while (i3.hasNext()) {
      ( (SpaceField) i3.next()).addEntry(newObj);
    }
    newObj.validate();
  }

  /** Reads an entry from the  space system */
  public Entry read(Entry template) throws RemoteException {
    if (template == null) {
      return null;
    }
    return findCorrelatedObj(template, !TAKE);
  }

  /** Takes an entry from the  space system */
  public Entry take(Entry template) throws RemoteException {
    if (template == null) {
      return null;
    }
    return findCorrelatedObj(template, TAKE);
  }

  /** Registers an entry from the  space system */
  public void register(Entry template, RemoteEventListener l) throws
      RemoteException {
    if (template == null) {
      return;
    }
    Entry correlated = findCorrelatedObj(template, !TAKE);
    if (correlated != null) {
      l.notify(correlated);
      return;
    }
    try {
      requestsThread.addRequest(new SpaceObj(template), l);
    }
    catch (Exception ex) {
      System.out.println("Error: " + ex.toString());
      ex.printStackTrace();
    }
  }

  /** Finds a correlated entry to the given template.
   * Removes the entry from the system if the TAKE flag is on. */
  private Entry findCorrelatedObj(Entry template, boolean take) throws
      RemoteException {
    SpaceObj templateObj;
    try {
      templateObj = new SpaceObj(template);
    }
    catch (Exception ex) {
      System.out.println("Error reading entry " + ex.toString());
      ex.printStackTrace();
      return null;
    }

    // no public fields
    boolean noPublicFields = false;
    Set subClassFields = new HashSet();
    if (templateObj.getEntryFields().isEmpty()) {
      Object o = npfThread.findCorrelatedObj(templateObj, take);
      //entry found
      if (o instanceof Entry) {
        return (Entry) o;
      }
      //entry not found, a set of all subclasses fields is returned
      noPublicFields = true;
      subClassFields = (Set) o;
    }

    // all relevant SpaceFields will be held in this list
    List spaceFields = new LinkedList();

    //go over all template's public fields, if one doesn't exists, return
    Iterator i = templateObj.getEntryFields().iterator();
    if (noPublicFields && subClassFields != null) {
      i = subClassFields.iterator();
    }
    dbSem.aquireReadersLock();
    while (i.hasNext()) {
      Field field = (Field) i.next();
      FieldKey key = new FieldKey(field);
      SpaceField sf = (SpaceField) db.get(key);
      if (sf == null && !noPublicFields) {
        dbSem.releaseLock();
        return null;
      }
      //if no public fields, continue
      if (sf == null) {
        continue;
      }
      if (sf.isEmpty()) {
        cleaningTask.addField(sf);
      }
      spaceFields.add(sf);
    }
    dbSem.releaseLock();

    // no relevant fields in the database
    if (spaceFields.isEmpty()) {
      return null;
    }

    // if at least one of the templates fields is not null, take only those fields
    if (!templateObj.getObjFields().isEmpty()) {
      Map objFields = templateObj.getObjFields();
      i = spaceFields.iterator();
      while (i.hasNext()) {
        if (!objFields.containsKey( ( (SpaceField) i.next()).getField().getName())) {
          i.remove();
        }
      }
    }

    // look for a field which is not held by a writer, or has the min waiting list
    int shortestQueue = Integer.MAX_VALUE;
    SpaceField lookupField = null;
    i = spaceFields.iterator();
    while (i.hasNext()) {
      SpaceField sf = (SpaceField) i.next();
      if (!sf.lockedByWriter()) {
        return sf.findCorellatedEntry(templateObj, take);
      }
      if (sf.getWaitingNo() < shortestQueue) {
        shortestQueue = sf.getWaitingNo();
        lookupField = sf;
      }
    }
    //maximum one field is choosed
    return lookupField.findCorellatedEntry(templateObj, take);
  }

  /** If a field is not used any more, the system cleaner calls this function */
  public void cleanFields(Set fields) {
    dbSem.aquireWriterLock();
    Iterator i = fields.iterator();
    while (i.hasNext()) {
      SpaceField f = (SpaceField) i.next();
      if (f.isEmpty()) {
        db.remove(new FieldKey(f.getField()));
      }
    }
    dbSem.releaseLock();
  }

}

/**
 *  FieldKey is used as key for the db hashtable.
 */
class FieldKey {
  public final String name;
  public final Class cls;

  FieldKey(Field f) {
    this.name = f.getName();
    this.cls = f.getDeclaringClass();
  }

  public boolean equals(Object o) {
    return (o instanceof FieldKey) &&
        (this.name.equals( ( (FieldKey) o).name)) &&
        (this.cls.equals( ( (FieldKey) o).cls));
  }

  public int hashCode() {
    return name.hashCode();
  }

}
