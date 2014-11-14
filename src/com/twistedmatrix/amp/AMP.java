package com.twistedmatrix.amp;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Retention;

import com.twistedmatrix.internet.*;

/**
 * The actual asynchronous messaging protocol, with command dispatch.
 */

public class AMP extends AMPParser {
    private int counter;
    private Map<String, Command> commands;
    private Map<String, CommandResult> results;
    private enum Forbidden { _answer, _command, _ask, _error,
			     _error_code, _error_description };

    public AMP() {
	results = new HashMap<String, CommandResult>();
	commands = new HashMap<String, Command>();
    }

    /** Internal record for tracking the results of commands sent. */
    private static class CommandResult {
        Object protoResult;
        Deferred deferred;
        CommandResult(Object protoResult, Deferred deferred) {
            this.protoResult = protoResult;
            this.deferred = deferred;
        }
    }

    /** Class to define methods and their arguments. The method specified
     * must be public.
     */
    private class Command {
	private String       _method;
	private List<String> _arguments;

	public Command(String method, List<String> arguments) {
	    _method = method;
	    _arguments = arguments;
	}

	public String getMethod() { return _method; }
	public List<String> getArguments() { return _arguments; }
    }

    /** Associate a command with a method and its arguments. */
    public void addCommand(String name, String method, List<String> arguments) {
	commands.put(name, new Command(method, arguments));

	for (Forbidden f: Forbidden.values())
	    if (name.equals(f.name()))
		throw new Error ("Command name '" + name +
				 "' is not allowed!");
	for (Forbidden f: Forbidden.values())
	    if (method.equals(f.name()))
		throw new Error ("Method name '" + method +
				 "' is not allowed!");
	for (String argument: arguments) {
	    for (Forbidden f: Forbidden.values())
		if (argument.equals(f.name()))
		    throw new Error ("Argument name '" + argument +
				     "' is not allowed!");
	}
    }

    /**
     * Return a string unique for this connection, to uniquely identify
     * subsequent requests.
     */
    private String nextTag() {
        counter++;
        return Integer.toHexString(counter);
    }

    /**
     * Send a remote command.
     *
     *  name: name of the remote command,
     *  args: class containing values to send,
     *  result: class defining response variables, any data is ignored
     *
     */
    public Deferred callRemote(String name, Object args, Object result) {
        AMPBox box = new AMPBox();
        String asktag = this.nextTag();
        box.putAndEncode("_command", name);
        box.putAndEncode("_ask", asktag);
        box.extractFrom(args);
        this.sendBox(box);
        Deferred d = new Deferred();
        results.put(asktag, new CommandResult(result, d));
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
            /* An error?  We definitely don't know what to do with it.  */
            return;
        }

        if ("_answer".equals(msgtype)) {
            CommandResult cr = this.results.get(cmdprop);
            box.fillOut(cr.protoResult);
            cr.deferred.callback(cr.protoResult);
        } else if ("_error".equals(msgtype)) {
            CommandResult cr = this.results.get(cmdprop);
            ErrorPrototype error = box.fillError();
            cr.deferred.errback(new Deferred.Failure(error.getThrowable()));
        } else if ("_command".equals(msgtype)) {
	    Method m = null;
	    for (String cmd: commands.keySet())
		if (cmd.equals(cmdprop))
		    for (Method p: this.getClass().getMethods())
			if (p.getName().equals(commands.get(cmd).getMethod()))
			    m = p;

	    if (null == m) {
		throw new Error ("No method defined to handle command '" +
				 cmdprop + "'!");
	    } else {
		// At this point: it's a command, the command name
		// from the network matches, it's time to marshal the
		// arguments and then call it.
		int i = 0;
		Class[] ptypes = m.getParameterTypes();
		List<String> args = commands.get(cmdprop).getArguments();
		if (ptypes.length != args.size()) {
		    throw new Error (m + " signature did not match " +
				     "command arguments!");
		}
		Object[] parameters = new Object[ptypes.length];

		for (String argname : args) {
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
