import java.util.*;

/**
 * <p>Title: RemoveObjTask</p>
 * <p>Description:When is called by system timer , the object is marked as
 * not valid and the DisposalsThread is notified.</p>
 * @authors Yael Weinberg and Marcel Apfelbaum
 */

public class RemoveObjTask
    extends TimerTask {

  /** An entry to remove */
  private final SpaceObj obj;

  /** A background thread that removes non-valid objects*/
  private DisposalsThread disposalsThread;

  public RemoveObjTask(SpaceObj obj, DisposalsThread disposalsThread) {
    this.obj = obj;
    this.disposalsThread = disposalsThread;
  }

  /** Removes the entry from the space database */
  public void run() {
    obj.invalidate();
    disposalsThread.addObj(obj);
  }
}
