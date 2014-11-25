package com.twistedmatrix.internet;

/** A Protocol factory for servers. */
public abstract interface ServerFactory extends IFactory {
    /** Called when the server starts listening. */
    public abstract void startedListening(IListeningPort connector);

    /** Called when the connection to a client is lost. */
    public abstract void connectionLost(IListeningPort connector, 
					Throwable reason);
}
