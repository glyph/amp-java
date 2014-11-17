package com.twistedmatrix.internet;

import java.util.List;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.Iterator;
import java.util.NoSuchElementException;
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

public class Reactor {
    private static int                    BUFFER_SIZE  = 8 * 1024;

    private        TCPConnection          connection;
    private        Selector               selector;
    private        boolean                running;
    private        TreeMap<Long,Runnable> pendingCalls;

    public Reactor () throws IOException {
        this.selector = Selector.open();
        this.running = false;
        this.pendingCalls = new TreeMap<Long,Runnable>();
    }

    /* It appears that this interface is actually unnamed in
     * Twisted... abstract.FileDescriptor serves this purpose.
     */
    private class Selectable {
        protected SelectionKey sk;

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

    abstract private class TCPConnection
	extends Selectable implements ITransport {
	private ByteBuffer         inbuf;
	private ArrayList<byte[]>  outbufs;

        protected SocketChannel     channel;
        protected Socket            socket;
	protected IProtocol         protocol;
        protected boolean           disconnecting;

	TCPConnection(int portno) throws Throwable {
	    connection = this;
            this.inbuf = ByteBuffer.allocate(BUFFER_SIZE);
            this.inbuf.clear();
            this.outbufs = new ArrayList<byte[]>();
            this.disconnecting = false;
        }

        // HAHAHAHA the fab four strike again
        private void startReading() {
            this.sk.interestOps(sk.interestOps() | SelectionKey.OP_READ);
            interestOpsChanged();
        }

        private void startWriting () {
            this.sk.interestOps(sk.interestOps() | SelectionKey.OP_WRITE);
            interestOpsChanged();
        }

        private void stopReading () {
            this.sk.interestOps(sk.interestOps() & ~SelectionKey.OP_READ);
            interestOpsChanged();
        }

        private void stopWriting () {
            this.sk.interestOps(sk.interestOps() & ~SelectionKey.OP_WRITE);
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
                // this means the connection is closed, what???
                channel.close();
                sk.cancel();
                interestOpsChanged();

		if (null == reason)
		    reason = new IOException("Connection reset by peer");
                this.protocol.connectionLost(reason);
                return;
            }

            byte[] data = new byte[inbuf.position()];
            inbuf.flip();
            inbuf.get(data);
            inbuf.clear();
            try {
                this.protocol.dataReceived(data);
            } catch (Throwable t) {
                t.printStackTrace();
                this.loseConnection(t);
            }
        }

        public void write(byte[] data) {
            this.outbufs.add(data);
            this.startWriting();
        }

        public void doWrite() throws Throwable {
            /* XXX TODO: this cannot possibly be correct, but every example
             * and every tutorial does this!  Insane.
             */
            if (0 == this.outbufs.size()) {
                if (this.disconnecting) {
                    this.channel.close();
		    this.protocol.connectionLost(new Throwable("Disconnected"));
		}
                // else wtf?
            } else {
                this.channel.write(ByteBuffer.wrap(this.outbufs.remove(0)));
                if (0 == this.outbufs.size()) {
                    this.stopWriting();
                }
            }
        }

	public boolean isConnecting() {
	    return channel.isConnectionPending();
	}

	abstract public void loseConnection(Throwable reason);
    }

   /** Implements the bulk of the tcp server support */
    private class TCPPort extends TCPConnection implements IListeningPort {
        private ServerFactory       serverFactory;
        private ServerSocketChannel schannel;
        private ServerSocket        ssocket;
        private InetSocketAddress   addr;

        TCPPort(int port, ServerFactory sf) throws Throwable {
	    super(port);
            this.serverFactory = sf;
            this.addr = new InetSocketAddress(port);

            this.schannel = ServerSocketChannel.open();
            this.schannel.configureBlocking(false);
            this.ssocket = schannel.socket();
            this.ssocket.bind(this.addr);
            this.protocol = sf.buildProtocol(this.addr);

	    this.sk = schannel.register(selector, SelectionKey.OP_ACCEPT, this);
            interestOpsChanged();

            this.startListening();
        }

	public InetSocketAddress getHost() { return this.addr; }

        public void startListening() throws Throwable {
            this.sk.interestOps(sk.interestOps() | SelectionKey.OP_ACCEPT);
            interestOpsChanged();
        }

        public void stopListening() {
            this.sk.interestOps(sk.interestOps() & ~SelectionKey.OP_ACCEPT);
            this.sk.cancel();
            interestOpsChanged();
        }

	public void doAccept() throws Throwable {
            SocketChannel newchannel = schannel.accept();
            if (null == newchannel) {
		throw new Throwable("Unable to accept connection!");
	    } else {
		channel = newchannel;
		channel.configureBlocking(false);
		socket = newchannel.socket();
		this.protocol.makeConnection(this);

		this.sk = channel.register(selector,SelectionKey.OP_READ,this);
		interestOpsChanged();

		super.startReading();
	    }
        }

	public void connectionLost(Throwable reason) {
	    this.serverFactory.connectionLost(this, reason);
        }

        public void loseConnection(Throwable reason) {
            this.disconnecting = true;
	    this.protocol.connectionLost(reason);
	    //this.serverFactory.loseConnection(this, reason);
        }
     }

    /** Implements the bulk of the tcp client support */
    private class TCPConnect extends TCPConnection implements IConnector {
	private ClientFactory     clientFactory;
        private InetSocketAddress addr;

	TCPConnect(String host, int port, ClientFactory cf) throws Throwable {
	    super(port);
	    this.clientFactory = cf;
	    this.addr = new InetSocketAddress(host, port);

	    this.channel = SocketChannel.open();
	    this.channel.configureBlocking(false);
	    this.socket = channel.socket();
            this.protocol = cf.buildProtocol(this.addr);

	    this.sk = channel.register(selector, SelectionKey.OP_CONNECT, this);
            interestOpsChanged();

	    this.connect();
	}

	public InetSocketAddress getDestination() { return this.addr; }

	public void connect() throws Throwable {
	    this.clientFactory.startedConnecting(this);
	    this.channel.connect(this.addr);
            this.sk.interestOps(sk.interestOps() | SelectionKey.OP_CONNECT);
	    interestOpsChanged();
	}

	public void disconnect() {
	    this.sk.interestOps(sk.interestOps() & ~SelectionKey.OP_CONNECT);
	    this.sk.cancel();
	    interestOpsChanged();

	    try {
		this.channel.close();
	    } catch (IOException e) {
		loseConnection(e);
	    }
	}

	public void stopConnecting() {
	    this.sk.interestOps(sk.interestOps() & ~SelectionKey.OP_CONNECT);
	    this.sk.cancel();
	    interestOpsChanged();

	    try {
		this.channel.close();
	    } catch (IOException e) {
		loseConnection(e);
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

        public void loseConnection(Throwable reason) {
            this.disconnecting = true;
	    this.protocol.connectionLost(reason);
	    this.clientFactory.clientConnectionLost(this, reason);
        }
    }

    /**
     * Run all runnables scheduled to run before right now, and return the
     * timeout.  Negative timeout means "no timeout".
     */
    private long runUntilCurrent(long now) {
        while (0 != pendingCalls.size()) {
            try {
                long then = pendingCalls.firstKey();
                if (then < now) {
                    Runnable r = pendingCalls.remove((Object) new Long(then));
                    r.run();
                } else {
                    return then - now;
                }
            } catch (NoSuchElementException nsee) {
                nsee.printStackTrace();
                throw new Error("Impossible; pendingCalls.size was not zero");
            }
        }
        return -1;
    }

    private void iterate() throws Throwable {
        Iterator<SelectionKey> selectedKeys =
            this.selector.selectedKeys().iterator();
        while (selectedKeys.hasNext()) {
            SelectionKey sk = selectedKeys.next();
            Selectable selectable = ((Selectable) sk.attachment());
            if (sk.isValid() && sk.isWritable() && !selectable.isConnecting()) {
		//selectedKeys.remove();
                selectable.doWrite();
            }
            if (sk.isValid() && sk.isReadable() && !selectable.isConnecting()) {
		//selectedKeys.remove();
                selectable.doRead();
            }
            if (sk.isValid() && sk.isAcceptable()) {
		//selectedKeys.remove();
                selectable.doAccept();
            }
            if (sk.isValid() && sk.isConnectable()) {
		//selectedKeys.remove();
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

    /**
     * Override this method in subclasses to run "iterate" in a different
     * context, i.e. in a thread.
     */
    public void doIteration() throws Throwable {
        iterate();
    }

    public void wakeup() { selector.wakeup(); }

    public void callLater(double secondsLater, Runnable runme) {
        long millisLater = (long) (secondsLater * 1000.0);
        synchronized(pendingCalls) {
            pendingCalls.put(System.currentTimeMillis() + millisLater, runme);
            // This isn't actually an interestOps
            interestOpsChanged();
        }
    }

    public void run() throws Throwable {
        running = true;
        while (running) {
            int selected;
            long timeout = processTimedEvents();

            if (timeout >= 0) {
                this.selector.select(timeout);
            } else {
                this.selector.select();
            }
            this.doIteration();
        }
	connection.loseConnection(new Throwable("Shutdown"));
    }

    public void stop() {
        this.running = false;
    }

    public IListeningPort listenTCP(int portno,
				    ServerFactory factory) throws Throwable {
        return new TCPPort(portno, factory);
    }

    public IConnector connectTCP(String addr, int portno,
				 ClientFactory factory) throws Throwable {
	return new TCPConnect(addr, portno, factory);
    }

    public static void msg (String m) {
        System.out.println(m);
    }

    /* A very basic example of how to use the reactor */

    static class ShowMessage implements Runnable {
        String x;
        ShowMessage(String s) {
            this.x = s;
        }
        public void run () {
            msg(System.currentTimeMillis() + " " + this.x);
        }
    }


    public static void main (String[] args) throws Throwable {
        // The most basic server possible.
        Reactor r = Reactor.get();

        r.callLater(1, new ShowMessage("one!"));
        r.callLater(3, new ShowMessage("three!"));
        r.callLater(2, new ShowMessage("two!"));
        r.callLater(4, new ShowMessage("four!"));

        r.listenTCP(1234, new ServerFactory() {
                public IProtocol buildProtocol(Object addr) {
                    return new Protocol() {
                        public void dataReceived(byte[] data) {
                            this.transport().write(data);
			    ShowMessage sm = new ShowMessage("delayed data: " +
							     new String(data));
                            Reactor.get().callLater(1, sm);
                        }
                    };
                }
		@Override public void connectionLost(IListeningPort port,
					   Throwable reason) {
		    System.out.println("connection lost:" + reason);
		}
		@Override public void startedListening(IListeningPort port) {
		    System.out.println("listening");
		}
            });
        r.run();
    }
}

