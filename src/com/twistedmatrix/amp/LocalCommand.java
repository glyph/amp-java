package com.twistedmatrix.amp;

/** Class to define a local command that might be invoked remotely. */
public class LocalCommand {
    private String   _name;
    private String[] _params;
    
    /** Maps a method to a remote command. It is important that the paramater
     * list defined here matches the number and order of the local method.
     * @param name The local method name.
     * @param params An array of the names of the local method.
     */
    public LocalCommand(String name, String[] params) { 
	_name = name;
	_params = params;
    }
    
    /** Get the name of the local method.
     * @return The name of the local method. */
    public String getName() { return _name; }

    /** Get the parameters of the local method.
     * @return The parameters of the local method. */
    public String[] getParams() { return _params; }
}
