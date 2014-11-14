package com.twistedmatrix.internet;

abstract public class Protocol implements IProtocol {
    private ITransport transport;

    public ITransport transport() {
        return this.transport;
    }

    public void makeConnection(ITransport transport) {
        this.transport = transport;
        this.connectionMade();
    }

    public void connectionMade() {
    }

    public void connectionLost(Throwable reason) {
    }

    abstract public void dataReceived(byte[] data);
}
