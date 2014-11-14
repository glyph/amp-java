package com.twistedmatrix.internet;

import java.net.InetSocketAddress;

public interface IConnector {
    public void stopConnecting();
    public void disconnect();
    public void connect() throws Throwable;
    public void connectionFailed(Throwable reason);
    public void loseConnection(Throwable reason);
    public InetSocketAddress getDestination();
}

