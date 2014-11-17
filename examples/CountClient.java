//import java.util.List;
//import java.util.Arrays;
//import java.util.ArrayList;

import com.twistedmatrix.amp.*;
import com.twistedmatrix.internet.*;

/** To compile: ant buildexamples
    To run: ant runexclient
 */

public class CountClient extends AMP {
    Reactor _reactor = null;
    
    public CountClient(Reactor reactor) {
	_reactor = reactor;
	
	/** Local methods with parameter names that might be called  
	 * remotely are defined with localCommand. Parameters are:
	 * Command name, local method, local parameters*/
	localCommand("Count", "count", new String[] {"n"});
    }
    
    /** Class that handles the results of a command invoked remotely. */
    class CountHandler implements Deferred.Callback {
	public Object callback(Object retval) {
	    CountResp ret = (CountResp) retval;
	    
	    System.out.println("response: " + ret.getResponse());
	    return null;
	}
    }
    
    /** Class that handles the problem if a remote command goes awry. */
    class ErrHandler implements Deferred.Callback {
	public Object callback(Object retval) {
	    Deferred.Failure err = (Deferred.Failure) retval;
	    
	    System.out.println("error: " + err.get());
	    
	    //Class tc = err.trap(Exception.class);
	    
	    System.exit(0);
	    
	    return null;
	}
    }
    
    /** Command response class and all its variables must be public
     * Response class data is ignored when calling remote
     * Upon success the populated response is returned via the Callback
     * Upon failure the error is returned via the ErrBack */
    public class CountResp {
	public boolean ok = true;
	public CountResp(boolean b) { ok = b; }
	public boolean getResponse() { return ok; }
    }
    
    /** Command arguments class and all its variables must be public
     * This class contains the parameters when invoking the remote command
     * Command arguments class variables must match remote command arguments */
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
	
	if (ca.getArg() < 10) {
	    System.out.println("sending: " + ca.getArg());
	    
	    Deferred dfd = callRemote("Count", ca, cr);
	    dfd.addCallback(new CountHandler());
	    dfd.addErrback(new ErrHandler());
	} else { _reactor.stop(); }
	
	return cr;
    }
    
    /** The example the client and server use the same method and classes to 
     * exchange data, but the client initiates this process upon connection */
    @Override public void connectionMade() {
	System.out.println("connected");
	
	CountArgs ca = new CountArgs(1);
	CountResp cr = new CountResp(true);
	
	Deferred dfd = callRemote("Count", ca, cr);
	dfd.addCallback(new CountHandler());
	dfd.addErrback(new ErrHandler());
    }
    
    @Override public void connectionLost(Throwable reason) {
	System.out.println("connection lost 1:" + reason);
    }
        
    public static void main(String[] args) throws Throwable {
	Reactor reactor = Reactor.get();
	reactor.connectTCP("127.0.0.1", 7113, new ClientFactory() {
		public IProtocol buildProtocol(Object addr) {
		    System.out.println("building protocol");
		    return new CountClient(reactor);
		}

		public void clientConnectionFailed(IConnector connector,
						   Throwable reason) {
		    System.out.println("connectiion failed:" + reason);
		    System.exit(0);
		}

		@Override public void startedConnecting(IConnector connector) {
		    System.out.println("connecting");
		}
		
		@Override public void clientConnectionLost(IConnector connector,
							   Throwable reason) {
		    System.out.println("connection lost 2:" + reason);
		}
	    });

	reactor.run();
    }
}
