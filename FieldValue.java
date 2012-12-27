import java.util.*;
/**
 * <p>Title: FieldValue </p>
 * <p>Description: The second level of the system.
 *  Represents mapping from a value of a field to the third level of the system </p>
 * @authors Yael Weinberg and Marcel Apfelbaum
 */
public class FieldValue {

  /** One writer many readers semaphore */
  private RWSemaphore sem;

  /** Maping from all  existing classes  having this field and value to their objects.
   * Represents the third level of the system. */
  private Hashtable allClasses;

  /** The value of this field */
  private Object value;

  /** A background thread that removes non-valid objects*/
  private DisposalsThread disposalsThread;

  public FieldValue(Object value, DisposalsThread disposalsThread){
    this.value = value;
    this.sem = new RWSemaphore();
    this.allClasses = new Hashtable();
    this.disposalsThread = disposalsThread;
  }

  /** Adds a new entry to this FieldValue. */
  public void addEntry(SpaceObj obj) {
    sem.aquireWriterLock();
    Class objClass = obj.getObjClass();
    LinkedList classObjects = (LinkedList) allClasses.get(objClass);
    //class not found. create a new hash for the class.
    if (classObjects == null) {
      classObjects = new LinkedList();
      allClasses.put(objClass, classObjects);
    }
    // adds the object to the data structure
    classObjects.add(obj);
    Object[] address = {
        this, classObjects};
    obj.addAddress(address);
    sem.releaseLock();
  }

  /** Finds an object from this data structure that correlates with the given
   * template. If take flag is on, the object is marked as not valid and  DisposalsThread
   * is notified. */
  public Entry findCorellatedEntry(SpaceObj template, boolean take) {
    sem.aquireReadersLock();
    LinkedList classObjects;
    Class templateClass = template.getObjClass();

    // if all public fields are null
    if (template.getObjFields().isEmpty()) {
      Iterator i1 = allClasses.entrySet().iterator();
      while (i1.hasNext()) {
        Map.Entry m = (Map.Entry) i1.next();
        Class cls = (Class) m.getKey();
        if (templateClass.isAssignableFrom(cls)) {
          classObjects = (LinkedList) m.getValue();
          Iterator i2 = classObjects.iterator();
          boolean found = false;
          while (i2.hasNext()) {
            SpaceObj obj = (SpaceObj) i2.next();
            synchronized (obj) {
              if (obj.isValid()) {
                found = true;
                if (take) {
                  obj.invalidate();
                  obj.cancelTask();
                  disposalsThread.addObj(obj);
                }
              }
            }
            if (found) {
              sem.releaseLock();
              return obj.getEntry();
            }
          }
        }
      }
    }
    // at least one public field of the template is not null
    classObjects = (LinkedList) allClasses.get(templateClass);
    if (classObjects != null) {
      Iterator i = classObjects.iterator();
      while (i.hasNext()) {
        SpaceObj obj = (SpaceObj) i.next();
        if (SpaceObj.correlates(obj, template)) {
        boolean found = false;
          synchronized (obj) {
            if (obj.isValid()) {
              found = true;
              if (take) {
                obj.invalidate();
                obj.cancelTask();
                disposalsThread.addObj(obj);
              }
            }
          }
          if (found) {
            sem.releaseLock();
            return obj.getEntry();
          }
        }
      }
    }
    //class not find.look for sub-class
    else {
      Iterator i1 = allClasses.entrySet().iterator();
      while (i1.hasNext()) {
        Map.Entry m = (Map.Entry) i1.next();
        Class cls = (Class) m.getKey();
        if (template.getObjClass().isAssignableFrom(cls)) {
          classObjects = (LinkedList) m.getValue();
          Iterator i2 = classObjects.iterator();
          while (i2.hasNext()) {
            SpaceObj obj = (SpaceObj) i2.next();
            if (SpaceObj.correlates(obj, template)) {
              boolean found = false;
              synchronized (obj) {
                if (obj.isValid()) {
                  found = true;
                  if (take) {
                    obj.invalidate();
                    obj.cancelTask();
                    disposalsThread.addObj(obj);
                  }
                }
              }
              if (found) {
                sem.releaseLock();
                return obj.getEntry();
              }
            }
          }
        }
      }
    }
    sem.releaseLock();
      return null;
  }

  /** Returns true if no object inside */
  public boolean isEmpty() {
    sem.aquireReadersLock();
    boolean b = allClasses.isEmpty();
    sem.releaseLock();
    return b;
  }

  public Object getValue() {
    return value;
  }

  /** Removes an entry from this data structure */
  public void removeEntry(List classObjects, SpaceObj obj) {
    sem.aquireWriterLock();
    classObjects.remove(obj);
    if (classObjects.isEmpty()) {
      allClasses.remove(obj.getObjClass());
    }
    sem.releaseLock();
  }

  public boolean equals(Object o) {
    if (value == null) {
      return (o == null);
    }
    return (o instanceof FieldValue) &&
        (this.value.equals( ( (FieldValue) o).value));
  }

  public int hashCode() {
    return value.hashCode();
  }

}
