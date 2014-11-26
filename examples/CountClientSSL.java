import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import com.twistedmatrix.amp.*;
import com.twistedmatrix.internet.*;

/** To compile: ant buildexamples
    To run: java -cp classes CountClientSSL
 */

public class CountClientSSL extends AMP {
    Reactor _reactor = null;

    public CountClientSSL(Reactor reactor) {
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
	    //System.out.println("error: " + err.get());
	    err.get().printStackTrace();
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
	public List<Integer> okl1 = new ArrayList<Integer>();
	public List<List<String>> okl2 = new ArrayList<List<String>>();
	public List<CountItem> okla = new ArrayList<CountItem>();

	public CountResp() {
	    okt.setTimeZone(TimeZone.getTimeZone("UTC"));
	    okl1.add(new Integer(3));
	    okl1.add(new Integer(6));

	    List<String> al1 = new ArrayList<String>();
	    List<String> al2 = new ArrayList<String>();
	    al1.add("str01");
	    al1.add("str02");
	    al2.add("str03");
	    al2.add("str04");
	    al2.add("str05");
	    okl2.add(al1);
	    okl2.add(al2);

	    okla.add(new CountItem(2, "hello"));
	    okla.add(new CountItem(4, "goodbye"));
	}

	public String getResponse() {
	    String str =  "Int: " + oki + " String: " + oks +
		" Unicode: " + oku + " Boolean: " + okb +
		" Double: " + okf + " Decimal: " + okd +
		" DateTime: " + okt + " ListOf1: ";
	    for (Integer i: okl1)
		str = str + " " + i;
	    str = str + " ListOf 2: ";
	    for (List<String> ls: okl2)
		for (String li: ls)
		    str = str + " " + li;
	    str = str + " AmpList: ";
	    for (CountItem ci: okla)
		str = str + ci + " ";

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

	if (rp.getArg() < 10) {
	    System.out.println("sending: " + rp.getArg());

	    AMP.RemoteCommand<CountResp> remote =
		new RemoteCommand<CountResp>("Count", rp, cr);
	    Deferred dfd = remote.callRemote();
	    dfd.addCallback(new RespHandler());
	    dfd.addErrback(new ErrHandler());
	} else {
	    _reactor.stop();
	    System.exit(0);
	}

	return cr;
    }

    /** The example the client and server use the same method and classes to
     * exchange data, but the client initiates this process upon connection */
    @Override public void connectionMade() {
	System.out.println("connected");

	RemoteParams rp = new RemoteParams(1);
	CountResp cr = new CountResp();

	System.out.println("sending: 1");
	AMP.RemoteCommand<CountResp> remote =
	    new RemoteCommand<CountResp>("Count", rp, cr);
	Deferred dfd = remote.callRemote();
	dfd.addCallback(new RespHandler());
	dfd.addErrback(new ErrHandler());
    }

    @Override public void connectionLost(Throwable reason) {
	System.out.println("connection lost 1:" + reason);
    }

    /** This context validates the server certificate, which is GOOD. */
    private static SSLContext getSecureContext() {
	// java -Djavax.net.debug=SSL
	SSLContext ctx = null;
	try {
	    ctx = SSLContext.getDefault();
	} catch (Exception e) { e.printStackTrace(); }

	return ctx;
    }

    /** This context does NOT validate the server certificate, which is BAD. */
    private static SSLContext getInsecureContext() {
	TrustManager[] trustAll = new TrustManager[]{ new X509TrustManager() {
		public X509Certificate[] getAcceptedIssuers() {
		    return new X509Certificate[0];
		}
		public void checkClientTrusted(X509Certificate[] certs,
					       String authType){ }
		public void checkServerTrusted(X509Certificate[] certs,
					       String authType){ }
	    }};

	SSLContext ctx = null;
	try {
	    ctx = SSLContext.getInstance("TLS");
	    ctx.init(null, trustAll, new SecureRandom());
	} catch (Exception e) { e.printStackTrace(); }

	return ctx;
    }

    /** This context loads the server certificate, which is GOOD ENOUGH. */
    private static SSLContext getCustomContext() {
	// The alias/password for localhost.ks is importkey/password

	SSLContext ctx = null;
	try {
	    KeyStore ks = KeyStore.getInstance("JKS");
	    ks.load(new FileInputStream("examples/localhost.ks"),
		    "importkey".toCharArray());
	    ctx = SSLContext.getInstance("TLS");
	    TrustManagerFactory tmf =TrustManagerFactory.getInstance("SunX509");
	    tmf.init(ks);
	    ctx.init(null, tmf.getTrustManagers(), null);
	} catch (Exception e) { e.printStackTrace(); }

	return ctx;
    }

    /** To convert a standard self signed certificate to a java keystore:
     * openssl pkcs8 -topk8 -nocrypt -in localhost.key -inform PEM -out localhost.key.der -outform DER
     * openssl x509 -in localhost.crt -inform PEM -out localhost.crt.der -outform DER
     * java ImportKey localhost.key.der localhost.crt.der
     * keytool -keypasswd -alias importkey -keystore localhost.ks
     */

    public static void main(String[] args) throws Throwable {
	Reactor reactor = Reactor.get();
	reactor.connectSSL("127.0.0.1", 7113, getCustomContext(),
			   new ClientFactory() {
		public IProtocol buildProtocol(Object addr) {
		    System.out.println("building protocol");
		    return new CountClientSSL(reactor);
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
