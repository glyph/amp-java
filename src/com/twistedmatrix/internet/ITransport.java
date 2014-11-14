
package com.twistedmatrix.internet;

public interface ITransport {
    void write(byte[] data);
    // no reason for writeSequence; unclear if we'd get any boost.
    void loseConnection(Throwable reason);

    // probably need this soon

    // void getPeer();
    // void getHost();
}
