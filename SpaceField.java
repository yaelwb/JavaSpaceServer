import java.lang.reflect.*;
import java.util.*;
/**
 * <p>Title: SpaceField </p>
 * <p>Description: The first level of the system.
 *  Represents mapping from a field  to the second level of the system </p>
 * @authors Yael Weinberg and Marcel Apfelbaum
 */
public class SpaceField {

  /** One writer many readers semaphore */
  private RWSemaphore sem;

  /** All existing values of the field are kept in this table */
  private Hashtable allValues;

  /** Name of this field  */
  private Field field;

  /** A background thread that removes non-valid objects*/
  private DisposalsThread disposalsThread;

  /** System cleaner for upper levels */
  private CleaningTask cleaningTask;


  public SpaceField(Field field, DisposalsThread disposalsThread,
                    CleaningTask cleaningTask){
    this.field = field;
    this.sem = new RWSemaphore();
    this.allValues = new Hashtable();
    this.disposalsThread = disposalsThread;
    this.cleaningTask = cleaningTask;
  }

  /** Adds a new entry to this SpaceField. If the value of this field
   * does not exists, a new value is created. */
  public void addEntry(SpaceObj obj) {
    sem.aquireReadersLock();
    try {
      Object val = field.get(obj.getEntry());
      FieldValue fieldValue = (FieldValue) allValues.get(new ValueKey(val));
      sem.releaseLock();
      if (fieldValue != null) {
        fieldValue.addEntry(obj);
        return;
      }
      //things might be changed now, so we'll re-check
      sem.aquireWriterLock();
      //re-check
      if (fieldValue != null) {
        sem.releaseLock();
        fieldValue.addEntry(obj);
        return;
      }
      //create new key
      fieldValue = new FieldValue(val, disposalsThread);
      allValues.put(new ValueKey(val), fieldValue);
      sem.releaseLock();
      fieldValue.addEntry(obj);
      return;
    }
    catch (Exception ex) {
      sem.releaseLock();
      System.out.println("Error: adding Entry  " + ex.toString());
      ex.printStackTrace();
    }
  }

  /** Finds an object from this data structure that correlates with the given
   * template. The query is taken from the upper level and propagated to
   * the lower system.  */
  public Entry findCorellatedEntry(SpaceObj template, boolean take) {
    sem.aquireReadersLock();
    try {
      // if all the public fields are empty
      if(template.getObjFields().isEmpty()){
        Iterator i = allValues.entrySet().iterator();
        while(i.hasNext()){
          Map.Entry m = (Map.Entry)i.next();
          FieldValue fieldValue = (FieldValue) m.getValue();
          Entry e = fieldValue.findCorellatedEntry(template, take);
          if (e!=null){
            sem.releaseLock();
            return e;
          }
        }
        sem.releaseLock();
        return null;
      }

      Object val = field.get(template.getEntry());
      FieldValue fieldValue = (FieldValue) allValues.get(new ValueKey(val));
      sem.releaseLock();
      if (fieldValue != null) {
        if (fieldValue.isEmpty()) {
          cleaningTask.addValue(fieldValue, this);
        }
        return fieldValue.findCorellatedEntry(template, take);
      }
    }
    catch (Exception ex) {
      sem.releaseLock();
      System.out.println("Error:finding entry  " + ex.toString());
      ex.printStackTrace();
    }
    return null;
  }

  /** Retruns true if this SpaceField is cached be a writer */
  public  boolean lockedByWriter() {
    return sem.lockedByWriter();
  }

  /** Returns the number of waiting threads */
  public  int getWaitingNo() {
    return sem.getWaitingNo();
  }

  public Field getField() {
    return field;
  }

  /** If a value is not used any more, the system cleaner calls this function */
  public void cleanValues(Set values) {
    sem.aquireWriterLock();
    Iterator i = values.iterator();
    while (i.hasNext()) {
      FieldValue fv = (FieldValue) i.next();
      if (fv.isEmpty()) {
         allValues.remove(new ValueKey(fv.getValue()));
      }
    }
    sem.releaseLock();
  }

  /** Returns true if no value exists for this field */
  public boolean isEmpty() {
    sem.aquireReadersLock();
    boolean b = allValues.isEmpty();
    sem.releaseLock();
    return b;
  }

  public boolean equals(Object o) {
    return (o instanceof SpaceField) &&
        (this.field.getName().equals( ( (SpaceField) o).field.getName())) &&
        (this.field.getDeclaringClass().equals( ( (SpaceField) o).field.
                                               getDeclaringClass()));
  }

}

/**
*  ValueKey is used as key for the SpaceField hashtable.
 */
class ValueKey {
  public final Object value;

  ValueKey(Object value) {
    this.value = value;
  }

  public boolean equals(Object o) {
    if (value == null)
      return (o == null);
    return (o instanceof ValueKey) &&
        (this.value.equals( ( (ValueKey) o).value));
  }

  public int hashCode() {
    return value == null ? Integer.MAX_VALUE : value.hashCode();
  }
}
