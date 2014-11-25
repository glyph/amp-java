package com.twistedmatrix.internet;

import java.util.ArrayList;

public class Deferred {

    /** A deferred callback is how most asynchronous exceptions are handled. 
     * When adding an errBack, a Deferred.Failure is passed should something
     * goes awry. A callback from AMP.callRemote is passed the response. */
    public static class Failure {
        Throwable t;
        public Failure(Throwable throwable) {
            this.t = throwable;
        }

	/** Returns the {@link Throwable} that caused the failure. */
        public Throwable get() { return this.t; }

	/** Returns the {@link Throwable} that caused the failure if it 
	 * is of the requested type, otherwise throws it. */
        public Class trap(Class c) throws Throwable {
            if (c.isInstance(this.t)) {
                return c;
            } else {
                // XXX TODO: chain this Throwable, don't re-throw it.
                throw this.t;
            }
        }
    }

    static class CallbackPair {
        Callback callback;
        Callback errback;

        CallbackPair(Callback callback, Callback errback) {
            if (null == callback) {
                throw new Error("null callback");
            }
            if (null == errback) {
                throw new Error("null errback");
            }
            this.callback = callback;
            this.errback = errback;
        }
    }

    /** A deferred callback is how most asynchronous events are handled.
     *  Callbacks generally return null unless you want to chain them,
     *  in which case the result of one callback is passed to the next. */
    public interface Callback<T> {
	/** Returns populated response object upon successful completion
	 * of the remote command.*/
        Object callback(T retval) throws Exception;
    }

    private static class Passthru implements Callback {
        public Object callback(Object obj) {
            return obj;
        }
    }

    private static final Callback passthru = new Passthru();

    ArrayList<CallbackPair> callbacks;

    Object result;
    boolean calledYet;
    int paused;

    public Deferred() {
        result = null;
        calledYet = false;
        paused = 0;
        callbacks = new ArrayList<CallbackPair>();
    }

    @SuppressWarnings("unchecked")
    private void runCallbacks() {
        if (0 == this.paused) {
            ArrayList<CallbackPair> holder = this.callbacks;
            this.callbacks = new ArrayList<CallbackPair>();
            while (holder.size() != 0) {
                CallbackPair cbp = holder.remove(0);
                Callback theCallback;
                if (this.result instanceof Failure) {
                    theCallback = cbp.errback;
                } else {
                    theCallback = cbp.callback;
                }
                if (null == theCallback) {
                    throw new Error("null callback");
                }
                if (this.result instanceof Deferred) {
                    this.callbacks = holder;
                } else {
                    try {
                        this.result = theCallback.callback(this.result);
                    } catch (Throwable t) {
                        // t.printStackTrace();
                        this.result = new Failure(t);
                    }
                }
            }
        }
    }

    /** Add a pair of callbacks (success and error) to this Deferred. */
    public void addCallbacks(Callback callback,
			     Callback errback) {
        this.callbacks.add(new CallbackPair(callback, errback));
        if (calledYet) {
            this.runCallbacks();
        }
    }

    /** Convenience method for adding just a callback. */
    public void addCallback(Callback callback) {
        this.addCallbacks(callback, passthru);
    }

    /** Convenience method for adding just an errback. */
    public void addErrback(Callback<Failure> errback) {
        this.addCallbacks(passthru, (Callback) errback);
    }

    /** Convenience method for adding a single callable as both a callback and an errback. */
    public void addBoth(Callback callerrback) {
        this.addCallbacks(callerrback, callerrback);
    }

    /** Run all success callbacks that have been added to this Deferred. */
    public void callback(Object result) {
        if (calledYet) {
            throw new Error("Already Called!");
        }
        this.result = result;
        this.calledYet = true;
        runCallbacks();
    }

    /** Run all error callbacks that have been added to this Deferred. */
    public void errback(Failure result) {
        callback(result);
    }

    /** Stop processing on a Deferred until unpause() is called. */
    public void pause() {
        this.paused++;
    }

    /** Process all callbacks made since pause() was called. */
    public void unpause() {
        this.paused--;
        if (0 != this.paused) {
            return;
        }
        if (this.calledYet) {
            this.runCallbacks();
        }
    }

}
