package com.twistedmatrix.internet;

/**  I am a transport for bytes.
 * I represent the physical connection and synchronicity of the framework
 * which is talking to the network. I make no representations about 
 * whether calls to me will happen immediately or require returning to a 
 * control loop, or whether they will happen in the same or another thread. */
public interface ITransport {
    /** Cleans up the socket. */
    void connectionLost(Throwable reason);

    /** Close my connection, after writing all pending data. */
    void loseConnection(Throwable reason);

    /** Write some data to the physical connection, in sequence, in a non-blocking fashion. */
    void write(byte[] data);

    // no reason for writeSequence; unclear if we'd get any boost.
    // void getPeer();
    // void getHost();
}
