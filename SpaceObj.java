import java.lang.reflect.*;
import java.util.*;
/**
 * <p>Title: SpaceObj </p>
 * <p>Description: A wrapper for an space entry</p>
 * @authors Yael Weinberg and Marcel Apfelbaum
 */
public class SpaceObj {

  /** the entry */
  Entry entry;

  /** The object's class */
  private final Class objClass;

  /** Object's not null public fields */
  private final Map publicFields;

  /** Entry's public fields */
  List entryPublicFields;

  /** True if the object is still in database  */
  private boolean valid = false;

  /** Task that removes this entry from database */
  private RemoveObjTask removeTask;

  /** Every element is an array with 2 pointers: value and class */
  private List addresses;

  public SpaceObj(Entry entry) throws Exception {
    this.entry = entry;
    this.objClass = entry.getClass();
    this.publicFields = new HashMap();
    Field[] entryFields;
    addresses = new LinkedList();
    entryFields = objClass.getFields();
    for (int i = 0; i < entryFields.length; i++) {
      Field currField = entryFields[i];
      /* A null field is not taken into account  */
      if (currField.get(entry) == null) {
        continue;
      }
      int mod = currField.getModifiers();
      if (Modifier.isPublic(mod)) {
        publicFields.put(currField.getName(), currField.get(entry));
      }
    }
    entryPublicFields = Arrays.asList(entry.getClass().getFields());
    Iterator i =  entryPublicFields.iterator();
    while (i.hasNext()) {
      Field fi = (Field) i.next();
      int mod = fi.getModifiers();
      if (!Modifier.isPublic(mod)) {
        i.remove();
      }
    }
  }

  public List getEntryFields() {
    return entryPublicFields;
  }

  public Entry getEntry() {
    return entry;
  }

  public void setRemoveTask(RemoveObjTask removeTask) {
    this.removeTask = removeTask;
  }

  public void addAddress(Object a) {
    addresses.add(a);
  }

  public boolean isValid() {
    return valid;
  }

  public void invalidate() {
    valid = false;
  }

  public void validate() {
    valid = true;
  }

  public Class getObjClass() {
    return objClass;
  }

  public Map getObjFields() {
    return publicFields;
  }

  public void cancelTask() {
    if(removeTask!=null)
      removeTask.cancel();
  }

  /** Called by DisposalsThread when it becomes invalid because of the
   * time expiration or because is taken. Use to lock only the lowers levels
   * of the system.
   */
  public void dispose() {
      Iterator i = addresses.iterator();
      while (i.hasNext()) {
        Object[] o = (Object[]) i.next();
        //this remove function is protected by RWSemaphore from the inside.
        ( (FieldValue) o[0]).removeEntry( (List) o[1], this);
      }
  }

  /** Retruns true if the object correlates with the template */
  public static boolean correlates(SpaceObj o, SpaceObj template) {
    Iterator i = template.getObjFields().entrySet().iterator();
    while (i.hasNext()) {
      Map.Entry templateFieldMapping = (Map.Entry) i.next();
      if (!o.getObjFields().containsKey(templateFieldMapping.getKey())) {
        return false;
      }
      if (!templateFieldMapping.getValue().equals(o.getObjFields().get(
          templateFieldMapping.getKey()))) {
        return false;
      }
    }
    return true;
  }

  public boolean equals(Object o) {
    return (o instanceof SpaceObj) &&
        (this.objClass.equals( ( (SpaceObj) o).objClass)) &&
        (this.publicFields.equals( ( (SpaceObj) o).publicFields));
  }

}
