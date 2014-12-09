package com.twistedmatrix.internet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.ConnectException;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.util.List;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

/** The reactor is an event loop based on {@link SelectionKey} which drives
 * applications and provides APIs for networking, threading, dispatching
 * events, and more.
 * New application code should prefer to pass and accept the reactor as a
 * parameter where it is needed, which will simplify unit testing and may make
 * it easier to one day support multiple reactors.
*/
public class Reactor {
    private static int	                BUFFER_SIZE  = 32 * 1024;

    private	TCPConnection           _connection;
    private	Selector                _selector;
    private	boolean		        _running;
    private	TreeMap<Long,Runnable>  _pendingCalls;

    public Reactor () throws IOException {
	_selector = Selector.open();
	_running = false;
	_pendingCalls = new TreeMap<Long,Runnable>();
    }

    /* It appears that this interface is actually unnamed in
     * Twisted... abstract.FileDescriptor serves this purpose.
     */
    private class Selectable {
	protected SelectionKey _key;

	public boolean isConnecting() {
	    msg("UNHANDLED ISCONNECTING "+this);
	    return false;
	}
	public void doRead() throws Throwable {
	    msg("UNHANDLED READ "+this);
	}
	public void doWrite() throws Throwable {
	    msg("UNHANDLED WRITE "+this);
	}
	public void doAccept() throws Throwable {
	    msg("UNHANDLED ACCEPT "+this);
	}
	public void doConnect() throws Throwable {
	    msg("UNHANDLED CONNECT "+this);
	}
    }

    private abstract class TCPConnection
	extends Selectable implements ITransport {
	private ByteBuffer	   inbuf;
	private ArrayList<byte[]>  outbufs;

	protected SocketChannel    channel;
	protected Socket	   socket;
	protected IProtocol	   protocol;
	protected boolean	   disconnecting;

	/* Used for encrypted connections */
	protected SSLEngine  engine;
	protected ByteBuffer wrapSrc, unwrapSrc, wrapDst, unwrapDst;

	TCPConnection(int portno) throws Throwable {
	    _connection = this;
	    this.inbuf = ByteBuffer.allocate(BUFFER_SIZE);
	    this.inbuf.clear();
	    this.outbufs = new ArrayList<byte[]>();
	    this.disconnecting = false;

	    this.wrapSrc = ByteBuffer.allocateDirect(BUFFER_SIZE);
	    this.wrapDst = ByteBuffer.allocateDirect(BUFFER_SIZE * 8);
	    this.unwrapSrc = ByteBuffer.allocateDirect(BUFFER_SIZE * 8);
	    this.unwrapDst = ByteBuffer.allocateDirect(BUFFER_SIZE);
	    this.unwrapSrc.limit(0);
	}

	// HAHAHAHA the fab four strike again
	protected void startReading() {
	    _key.interestOps(_key.interestOps() | SelectionKey.OP_READ);
	    interestOpsChanged();
	}

	private void startWriting () {
	    _key.interestOps(_key.interestOps() | SelectionKey.OP_WRITE);
	    interestOpsChanged();
	}

	private void stopReading () {
	    _key.interestOps(_key.interestOps() & ~SelectionKey.OP_READ);
	    interestOpsChanged();
	}

	private void stopWriting () {
	    _key.interestOps(_key.interestOps() & ~SelectionKey.OP_WRITE);
	    interestOpsChanged();
	}

	public void doRead() throws Throwable {
	    boolean failed = false;
	    Throwable reason = null;
	    try {
		int bytesread = channel.read(inbuf);
		failed = (-1 == bytesread);
	    } catch (IOException ioe) {
		failed = true;
		reason = ioe;
	    }

	    if (failed) {
		if (this.engine != null) {
		    this.engine.closeOutbound();
		    while (this.step()) { continue; }
		}

		channel.close();
		_key.cancel();
		interestOpsChanged();

		if (null == reason)
		    reason = new IOException("Connection reset by peer");
		this.connectionLost(reason);
		return;
	    }

	    byte[] data = new byte[inbuf.position()];
	    inbuf.flip();
	    inbuf.get(data);
	    inbuf.clear();
	    try {
		if (this.engine == null) {
		    this.protocol.dataReceived(data);
		} else {
		    unwrapSrc.put(data);
		    while (this.step()) { continue; }
		}
	    } catch (Throwable t) {
		t.printStackTrace();
		this.connectionLost(t);
	    }
	}

	public void write(byte[] data) {
	    if (this.engine == null) {
		this.outbufs.add(data);
		this.startWriting();
	    } else {
		wrapSrc.put(data);
		while (this.step()) { continue; }
	    }
	}

	public void doWrite() throws Throwable {
	    if (0 == this.outbufs.size()) {
		if (this.disconnecting) {
		    this.channel.close();
		  this.connectionLost(new Throwable("Disconnected"));
		}
	    } else {
		this.channel.write(ByteBuffer.wrap(this.outbufs.remove(0)));
		if (0 == this.outbufs.size()) {
		    this.stopWriting();
		}
	    }
	}

	/* Actually encrypt outgoing data */
	protected boolean wrap() {
	    SSLEngineResult wrapResult;

	    try {
		wrapSrc.flip();
		wrapDst.clear();
		wrapResult = engine.wrap(wrapSrc, wrapDst);
		wrapSrc.compact();
	    } catch (SSLException exc) {
		exc.printStackTrace();
		this.connectionLost(exc);
		return false;
	    }

	    switch (wrapResult.getStatus()) {
	    case OK:
		if (wrapDst.position() > 0) {
		    wrapDst.flip();
		    byte[] bytes = new byte[wrapDst.remaining()];
		    wrapDst.get(bytes);
		    this.outbufs.add(bytes);
		    this.startWriting();
		}
		break;

	    case BUFFER_UNDERFLOW:
		// try again later
		break;

	    case BUFFER_OVERFLOW:
		throw new IllegalStateException("Buffer overflow in wrap!");

	    case CLOSED:
		//this.protocol.connectionLost(new Throwable("Disconnected"));
		return false;
	    }

	    return true;
	}

	/* Actually decrypt incoming data */
	protected boolean unwrap() {
	    SSLEngineResult unwrapResult;

	    try {
		unwrapSrc.flip();
		unwrapDst.clear();
		unwrapResult = engine.unwrap(unwrapSrc, unwrapDst);
		unwrapSrc.compact();
	    } catch (SSLException exc) {
		exc.printStackTrace();
		this.connectionLost(exc);
		return false;
	    }

	    switch (unwrapResult.getStatus()) {
	    case OK:
		if (unwrapDst.position() > 0) {
		    unwrapDst.flip();
		    byte[] bytes = new byte[unwrapDst.remaining()];
		    unwrapDst.get(bytes);
		    this.protocol.dataReceived(bytes);
		}
		break;

	    case CLOSED:
		this.connectionLost(new Throwable("Disconnected"));
		return false;

	    case BUFFER_OVERFLOW:
		throw new IllegalStateException("Buffer overflow in unwrap!");

	    case BUFFER_UNDERFLOW:
		return false;
	    }

	    switch (unwrapResult.getHandshakeStatus()) {
	    case FINISHED:
		this.protocol.makeConnection(this);

		/*
		SSLSession session = engine.getSession();
		try {
		    System.out.println("- local principal: " +
				       session.getLocalPrincipal());
		    System.out.println("- remote principal: " +
				       session.getPeerPrincipal());
		    System.out.println("- using cipher: " +
				       session.getCipherSuite());
		} catch (Exception exc) { exc.printStackTrace(); }
		*/

		return false;
	    }

	    return true;
	}

	/* Encryption housekeeping */
	protected boolean step() {
	    switch (engine.getHandshakeStatus()) {
	    case NOT_HANDSHAKING:
		boolean anything = false;
		if (wrapSrc.position() > 0)
		    anything |= this.wrap();
		if (unwrapSrc.position() > 0)
		    anything |= this.unwrap();
		return anything;

	    case NEED_WRAP:
		if (!this.wrap())
		    return false;
		break;

	    case NEED_UNWRAP:
		if (!this.unwrap())
		    return false;
		break;

	    case NEED_TASK:
		Runnable task = null;
		Executor exec = Executors.newSingleThreadExecutor();
		while ((task = engine.getDelegatedTask()) != null) {
		    exec.execute(task);
		    while (this.step()) { continue; }
		}

		return true;

	    case FINISHED:
		throw new IllegalStateException("FINISHED");
	    }

	    return true;
	}

	public boolean isConnecting() {
	    return channel.isConnectionPending();
	}

	public abstract void connectionLost(Throwable reason);
	public abstract void loseConnection(Throwable reason);
    }

    /** Implements the bulk of the tcp server support */
    private class TCPPort extends TCPConnection implements IListeningPort {
	protected ServerFactory       serverFactory;
	protected ServerSocketChannel schannel;
	private   ServerSocket	      ssocket;
	private   InetSocketAddress   addr;

	TCPPort(int port, ServerFactory sf) throws Throwable {
	    super(port);
	    this.serverFactory = sf;
	    this.addr = new InetSocketAddress(port);

	    this.schannel = ServerSocketChannel.open();
	    this.schannel.configureBlocking(false);
	    this.ssocket = schannel.socket();
	    this.ssocket.bind(this.addr);
	    this.protocol = sf.buildProtocol(this.addr);

	    _key = schannel.register(_selector, SelectionKey.OP_ACCEPT,this);
	    interestOpsChanged();

	    this.startListening();
	}

	public InetSocketAddress getHost() { return this.addr; }

	public void startListening() throws Throwable {
	    _key.interestOps(_key.interestOps() | SelectionKey.OP_ACCEPT);
	    interestOpsChanged();
	    this.serverFactory.startedListening(this);
	}

	public void doAccept() throws Throwable {
	    SocketChannel newchannel = schannel.accept();
	    if (null == newchannel) {
		throw new Throwable("Unable to accept connection!");
	    } else {
		channel = newchannel;
		channel.configureBlocking(false);
		socket = newchannel.socket();
		socket.setTcpNoDelay(true);

		_key = channel.register(_selector,SelectionKey.OP_READ,this);
		interestOpsChanged();

		super.startReading();
		this.protocol.makeConnection(this);
	    }
	}

	public void connectionLost(Throwable reason) {
	    this.serverFactory.connectionLost(this, reason);
	}

	public void loseConnection(Throwable reason) {
	    if (this.engine != null) {
		this.engine.closeOutbound();
		while (this.step()) { continue; }
	    }

	    this.disconnecting = true;
	    _key.interestOps(_key.interestOps() & ~SelectionKey.OP_ACCEPT);
	    _key.cancel();
	    interestOpsChanged();
	}
     }

    /** Implements the SSL server support */
    private class SSLPort extends TCPPort {
	private SSLContext _ctx;

	SSLPort(int port, SSLContext ctx, ServerFactory sf) throws Throwable {
	    super(port, sf);
	    _ctx = ctx;
	}

	@Override public void doAccept() throws Throwable {
	    SocketChannel newchannel = schannel.accept();
	    if (null == newchannel) {
		throw new Throwable("Unable to accept connection!");
	    } else {
		channel = newchannel;
		channel.configureBlocking(false);
		socket = newchannel.socket();
		socket.setTcpNoDelay(true);

		_key = channel.register(_selector,SelectionKey.OP_READ,this);
		interestOpsChanged();

		String[] ecs = this.serverFactory.getEnabledCipherSuites();

		super.startReading();
		this.engine = _ctx.createSSLEngine();

		if (ecs != null && ecs.length > 0)
		    this.engine.setEnabledCipherSuites(ecs);

		this.engine.setUseClientMode(false);
		this.engine.beginHandshake();
		this.wrap(); // Initiate the handshake
		while (this.step()) { continue; } // Complete the handshake
		this.protocol.makeConnection(this);
	    }
	}
    }

    /** Implements the bulk of the tcp client support */
    private class TCPConnect extends TCPConnection implements IConnector {
	protected ClientFactory     clientFactory;
	private InetSocketAddress addr;

	TCPConnect(String host, int port, ClientFactory cf) throws Throwable {
	    super(port);
	    this.clientFactory = cf;
	    this.addr = new InetSocketAddress(host, port);

	    this.channel = SocketChannel.open();
	    this.channel.configureBlocking(false);
	    this.socket = channel.socket();
	    this.protocol = cf.buildProtocol(this.addr);

	    _key = channel.register(_selector, SelectionKey.OP_CONNECT,this);
	    interestOpsChanged();

	    this.connect();
	}

	public InetSocketAddress getDestination() { return this.addr; }

	public void connect() throws Throwable {
	    this.isConnecting();
	    this.clientFactory.startedConnecting(this);
	    this.channel.connect(this.addr);
	    this.channel.configureBlocking(false);
	    this.socket.setTcpNoDelay(true);
	    _key.interestOps(_key.interestOps() | SelectionKey.OP_CONNECT);
	    interestOpsChanged();
	}

	public void loseConnection(Throwable reason) {
	    _key.interestOps(_key.interestOps() & ~SelectionKey.OP_CONNECT);
	    _key.cancel();
	    interestOpsChanged();

	    try {
		if (this.engine != null) {
		    this.engine.closeOutbound();
		    while (this.step()) { continue; }
		}
		this.channel.close();
	    } catch (IOException e) {
		e.printStackTrace();
	    }
	}

	public void stopConnecting() {
	    _key.interestOps(_key.interestOps() & ~SelectionKey.OP_CONNECT);
	    _key.cancel();
	    interestOpsChanged();

	    try {
		this.channel.close();
	    } catch (IOException e) {
		e.printStackTrace();
	    }
	}

	 public void doConnect() throws Throwable {
	    try {
		this.channel.finishConnect();
		super.startReading();
		this.protocol.makeConnection(this);
	    } catch (ConnectException e) {
		connectionFailed(e);
	    }
	}

	public void connectionFailed(Throwable reason) {
	    this.clientFactory.clientConnectionFailed(this, reason);
	}

	public void connectionLost(Throwable reason) {
	    this.protocol.connectionLost(reason);
	    this.clientFactory.clientConnectionLost(this, reason);
	}
    }

    /** Implements the SSL client support */
    private class SSLConnect extends TCPConnect {
	private SSLContext _ctx;

	SSLConnect(String host, int port, SSLContext ctx,
		   ClientFactory cf) throws Throwable {
	    super(host, port, cf);
	    _ctx = ctx;
	}

	@Override public void doConnect() throws Throwable {
	    try {
		String[] ecs = this.clientFactory.getEnabledCipherSuites();
		this.channel.finishConnect();
		this.engine = _ctx.createSSLEngine();

		if (ecs != null && ecs.length > 0)
		    this.engine.setEnabledCipherSuites(ecs);

		this.engine.setUseClientMode(true);
		this.engine.beginHandshake();

		super.startReading();
		this.wrap(); // Initiate the handshake
		while (this.step()) { continue; } // Complete the handshake
	    } catch (ConnectException e) {
		connectionFailed(e);
	    }
	}
    }

   /**
     * Run all runnables scheduled to run before right now, and return the
     * timeout.  Negative timeout means "no timeout".
     */
    private long runUntilCurrent(long now) {
	while (0 != _pendingCalls.size()) {
	    try {
		long then = _pendingCalls.firstKey();
		if (then < now) {
		    Runnable r = _pendingCalls.remove((Object) new Long(then));
		    r.run();
		} else {
		    return then - now;
		}
	    } catch (NoSuchElementException nsee) {
		nsee.printStackTrace();
		throw new Error("Impossible; _pendingCalls.size was not zero");
	    }
	}
	return -1;
    }

    private void iterate() throws Throwable {
	Iterator<SelectionKey> selectedKeys=_selector.selectedKeys().iterator();
	while (selectedKeys.hasNext()) {
	    SelectionKey _key = selectedKeys.next();
	    Selectable selectable = ((Selectable) _key.attachment());
	    if (_key.isValid() && _key.isWritable() &&
		!selectable.isConnecting()) {
		selectable.doWrite();
	    }
	    if (_key.isValid() && _key.isReadable() &&
		!selectable.isConnecting()) {
		selectable.doRead();
	    }
	    if (_key.isValid() && _key.isAcceptable()) {
		selectable.doAccept();
	    }
	    if (_key.isValid() && _key.isConnectable()) {
		selectable.doConnect();
	    }
	    selectedKeys.remove();
	}
    }

    /**
     * This may need to run the runUntilCurrent() in a different thread.
     */
    private long processTimedEvents() {
	long now = System.currentTimeMillis();
	return runUntilCurrent(now);
    }

    /** Runs something later. */
    public void callLater(double secondsLater, Runnable runme) {
	long millisLater = (long) (secondsLater * 1000.0);
	synchronized(_pendingCalls) {
	    _pendingCalls.put(System.currentTimeMillis() + millisLater, runme);
	    // This isn't actually an interestOps
	    interestOpsChanged();
	}
    }

    /** Connect a client protocol factory to a remote SSL server.  */
    public IConnector connectSSL(String addr, int portno, SSLContext ctx,
				 ClientFactory factory) throws Throwable {
	return new SSLConnect(addr, portno, ctx, factory);
    }

    /** Connect a client protocol factory to a remote TCP server. */
    public IConnector connectTCP(String addr, int portno,
				 ClientFactory factory) throws Throwable {
	return new TCPConnect(addr, portno, factory);
    }

    /**
     * Override this method in subclasses to iterate in a different thread.
     */
    public void doIteration() throws Throwable {
	iterate();
    }

    /**
     * Convienence method to get and instance of a Reactor.
     */
    public static Reactor get() {
	Reactor theReactor = null;
	try {
	    theReactor = new Reactor();
	} catch (IOException ioe) {
	    ioe.printStackTrace();
	}
	return theReactor;
    }

    /**
     * Selectors were added or removed.
     *
     * In the normal case, this is simply a no-op.  However, in the case where
     * the "run()" thread is different from the application code thread, this
     * is a hook for the reactor to "wakeup" its selector.
     */
    public void interestOpsChanged() { }

    /** Connects a SSL server protocol factory to a numeric TCP port. */
    public IListeningPort listenSSL(int portno, SSLContext ctx,
				    ServerFactory factory) throws Throwable {
	return new SSLPort(portno, ctx, factory);
    }

    /** Connects a TCP server protocol factory to a numeric TCP port. */
    public IListeningPort listenTCP(int portno,
				    ServerFactory factory) throws Throwable {
	return new TCPPort(portno, factory);
    }

    /** Convenience method to print to STDOUT. */
    public static void msg (String m) {
	System.out.println(m);
    }

    /** Fire 'startup' System Events, move the reactor to the 'running' state,
     * then run the main loop until it is stopped with stop().  */
    public void run() throws Throwable {
	_running = true;
	while (_running) {
	    int selected;
	    long timeout = processTimedEvents();

	    if (timeout >= 0) {
		_selector.select(timeout);
	    } else {
		_selector.select();
	    }
	    this.doIteration();
	}
	_connection.connectionLost(new Throwable("Shutdown"));
    }

    /** Fire 'shutdown' System Events, which will move the reactor to the
     * 'stopped' state and cause reactor.run() to exit. */
    public void stop() {
	_running = false;
    }

    /** Called from other threads to cause this thread to process any
     * pending requests. */
    public void wakeup() { _selector.wakeup(); }

}
