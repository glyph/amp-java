package com.twistedmatrix.internet;

public interface IServerFactory extends IFactory {
    public void startedListening(IListeningPort connector);
    //public void loseConnection(IListeningPort connector, Throwable reason);
    public void connectionLost(IListeningPort connector, Throwable reason);
}
