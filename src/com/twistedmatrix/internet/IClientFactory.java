package com.twistedmatrix.internet;

public interface IClientFactory extends IFactory {
    public void startedConnecting(IConnector connector);
    public void clientConnectionFailed(IConnector connector, Throwable reason);
    public void clientConnectionLost(IConnector connector, Throwable reason);
}
