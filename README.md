amp-java
========

Java implementation of AMP (Asynchronous Messaging Protocol) that includes some
twisted python features like reactors and deferreds. More on AMP can be found
at "http://amp-protocol.net/". A good place to start to get an overview of the
project are the example client and server in the examples directory.

 * AMP Integer  = java.lang.Integer or int
 * AMP String   = java.nio.ByteBuffer or byte[]
 * AMP Unicode  = java.lang.String
 * AMP Boolean  = java.lang.Boolean or boolean
 * AMP Float    = java.lang.Double or double
 * AMP Decimal  = java.math.BigDecimal
 * AMP DateTime = java.util.Calendar
 * AMP ListOf   = java.util.ArrayList
 * AMP AmpList  = java.util.ArrayList<extends com.twistedmatrix.amp.AmpItem>
 *
 * NOTE1: Java BigDecimal does not support special values like Infinity or NaN.
 * NOTE2: Java Calendar only supports up to millisecond accuracy.
 * NOTE3: Classes that extend AmpItem must not be nested in other classes.
 * NOTE4: Classes sent or recieved must only contain data types listed above.
