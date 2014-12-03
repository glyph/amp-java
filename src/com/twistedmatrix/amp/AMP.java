package com.twistedmatrix.amp;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

import com.twistedmatrix.internet.*;
import com.twistedmatrix.internet.Deferred.Failure;

/**
 * The actual asynchronous messaging protocol, with command dispatch.
 * Extend this class to actually exchanges messages. Outgoing commands
 * are submitted by building an {@link RemoteCommand} and invoking it's
 * callRemote method. Incoming commands are handled by building a
 * {@link LocalCommand} object and using the localCommand method to
 * register it in the constructor. </BR></BR> Supported data types:<UL>
 *<LI>AMP Integer  = java.lang.Integer or int</LI>
 *<LI>AMP String   = java.nio.ByteBuffer or byte[]</LI>
 *<LI>AMP Unicode  = java.lang.String</LI>
 *<LI>AMP Boolean  = java.lang.Boolean or boolean</LI>
 *<LI>AMP Float    = java.lang.Double or double</LI>
 *<LI>AMP Decimal  = java.math.BigDecimal</LI>
 *<LI>AMP DateTime = java.util.Calendar</LI>
 *<LI>AMP ListOf   = java.util.ArrayList</LI>
 *<LI>AMP AmpList  = java.util.ArrayList&lt;extends {@link AmpItem}&gt;</LI>
 *</UL></BR>Notes:<UL>
 *<LI>Java BigDecimal does not support special values like NaN or Infinity.</LI>
 *<LI>Java Calendar only supports up to millisecond accuracy.</LI>
 *<LI>Classes that extend AmpItem must not be nested in other classes.</LI>
 *<LI>Classes sent or received must only contain data types listed above.</LI>
 *</UL>*/

public class AMP extends AMPParser {
    private int _counter;
    private Map<String, LocalCommand> _locals;
    private Map<String, RemoteCommand> _remotes;
    private enum Forbidden { _answer, _command, _ask, _error, _name,
			     _error_code, _error_description, _params };

    public AMP() {
	_locals = new HashMap<String, LocalCommand>();
	_remotes = new HashMap<String, RemoteCommand>();
    }

    /**
     * Return a string unique for this connection, to uniquely identify
     * subsequent requests.
     */
    private String nextTag() {
	_counter++;
	return Integer.toHexString(_counter);
    }

    /** Class for invoking remote commands and managing their responses.
     * For both the remote parameters and local response objects, the public
     * variables must consist of the types supported by {@link AMP}. */
    public class RemoteCommand<R> {
	private String   _asktag   = "";
	private R	_response = null;
	private Deferred _deferred = null;
	private AMPBox   _box      = null;

	/** The heavy lifting for invoking a remote command happens here.
	 *  @param name The name of the remote command to invoke.
	 *  @param params A populated object whose values will be passed as
	 *	 arguments to the remote command.
	 *  @param response The object that will be populated with the
	 *	 remote command response
	 */
	public RemoteCommand(String name, Object params, R response) {
	    _box = new AMPBox();
	    _asktag = AMP.this.nextTag();
	    _response = response;

	    _box.putAndEncode("_command", name);
	    _box.putAndEncode("_ask", _asktag);
	    _box.extractFrom(params);
	}

	private R getResponse() { return _response; }
	private Deferred getDeferred() { return _deferred; }

	/** Actually invoke the remote command.
	 * @return A {@link Deferred}, to which you use addCallback and
	 * addErrback to add handlers for success and failure respectively.
	 */
	public Deferred callRemote() {
	    AMP.this.sendBox(_box);
	    AMP.this._remotes.put(_asktag, RemoteCommand.this);
	    _deferred = new Deferred();
	    return _deferred;
	}
    }

    /**
     * An AMPBox was received from the network.
     * Determine its type and dispatch it to the appropriate handler.
     * This method is rarely, if ever, invoked directly or overridden.
     *  @param box The AMPBox to received.
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
	    RemoteCommand rc = this._remotes.get(cmdprop);
	    box.fillOut(rc.getResponse());
	    rc.getDeferred().callback(rc.getResponse());
	} else if ("_error".equals(msgtype)) {
	    RemoteCommand rc = this._remotes.get(cmdprop);
	    rc.getDeferred().errback(new Failure(box.fillError()));
	} else if ("_command".equals(msgtype)) {
	    Method m = null;
	    Object[] mparams = null;

	    for (String cmd: _locals.keySet())
		if (cmd.equals(cmdprop))
		    for (Method p: this.getClass().getMethods()) {
			if (p.getName().equals(_locals.get(cmd).getName())) {
			    // The remote command name matches a local one.
			    LocalCommand local = _locals.get(cmd);
			    Class[] ptypes = p.getParameterTypes();
			    mparams = new Object[ptypes.length];
			    String[] pparams = local.getParams();
			    if (ptypes.length == pparams.length) {
				// The parameters match too, we have a winner!
				m = p;
				Field[] mtypes = new Field[ptypes.length];
				Field[] fields = local.getClass().getFields();

				for (int i = 0; i < ptypes.length; i++)
				    for (Field f: fields)
					if (f.getName().equals(pparams[i]))
					    mtypes[i] = f;

				for (int i = 0; i < ptypes.length; i++)
				    mparams[i] = box.getAndDecode(mtypes[i]);
			    }
			}
		    }

	    if (null == m) {
		throw new Error ("No method defined to handle command '" +
				 cmdprop + "'!");
	    } else {
		// We have method and an array of parameters, time to call it.
		try {
		    Object result = m.invoke(this, mparams);
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
		    System.out.println("Name and parameters matched, but got " +
				       "an exception invoking " + m.getName());
		    t.printStackTrace();
		}
	    }
	}
    }

    /** Associate an incoming command with a local method and its arguments.
     * This is the main way to handle messaged received from the network.
     * @param name The name of the command to be invoked remotely.
     * @param command The command to invoke locally.
     */
    public void localCommand(String name, LocalCommand command) {
	_locals.put(name, command);

	for (Forbidden f: Forbidden.values())
	    if (name.equals(f.name()))
		throw new Error ("Command name '" + name +
				 "' is not allowed!");
	for (Forbidden f: Forbidden.values())
	    if (command.getName().equals(f.name()))
		throw new Error ("Method name '" + command.getName() +
				 "' is not allowed!");
	for (String param: command.getParams()) {
	    for (Forbidden f: Forbidden.values())
		if (param.equals(f.name()))
		    throw new Error ("Parameter name '" + param +
				     "' is not allowed!");
	}
    }

    /**
     * Send an AMPBox to the network.
     * Serialize an AMPBox to the current transport for this AMP connection.
     * This method is rarely, if ever, invoked directly or overridden.
     *  @param box The AMPBox to send.
     */
    public void sendBox(AMPBox box) {
	ITransport t = this.transport();
	if (null == t) {
	    return;
	}
	t.write(box.encode());
    }
}
