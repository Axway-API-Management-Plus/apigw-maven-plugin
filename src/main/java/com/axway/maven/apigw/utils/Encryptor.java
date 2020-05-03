package com.axway.maven.apigw.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;

import org.apache.maven.plugin.MojoExecutionException;

import com.axway.maven.apigw.AbstractGatewayMojo;

public class Encryptor {

	private final AbstractGatewayMojo mojo;
	private final File secretsFile;
	private boolean verboseCfgTools = false;

	public Encryptor(AbstractGatewayMojo mojo, File secretsFile) throws MojoExecutionException {
		this.mojo = Objects.requireNonNull(mojo);
		this.secretsFile = Objects.requireNonNull(secretsFile);
	}

	public void enableVerboseMode(boolean enabled) {
		this.verboseCfgTools = enabled;
	}

	public int execute(File key) throws MojoExecutionException {

		try {
			JythonExecutor jython = new JythonExecutor(mojo.getHomeAxwayGateway(), mojo.getLog(),
					new File(mojo.getTargetDir(), "temp-scripts"));

			ArrayList<String> args = new ArrayList<>();
			if (this.verboseCfgTools) {
				args.add("--verbose");
			}
			if (this.secretsFile != null) {
				args.add("--secrets-file");
				args.add(this.secretsFile.getPath());
				args.add("--secrets-key");
				args.add(key.getPath());
			}
			return jython.execute("secrets.py", args.toArray(new String[0]));
		} catch (JythonExecutorException e) {
			throw new MojoExecutionException("Error on encrypting secrest file", e);
		}
	}
}
