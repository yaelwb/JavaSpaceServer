import java.util.Vector;
/**
 * <p>Title: RWSemaphore </p>
 * <p>Description: A classical many readers one writer semaphore.</p>
 * @authors Yael Weinberg and Marcel Apfelbaum
 */

public class RWSemaphore {

  /** A list of reader threads which currently own the lock*/
  private Vector currentReaders = new Vector();

  /** A queue of threads waiting the lock*/
  private Vector queue = new Vector();

  /** A single writer thread that own the lock */
  private Thread writer = null;

  /** Returns true if a writer owns the lock, false otherwise  */
  public synchronized boolean lockedByWriter() {
    return writer != null;
  }

  /** Returns the number of waiting threads */
  public synchronized int getWaitingNo() {
    return queue.size();
  }

 /** Many readers are allowed to hold the lock of this semaphore  */
  public synchronized void aquireReadersLock() {
    Thread callingThread = Thread.currentThread();
    if (writer != null || !queue.isEmpty()) {
      queue.addElement(callingThread);
      while (writer != null || callingThread != queue.firstElement())
        try {wait();}
        catch (InterruptedException ex) {}
        queue.removeElement(callingThread);
    }
    currentReaders.addElement(callingThread);
    notifyAll();
  }

 /** One writer allowed to hold the lock of this semaphore  */
  public synchronized void aquireWriterLock() {
    Thread callingThread = Thread.currentThread();

    if (writer != null || !currentReaders.isEmpty()) {
      queue.addElement(callingThread);
      while (writer != null || !currentReaders.isEmpty() ||
              callingThread != queue.firstElement())
        try { wait();}
        catch (InterruptedException ex) {}
        queue.removeElement(callingThread);
    }
    writer = callingThread;
  }

/** Release the semaphore lock */
  public synchronized void releaseLock() {
    Thread callingThread = Thread.currentThread();
    if (currentReaders.contains(callingThread)) {
      currentReaders.removeElement(callingThread);
      if (currentReaders.isEmpty()) {
        notifyAll();
      }
    }
    else if (callingThread == writer) {
      writer = null;
      notifyAll();
    }
  }
}
