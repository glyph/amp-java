package com.twistedmatrix.internet;

abstract public interface ServerFactory extends IFactory {
    //public void loseConnection(IListeningPort connector, Throwable reason);
    abstract public void startedListening(IListeningPort connector);
    abstract public void connectionLost(IListeningPort connector, 
					Throwable reason);
}
