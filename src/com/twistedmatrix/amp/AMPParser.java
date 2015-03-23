package com.twistedmatrix.amp;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/** This class buffers incoming data until a complete message has been received. */
public abstract class AMPParser extends Int16StringReceiver {

    private enum State { KEY, VALUE, INIT };

    private State state = State.INIT;

    private byte[] workingKey;

    private AMPBox workingBox;

    private static class ParseGatherer extends AMPParser {
        ArrayList<AMPBox> alhm;
        public ParseGatherer() {
            alhm = new ArrayList<AMPBox>();
        }
        public void ampBoxReceived(AMPBox hm) {
            alhm.add(hm);
        }
    }

    /** Deliver a complete message. */
    public abstract void ampBoxReceived(AMPBox hm);
    
    /** Parse arbitrary data into a set of messages. Used for testing.*/
    public static List<AMPBox> parseData(byte[] data) {
        ParseGatherer pg = new ParseGatherer();
        pg.dataReceived(data);
        if (pg.recvd.length != 0) {
            System.out.println("UNPARSED: " + pg.getCurrent());
            for (byte b: pg.recvd) {
                System.out.print(Int16StringReceiver.toInt(b));
                System.out.print(", ");
            }
            System.out.println();
        }
        return pg.alhm;
    }
    
    /** Add a chunk to the message. */
    public void stringReceived(byte[] hunk) {
        switch(this.state) {
        case INIT:
            this.workingBox = new AMPBox();
        case KEY:
            if (hunk.length == 0) {
                if (this.workingBox.size() == 0) {
                    System.out.println("empty box, you lose");
                }
                this.ampBoxReceived(this.workingBox);
                this.workingBox = null;
                this.state = State.INIT;
            } else {
                this.workingKey = hunk;
                this.state = State.VALUE;
            }
            break;
        case VALUE:
            this.workingBox.put(workingKey, hunk);
            this.state = State.KEY;
            this.workingKey = null;
            break;
        }
    }
    
    /** Get the current chunk being processed. */
    public String getCurrent() { return new String(workingKey); }
}
