amp-java
========

Java implementation of AMP (Asynchronous Messaging Protocol) that includes some
twisted python features like reactors and deferreds. More on AMP can be found
at "http://amp-protocol.net/". A good place to start to get an overview of the
project are the example clients and servers in the examples directory.

Supported Data Types:
 * AMP Integer  = java.lang.Integer or int
 * AMP String   = java.nio.ByteBuffer or byte[]
 * AMP Unicode  = java.lang.String
 * AMP Boolean  = java.lang.Boolean or boolean
 * AMP Float    = java.lang.Double or double
 * AMP Decimal  = java.math.BigDecimal
 * AMP DateTime = java.util.Calendar
 * AMP ListOf   = java.util.ArrayList
 * AMP AmpList  = java.util.ArrayList(extends com.twistedmatrix.amp.AmpItem)

NOTES: 
 * Java BigDecimal does not support special values like Infinity or NaN.
 * Java Calendar only supports up to millisecond accuracy.
 * Classes that extend AmpItem must not be nested in other classes.
 * Classes sent or recieved must only contain data types listed above.

Ant Targets:
 * build          Compiles bytecode with debug
 * buildexamples  Compiles bytecode for examples
 * buildprod      Compiles bytecode without debug
 * clean          Cleans this project
 * jar            Creates jar file
 * javadoc        Generate documentation
 * runexclient    Runs example client
 * runexserver    Runs example server
 * test           Run junit tests
