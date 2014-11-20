import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.ArrayList;
import java.util.List;

import com.twistedmatrix.amp.*;
import com.twistedmatrix.internet.*;

/** To compile: ant buildexamples
 * To run: ant runexserver
 */

public class CountServer extends AMP {
    Reactor _reactor = null;

    public CountServer(Reactor reactor) {
	_reactor = reactor;

	/** Define a local method that might be called remotely. */
	localCommand("Count", new CountCommand());
    }

    /** Class that handles the results of a command invoked remotely.
     * The callback method is called with a populated response class.
     * Returned object is handed to chained callback if it exists. */
    class RespHandler implements Deferred.Callback<CountResp> {
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

	    //Class tc = err.trap(Exception.class);
	    System.out.println("error: " + err.get());
	    System.exit(0);

	    return null;
	}
    }

    /** Command response class and all its variables must be public.
     * In this example the client and server have the same response.
     * Response class data is ignored when calling remote.
     * Upon success the populated response is returned via the Callback.
     * Upon failure the error is returned via the ErrBack. */
    public class CountResp {
	public Integer oki = 1;
	public ByteBuffer oks = ByteBuffer.wrap("2".getBytes());
	public String oku = "3";
	public Boolean okb = true;
	public Double okf = Double.valueOf("5.123");
	public BigDecimal okd = new BigDecimal("0.75");
	public Calendar okt = Calendar.getInstance();
	public List<List<String>> okl = new ArrayList<List<String>>();

	public CountResp() {
	    okt.setTimeZone(TimeZone.getTimeZone("UTC"));
	    List<String> al1 = new ArrayList<String>();
	    List<String> al2 = new ArrayList<String>();
	    al1.add("str01");
	    al1.add("str02");
	    al2.add("str03");
	    al2.add("str04");
	    al2.add("str05");

	    okl.add(al1);
	    okl.add(al2);
	}

	public String getResponse() {
	    String str =  "Int: " + oki + " String: " + oks +
		" Unicode: " + oku + " Boolean: " + okb +
		" Double: " + okf + " Decimal: " + okd +
		" DateTime: " + okt + " ListOf: ";
	    for (List<String> ls: okl)
		for (String li: ls)
		    str = str + " " + li;

	    return str;
	}
    }

    /** Remote command arguments class and all its variables must be public.
     * This class contains the parameters when invoking the remote command.
     * Class variables must be public and match remote command arguments. */
    public class RemoteParams {
	public int n = 0;

	public RemoteParams(int i) { n = i; }
	public int getArg() { return n; }
    }

    /** This class contains the name and parameters of a local command.
     * It defines the name, the parameter names in order, and parameter classes.
     * Class variables must be public and match local command arguments. */
    public class CountCommand extends LocalCommand {
	public int n;
	public CountCommand() { super("count", new String[] {"n"} ); }
    }

    /** Methods that might be called remotely must be public */
    public CountResp count (int n) {
	System.out.println("received: " + n);

	RemoteParams rp = new RemoteParams(n+1);
	CountResp cr = new CountResp();

	if (rp.getArg() < 11) {
	    System.out.println("sending: " + rp.getArg());

	    AMP.RemoteCommand<CountResp> remote =
		new RemoteCommand<CountResp>("Count", rp, cr);
	    Deferred dfd = remote.callRemote();
	    dfd.addCallback(new RespHandler());
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
