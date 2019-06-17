package com.axway.maven.apigw.utils;

public class JythonExecutorException extends Exception {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1275254343677128842L;

	public JythonExecutorException(String msg) {
		super(msg);
	}
	
	public JythonExecutorException(String msg, Throwable cause) {
		super(msg, cause);
	}
}
