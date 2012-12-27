import java.util.*;

/**
 * <p>Title: CleaningTask</p>
 * <p>Description: Cleans the upper levels from a Space Server.
 * It is called by system timer at fixed "long" intervals</p>
 * @authors Yael Weinberg and Marcel Apfelbaum
 */
public class CleaningTask
    extends TimerTask {

  /** Server to clean */
  SpaceServer server;

  /** thread caches */
  private Set fieldsCache;
  private Map valuesCache;

  /** adds a SpaceField to its cache. */
  public void addField(SpaceField sf) {
    synchronized (fieldsCache) {
      fieldsCache.add(sf);
    }
  }

  /** adds a FieldValue to its cache. */
  public void addValue(FieldValue fv, SpaceField sf) {
    synchronized (valuesCache) {
      Set values = (Set) valuesCache.get(sf);
      if (values == null) {
        values = new HashSet();
        valuesCache.put(sf, values);
      }
      values.add(fv);
    }
  }

  public CleaningTask(SpaceServer server) {
    this.server = server;
    fieldsCache = Collections.synchronizedSet(new HashSet());
    valuesCache = Collections.synchronizedMap(new HashMap());
  }

  /** Removes the fields and values from the space database .
   * Is called at fixed intervals by a timer thread
   * */
  public void run() {
    Set tempFields = new HashSet();
    Map tempValues = new HashMap();
    // gets the caches into teporary data structures
    synchronized (fieldsCache) {
      tempFields.addAll(fieldsCache);
      fieldsCache.clear();
    }
    synchronized (valuesCache) {
      tempValues.putAll(valuesCache);
      valuesCache.clear();
    }

    // Ask the coressponding  SpaceField to delete the specified  values
    if (!tempValues.isEmpty()) {
      Iterator i = tempValues.entrySet().iterator();
      while (i.hasNext()) {
        Map.Entry m = (Map.Entry) i.next();
        // protection by an internal rw semaphore
        ( (SpaceField) m.getKey()).cleanValues( (Set) m.getValue());
      }
      valuesCache.clear();
    }
    // Ask the SpaceServer to delete the specific fields
    if (!tempFields.isEmpty()) {
      // protection by an internal rw semaphore
      server.cleanFields(tempFields);
      tempFields.clear();
    }
  }
}
