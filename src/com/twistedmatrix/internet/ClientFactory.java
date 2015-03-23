package com.twistedmatrix.internet;

/** A Protocol factory for clients. */
public abstract class ClientFactory implements IFactory {

    /** If using SSL, optionally define the cipher suites. */
    public String[] getEnabledCipherSuites() { return new String[] {}; }

    /** Called when a connection has been started. */
    public void startedConnecting(IConnector connector) {};

    /** Called when a connection has failed to connect. */
    public void clientConnectionLost(IConnector connector,Throwable reason) {};

    /** Called when an established connection is lost. */
    public abstract void clientConnectionFailed(IConnector connector,
						Throwable reason);
}
