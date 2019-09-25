package com.axway.maven.apigw.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.maven.plugin.logging.Log;

public abstract class AbstractCommandExecutor {

	private final String name;
	private final Log log;

	public AbstractCommandExecutor(String name, Log log) {
		this.name = Objects.requireNonNull(name, "name is null");
		this.log = Objects.requireNonNull(log, "log is null");
	}

	protected abstract File getCommand() throws IOException;
	
	protected Log getLog() {
		return this.log;
	}

	protected int execute(List<String> parameters) throws IOException {
		File command = getCommand();
		if (command == null) {
			throw new NullPointerException("command is null");
		}
		if (!command.canExecute()) {
			throw new IOException("command not found or is not an executable: " + command.getAbsolutePath());
		}

		List<String> inputParam = new ArrayList<String>();
		inputParam.add(command.getAbsolutePath());

		if (parameters != null) {
			inputParam.addAll(parameters);
		}

		if (this.log.isDebugEnabled()) {
			this.log.debug("Exec: " + inputParam.toString());
		}

		ProcessBuilder pb = new ProcessBuilder(inputParam);
		pb.redirectErrorStream(true);
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

		return process.exitValue();
	}
}
