package com.twistedmatrix.internet;

public interface IProtocol {
    public void dataReceived(byte[] data);
    public void connectionLost(Throwable reason);
    public void makeConnection(ITransport transport);
    public void connectionMade();
}
