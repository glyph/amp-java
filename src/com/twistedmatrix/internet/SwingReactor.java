package com.twistedmatrix.internet;

import java.io.IOException;
import javax.swing.SwingUtilities;
import com.twistedmatrix.internet.Reactor;

/**
 * Call 'run()' on a thread that is not the event thread, but perform all I/O
 * operations on the event thread when using this reactor.
 */

public class SwingReactor extends Reactor {

    public SwingReactor() throws IOException {
        super();
    }

    /**
     * This will be invoked from the application thread, telling the run()
     * thread to do some stuff.
     */
    public void interestOpsChanged() {
        wakeup();
    }
    
    /**
     * Inject the application code into the Swing thread.
     */
    public void doIteration() throws Throwable {
        Runnable r = new Runnable () {
                public void run() {
                    try {
                        SwingReactor.this.doIteration();
                    } catch (Throwable t) {
                        // XXX TODO: Do something with this exception.
                        // Re-throw it or something?
                    }
                }
            };
        SwingUtilities.invokeAndWait(r);
    }
}

