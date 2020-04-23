package com.axway.maven.apigw.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.maven.plugin.logging.Log;

public abstract class AbstractCommandExecutor {

	private final String name;
	private final Log log;

	public AbstractCommandExecutor(String name, Log log) {
		this.name = Objects.requireNonNull(name, "name is null");
		this.log = Objects.requireNonNull(log, "log is null");
	}

	protected File getCommand() throws IOException { return null; };

	protected String getStringCommand( List<String> parameters ) { return null; }

	protected Log getLog() {
		return this.log;
	}

	protected int execute () throws IOException { return 0; }

	public int execute ( String task, boolean remove, String containerName, String imageName, String imageTag,
						 Map<String, String> ports, Map<String, String> links,
						 Map<String, String> environmentVariables ) throws IOException { return 0; }

	public int execute(Source source, Target target, Map<String, String> polProperties,
					   Map<String, String> envProperties) throws IOException { return 0; }

	protected int execute(List<String> parameters) throws IOException {
		List<String> inputParam = new ArrayList<String>();

		File command = getCommand();
		if (command == null) {
			String stringCommand = getStringCommand(parameters);
			if ( stringCommand == null ) {
				this.getLog().info("No command to run");
				return 0;
			} else {
				inputParam.add(stringCommand);
			}
		} else if (!command.canExecute()) {
			throw new IOException("command not found or is not an executable: " + command.getAbsolutePath());
		} else {
			inputParam.add(command.getAbsolutePath());
		}

		if (parameters != null) {
			inputParam.addAll(parameters);
		}

		if (this.log.isDebugEnabled()) {
			this.log.debug("Exec: " + inputParam.toString());
		}

		ProcessBuilder pb = new ProcessBuilder(inputParam);
		pb.redirectErrorStream(true);
		this.getLog().debug("My command: " + pb.command());
		Process process = pb.start();
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line = null;
			this.log.info("--- " + this.name + " (Start) ----------------------------");
			while ((line = br.readLine()) != null) {
				this.log.info(line);
			}
			this.log.info("--- " + this.name + " (End) ------------------------------");
		} finally {
			br.close();
		}

		int rc;

		try {
			rc = process.waitFor();
		} catch (InterruptedException e) {
			this.log.error("Comman executor interrupted");
			rc = 1;
		}

		return rc;
	}
}
