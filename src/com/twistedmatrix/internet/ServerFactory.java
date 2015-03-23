package com.twistedmatrix.internet;

/** A Protocol factory for servers. */
public abstract class ServerFactory implements IFactory {

    /** If using SSL, optionally define the cipher suites. */
    public String[] getEnabledCipherSuites() { return new String[] {}; }

    /** Called when the server starts listening. */
    public abstract void startedListening(IListeningPort connector);

    /** Called when the connection to a client is lost. */
    public abstract void connectionLost(IListeningPort connector,
					Throwable reason);
}
