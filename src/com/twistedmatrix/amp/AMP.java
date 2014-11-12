package com.twistedmatrix.amp;

import java.util.HashMap;
import java.util.ArrayList;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Retention;

import com.twistedmatrix.internet.Deferred;
import com.twistedmatrix.internet.Reactor;
import com.twistedmatrix.internet.Protocol;
import com.twistedmatrix.internet.IProtocol;
import com.twistedmatrix.internet.ITransport;

/**
 * The actual asynchronous messaging protocol, with command dispatch and everything.
 */

public class AMP extends AMPParser {
    /**
     * Internal record for tracking the results of commands that were
     * previously issued.
     */
    static class CommandResult {
        Object protoResult;
        Deferred deferred;
        CommandResult(Object protoResult, Deferred deferred) {
            this.protoResult = protoResult;
            this.deferred = deferred;
        }
    }

    HashMap<String, CommandResult> pendingCommands = new HashMap<String, CommandResult>();

    /**
     * Annotation interface for exposing commands.
     */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Command {
        /**
         * A comma-separated list of argument names.
         */
        String name();
        String arguments();
    }

    int counter;

    /**
     * Return a string unique for this connection, to uniquely identify
     * subsequent requests.
     */
    String nextTag() {
        counter++;
        return Integer.toHexString(counter);
    }

    /**
     * Send a remote command.
     */
    public Deferred callRemote(String name, Object args, Object result) {
        AMPBox box = new AMPBox();
        String asktag = this.nextTag();
        box.putAndEncode("_command", name);
        box.putAndEncode("_ask", asktag);
        box.extractFrom(args);
        this.sendBox(box);
        Deferred d = new Deferred();
        pendingCommands.put(asktag, new CommandResult(result, d));
        return d;
    }

    /**
     * Serialize an AMPBox to the current transport for this AMP connection.
     */
    public void sendBox(AMPBox ab) {
        ITransport t = this.transport();
        if (null == t) {
            return;
        }
        t.write(ab.encode());
    }

    /**
     * A single message was received from the network.  Determine its type and
     * dispatch it to the appropriate handler.
     */
    public void ampBoxReceived(AMPBox box) {
        String msgtype = null;
        String cmdprop = null;

        for(String k : new String[] {"_answer", "_error", "_command"}) {
            cmdprop = (String) box.getAndDecode(k, String.class);
            if (cmdprop != null) {
                msgtype = k;
                break;
            }
        }

        if (null == msgtype) {
            /*
              An error or something?  We definitely don't know what to do with it.
            */
            return;
        }

        if ("_answer".equals(msgtype)) {
            CommandResult cr = this.pendingCommands.get(cmdprop);
            box.fillOut(cr.protoResult);
            cr.deferred.callback(cr.protoResult);
        } else if ("_error".equals(msgtype)) {
            CommandResult cr = this.pendingCommands.get(cmdprop);
            AMPBox.ErrorPrototype error = box.fillError();
            cr.deferred.errback(new Deferred.Failure(error.getException()));
        } else if ("_command".equals(msgtype)) {
            for (Method m : this.getClass().getMethods()) {
                AMP.Command c = (AMP.Command) m.getAnnotation(AMP.Command.class);
                if (null != c) {
                    if (cmdprop.equals(c.name())) {
                        // At this point: it's a command, the command name
                        // from the network matches, it's time to marshal the
                        // arguments and then call it.
                        int i = 0;
                        Class[] ptypes = m.getParameterTypes();
                        String[] argnames = c.arguments().split(" ");
                        if (1 == argnames.length && "".equals(argnames[0])) {
                            argnames = new String[0];
                        }
                        if (ptypes.length != argnames.length) {
                            throw new Error (m + " signature did not match '" +
                                             c.arguments() + "'");
                        }
                        Object[] parameters = new Object[ptypes.length];

                        for (String argname : argnames) {
                            Class thisType = ptypes[i];
                            parameters[i] = box.getAndDecode(argname, thisType);
                            i++;
                        }

                        // Okay, I've got an array of parameters.  Time to
                        // call a method.
                        try {
                            Object result = m.invoke(this, parameters);
                            if (result == null) {
                                AMPBox emptyResponse = new AMPBox();
                                emptyResponse.put("_answer", box.get("_ask"));
                                this.sendBox(emptyResponse);
                            } else if (result instanceof Deferred) {
                                Deferred d = (Deferred) result;
                                class SuccessHandler implements Deferred.Callback {
                                    byte[] tag;
                                    public SuccessHandler(byte[] tag) {
                                        this.tag = tag;
                                    }
                                    public Object callback(Object retval) {
                                        AMPBox response = new AMPBox();
                                        response.extractFrom(retval);
                                        response.put("_answer", this.tag);
                                        AMP.this.sendBox(response);
                                        return null;
                                    }
                                }
                                // XXX TODO: addErrback
                                d.addCallback(new SuccessHandler(box.get("_ask")));
                            } else {
                                AMPBox resultBox = new AMPBox();
                                resultBox.put("_answer", box.get("_ask"));
                                resultBox.extractFrom(result);
                                this.sendBox(resultBox);
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    @AMP.Command(
        name="ping",
        arguments="")
    public void ping() {
    }

    static class summer extends AMP {

        class sumResult {
            public int sumValue = 0;
        }

        @AMP.Command(name="sum", arguments="a b")
        public sumResult sum (int a, int b) {
            sumResult sr = new sumResult();
            sr.sumValue = a + b;
            return sr;
        }
    }

    /**
     * This simple main-point starts a server that can listen on port 1234 for
     * AMP 'ping' requests.
     */
    public static void main(String[] args) throws Throwable {
        Reactor r = Reactor.get();
        r.listenTCP(1234, new IProtocol.IFactory() {
                public IProtocol buildProtocol(Object addr) {
                    System.out.println("User connected!!");
                    return new summer();
                }
            });
        r.run();
    }
}
