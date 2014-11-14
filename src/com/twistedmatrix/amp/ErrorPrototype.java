package com.twistedmatrix.amp;

public class ErrorPrototype {
    private String _code;
    private String _description;

    public ErrorPrototype (String code, String description) {
	_code = code;
	_description = description;
    }

    public String getCode() { return _code; }
    public String getDescription() { return _description; }
    public Throwable getThrowable() {
	return new Throwable(_code + " " + _description);
    }
}
