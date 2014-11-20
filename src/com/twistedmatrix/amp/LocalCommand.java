package com.twistedmatrix.amp;

/** Class to define a local commands and it's parameters. */
public class LocalCommand {
    private String   _name;
    private String[] _params;
    
    public LocalCommand(String name, String[] params) { 
	_name = name;
	_params = params;
    }
    
    public String getName() { return _name; }
    public String[] getParams() { return _params; }
}
