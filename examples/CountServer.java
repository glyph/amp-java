//import java.util.List;
//import java.util.ArrayList;

import com.twistedmatrix.amp.*;
import com.twistedmatrix.internet.*;

/** To compile: ant buildexamples
 * To run: ant runexserver
 */

public class CountServer extends AMP {
    Reactor _reactor = null;
    
    public CountServer(Reactor reactor) {
	_reactor = reactor;
	
	/** Local methods with parameter names that might be called  
	 * remotely are defined with localCommand. Parameters are:
	 * Command name, local method, local parameters */
	localCommand("Count", "count", new String[] {"n"});
    }
    
    /** Class that handles the results of a command invoked remotely. 
     * The callback method is called with a populated response class. 
     * Returned object is handed to chained callback if it exists. */
    class CountHandler implements Deferred.Callback<CountResp> {
	public Object callback(CountResp retval) {

	    System.out.println("response: " + retval.getResponse());
	    return null;
	}
    }

    /** Class that handles the problem if a remote command goes awry. 
     * The callback method is called with a populated Failure class.  
     * Returned object is handed to chained errback if it exists. */
    class ErrHandler implements Deferred.Callback<Deferred.Failure> {
	public Object callback(Deferred.Failure err) {

	    System.out.println("error: " + err.get());

	    //Class tc = err.trap(Exception.class);

	    System.exit(0);

	    return null;
	}
    }

    /** Command response class and all its variables must be public.
     * Response class data is ignored when calling remote.
     * Upon success the populated response is returned via the Callback.
     * Upon failure the error is returned via the ErrBack. */
    public class CountResp {
	public boolean ok = true;
	public CountResp(boolean b) { ok = b; }
	public boolean getResponse() { return ok; }
    }

    /** Command arguments class and all its variables must be public.
     * This class contains the parameters when invoking the remote command.
     * Command arguments class variables must match remote command arguments. */
    public class CountArgs {
	public int n = 0;

	public CountArgs(int i) { n = i; }
	public int getArg() { return n; }
    }

    /** Methods that might be called remotely must be public */
    public CountResp count (int n) {
	System.out.println("received: " + n + " ");

	CountArgs ca = new CountArgs(n+1);
	CountResp cr = new CountResp(true);

	if (ca.getArg() < 11) {
	    System.out.println("sending: " + ca.getArg());

	    AMP.RemoteCommand<CountResp> remote = 
		new RemoteCommand<CountResp>("Count", ca, cr);
	    Deferred dfd = remote.callRemote();
	    dfd.addCallback(new CountHandler());
	    dfd.addErrback(new ErrHandler());
	} else { _reactor.stop(); }

	return cr;
    }

    @Override public void connectionMade() {
	System.out.println("connected");
    }

    @Override public void connectionLost(Throwable reason) {
	System.out.println("connection lost 1:" + reason);
    }

    public static void main(String[] args) throws Throwable {
	Reactor reactor = Reactor.get();
	reactor.listenTCP(7113, new ServerFactory() {
		public IProtocol buildProtocol(Object addr) {
		    System.out.println("building protocol");
		    return new CountServer(reactor);
		}

		@Override public void startedListening(IListeningPort port) {
		    System.out.println("listening");
		}

		@Override public void connectionLost(IListeningPort port,
						 Throwable reason) {
		    System.out.println("connection lost 2:" + reason);
		}

	    });

	reactor.run();
    }
}
