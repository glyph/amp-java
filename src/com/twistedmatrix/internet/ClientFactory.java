package com.twistedmatrix.internet;

abstract public class ClientFactory implements IFactory {
    public void startedConnecting(IConnector connector) {};
    public void clientConnectionLost(IConnector connector,Throwable reason) {};
    abstract public void clientConnectionFailed(IConnector connector, 
						Throwable reason);
}
