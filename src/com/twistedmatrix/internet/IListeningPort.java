package com.twistedmatrix.internet;

import java.net.InetSocketAddress;

public interface IListeningPort {
    public void stopListening();
    public void startListening() throws Throwable;
    public InetSocketAddress getHost();
    public void connectionLost(Throwable reason);
}
