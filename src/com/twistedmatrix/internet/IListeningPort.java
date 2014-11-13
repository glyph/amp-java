package com.twistedmatrix.internet;

import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;

public interface IListeningPort {
    public void stopListening();
    public void startListening() throws ClosedChannelException;
    public InetSocketAddress getHost();
}
