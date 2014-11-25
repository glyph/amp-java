package com.twistedmatrix.internet;

/** A Protocol factory for clients. */
public abstract class ClientFactory implements IFactory {

    /** Called when a connection has been started. */
    public void startedConnecting(IConnector connector) {};

    /** Called when a connection has failed to connect. */
    public void clientConnectionLost(IConnector connector,Throwable reason) {};

    /** Called when an established connection is lost. */
    public abstract void clientConnectionFailed(IConnector connector, 
						Throwable reason);
}
