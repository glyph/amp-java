package com.twistedmatrix.amp;

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
 * AMP Integer  = Java Integer
 * AMP String   = Java byte[]
 * AMP Unicode  = Java String
 * AMP Boolean  = Java Boolean
 * AMP Float    = Java Double
 * AMP Decimal  = Java BigDecimal NOT IMPLEMENTED YET
 * AMP DateTime = Java Date       NOT IMPLEMENTED YET
 * AMP ListOf   = Java List       NOT IMPLEMENTED YET
 * AMP AmpList  = Java Map        NOT IMPLEMENTED YET
 */

public class AMP extends AMPParser {
    private int _counter;
    private Map<String, LocalCommand> _locals;
    private Map<String, RemoteCommand> _remotes;
    private enum Forbidden { _answer, _command, _ask, _error,
			     _error_code, _error_description };

    public AMP() {
	_locals = new HashMap<String, LocalCommand>();
	_remotes = new HashMap<String, RemoteCommand>();
    }

    /** Class to define methods and their paramters. The method specified
     * must be public.
     */
    private class LocalCommand {
	private String       _method;
	private String[]     _params;

	public LocalCommand(String method, String[] params) {
	    _method = method;
	    _params = params;
	}

	public String   getMethod() { return _method; }
	public String[] getParams() { return _params; }
    }

    /**
     * Return a string unique for this connection, to uniquely identify
     * subsequent requests.
     */
    private String nextTag() {
        _counter++;
        return Integer.toHexString(_counter);
    }

    /** Class for tracking the commands sent and their responses. */
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
    public void localCommand(String name, String method, String[] params) {
	_locals.put(name, new LocalCommand(method, params));

	for (Forbidden f: Forbidden.values())
	    if (name.equals(f.name()))
		throw new Error ("LocalCommand name '" + name +
				 "' is not allowed!");
	for (Forbidden f: Forbidden.values())
	    if (method.equals(f.name()))
		throw new Error ("Method name '" + method +
				 "' is not allowed!");
	for (String param: params) {
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
	    Object[] parameters = null;
	    for (String cmd: _locals.keySet())
		if (cmd.equals(cmdprop))
		    for (Method p: this.getClass().getMethods())
			if (p.getName().equals(_locals.get(cmd).getMethod())) {
			    // The remote command name matches a local one.
			    Class[] ptypes = p.getParameterTypes();
			    parameters = new Object[ptypes.length];
			    String[] lparams = _locals.get(cmd).getParams();
			    if (ptypes.length == lparams.length) {
				// The parameters match too, we have a winner!
				m = p;
				for (int i = 0; i < ptypes.length; i++) {
				    Class thisType = ptypes[i];
				    parameters[i] = box.getAndDecode(lparams[i],
								     thisType);
				}
			    }
			}

	    if (null == m) {
		throw new Error ("No method defined to handle command '" +
				 cmdprop + "'!");
	    } else {
		// We have method and an array of parameters, time to call it.
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
