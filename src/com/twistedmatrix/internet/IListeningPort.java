package com.twistedmatrix.internet;

import java.net.InetSocketAddress;

/** A TCP server, listening for connections.
 * When a connection is accepted, this will call a factory's 
 * buildProtocol with the incoming address as an argument
 */
public interface IListeningPort {
    /** Cleans up the socket. */
    public void connectionLost(Throwable reason);

    /** Returns an InetSocketAddress. */
    public InetSocketAddress getHost();

    /** Stop accepting connections on this port. */
    public void loseConnection(Throwable reason);

    /** Create and bind my socket, and begin listening on it. */
    public void startListening() throws Throwable;
}
