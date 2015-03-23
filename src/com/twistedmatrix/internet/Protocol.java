package com.twistedmatrix.internet;

/** Defines a connection. */
public abstract class Protocol implements IProtocol {
    private ITransport transport;

    /** Called whenever data is received. */
    public abstract void dataReceived(byte[] data);

    /** Called when the connection is shut down. */
    public void connectionLost(Throwable reason) { }

    /** Called when a connection is made. */
    public void connectionMade() { }

    /** Make a connection to a transport and a server. */
    public void makeConnection(ITransport transport) {
        this.transport = transport;
        this.connectionMade();
    }

    /** Returns this protocol's transport. */
    public ITransport transport() {
        return this.transport;
    }
}
