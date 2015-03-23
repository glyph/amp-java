
package com.twistedmatrix.internet;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TestInternet extends TestCase {
    public static class DeferredTest extends TestCase {
        int callcount = 0;
        public void testImmediateSuccessDeferred() {
	    Deferred d = new Deferred();
	    d.callback(null);
            d.addCallback(new Deferred.Callback() {
                    public Object callback(Object o) {
                        callcount++;
                        // XXX could autoboxing help here?
                        return new Integer(((Integer)o).intValue() + 1);
                    }
                });
            assertEquals(callcount, 1);
        }

        /**
         * Verify that return values in a successful result chain are passed
         * to subsequent callbacks added to the Deferred.
         */
        public void testDeferredResultChain() {
            class Capturer implements Deferred.Callback {
                public int captured;
                public Object callback(Object i) {
                    this.captured = ((Integer)i).intValue();
                    return captured + 1;
                }
            }
            Deferred d = new Deferred();
            Capturer one = new Capturer();
            Capturer two = new Capturer();
            Capturer three = new Capturer();
            d.addCallback(one);
            d.addCallback(two);
            d.callback(1);
            // Make sure it works after the fact too
            d.addCallback(three);
            assertEquals(1, one.captured);
            assertEquals(2, two.captured);
            assertEquals(3, three.captured);
        }
    }

    public static Test suite() {
        TestSuite suite = new TestSuite();
        suite.addTest(new TestSuite(DeferredTest.class));
        return suite;
    }
}
