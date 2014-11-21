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
 *
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
 */

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

    /** Class for tracking the remote commands and their responses. */
    public class RemoteCommand<R> {
	private String   _asktag   = "";
        private R        _response = null;
        private Deferred _deferred = null;
	private AMPBox   _box      = null;

        public RemoteCommand(String name, Object params, R response) {
	    _box = new AMPBox();
	    _asktag = AMP.this.nextTag();
            _response = response;

	    _box.putAndEncode("_command", name);
	    _box.putAndEncode("_ask", _asktag);
	    _box.extractFrom(params);
        }

	public R getResponse() { return _response; }
	public Deferred getDeferred() { return _deferred; }

	public Deferred callRemote() {
	    AMP.this.sendBox(_box);
	    AMP.this._remotes.put(_asktag, RemoteCommand.this);
            _deferred = new Deferred();
	    return _deferred;
	}
    }

    /** Associate an incoming command with a local method and its arguments. */
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
            RemoteCommand rc = this._remotes.get(cmdprop);
            box.fillOut(rc.getResponse());
            rc.getDeferred().callback(rc.getResponse());
        } else if ("_error".equals(msgtype)) {
            RemoteCommand rc = this._remotes.get(cmdprop);
            ErrorPrototype error = box.fillError();
            rc.getDeferred().errback(new Failure(error.getThrowable()));
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
		    t.printStackTrace();
		}
            }
        }
    }
}
