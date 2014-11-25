package com.twistedmatrix.internet;

import java.net.InetSocketAddress;

/** Base class for an TCP client. */
public interface IConnector {
    /** Start connecting to remote server. */
    public void connect() throws Throwable;

    /** Failed to connect to remote server. */
    public void connectionFailed(Throwable reason);

    /** Connection to remote server was lost. */
    public void connectionLost(Throwable reason);

    /** Disconnect whatever our state is. */
    public void loseConnection(Throwable reason);

    /** Return destination this will try to connect to. */
    public InetSocketAddress getDestination();

    /** Stop attempting to connect to remote server. */
    public void stopConnecting();

}

