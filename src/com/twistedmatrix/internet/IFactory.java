package com.twistedmatrix.internet;

/** This is a factory which produces protocols. */
public interface IFactory {
    /** Create an instance of a subclass of IProtocol. */
    public IProtocol buildProtocol(Object addr);
    //public void logPrefix();
    //public void doStart();
    //public void doStop();
    //public void startFactory();
    //public void stopFactory();
}
