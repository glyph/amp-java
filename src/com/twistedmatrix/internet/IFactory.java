package com.twistedmatrix.internet;

public interface IFactory {
    public IProtocol buildProtocol(Object addr);
    //public void logPrefix();
    //public void doStart();
    //public void doStop();
    //public void startFactory();
    //public void stopFactory();
}
