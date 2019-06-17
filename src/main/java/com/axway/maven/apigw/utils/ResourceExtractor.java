package com.axway.maven.apigw.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.logging.Log;

public class ResourceExtractor {

	private final Log log;

	public ResourceExtractor(Log log) {
		this.log = Objects.requireNonNull(log);
	}

	public void extractResources(File targetDir, String pkg, String[] names) throws IOException {
		for (String name : names) {
			extractResource(targetDir, pkg, name);
		}
	}

	private File extractResource(File targetDir, String pkg, String resourceName) throws IOException {
		File targetResource = new File(targetDir, resourceName);

		targetResource.getParentFile().mkdirs();

		ClassLoader cl = getClass().getClassLoader();
		InputStream resource = cl.getResourceAsStream(pkg + "/" + resourceName);
		if (resource == null) {
			throw new IOException("Resource not found '" + resourceName + "' in package '" + pkg + "'!");
		}

		try {
			this.log.debug("Extract resource '" + resourceName + "' to folder '" + targetDir.getPath());
			FileUtils.copyInputStreamToFile(resource, targetResource);
		} catch (IOException e) {
			throw new IOException(
					"Error on writing resource '" + resourceName + "' to folder '" + targetDir.getPath() + "'!", e);
		}

		return targetResource;
	}
}
