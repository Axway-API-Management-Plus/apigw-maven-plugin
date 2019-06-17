package com.axway.maven.apigw;

import java.util.Objects;

/**
 * Package types supported by the plugin.
 * 
 * @author look
 */
public enum PackageType {

	/**
	 * Project contains an API Gateway / Manager archive.
	 */
	SERVER("axway-server-archive", ".axsar"),
	
	/**
	 * Projects contains a common policy archive.
	 */
	POLICY("axway-policy-archive", ".axpar"),
	
	/**
	 * Projects contains an API Gateway / Manager deployment archive.
	 */
	DEPLOYMENT("axway-deployment-archive", ".axdar");

	private final String type;
	private final String extension;

	public static PackageType fromType(String type) {
		for (PackageType p : PackageType.values()) {
			if (p.getType().equals(type))
				return p;
		}
		throw new IllegalArgumentException("Unsuppored package type: " + type);
	}

	private PackageType(String type, String extension) {
		this.type = Objects.requireNonNull(type);
		this.extension = Objects.requireNonNull(extension);
	}

	public String getType() {
		return this.type;
	}

	public String getExtension() {
		return this.extension;
	}

	@Override
	public String toString() {
		return type;
	}
}
