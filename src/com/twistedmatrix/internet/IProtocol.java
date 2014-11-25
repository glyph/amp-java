package com.twistedmatrix.internet;

/** Defines a connection. */
public interface IProtocol {
    /** Called when the connection is shut down. */
    public void connectionLost(Throwable reason);

    /** Called when a connection is made. */
    public void connectionMade();

    /** Called whenever data is received. */
    public void dataReceived(byte[] data);

    /** Make a connection to a transport and a server. */
    public void makeConnection(ITransport transport);
}
