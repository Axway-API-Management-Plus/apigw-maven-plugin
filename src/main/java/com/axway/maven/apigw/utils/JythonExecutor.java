package com.axway.maven.apigw.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apache.maven.plugin.logging.Log;

public class JythonExecutor {

	public static final String SCRIPT_RESOURCE_PACKAGE = "scripts";

	public static final String[] SCRIPTS = { "addCert.py", "buildfed.py", "buildtemplate.py", "envconfig.py",
			"fedconfig.py" };

	private final String python;
	private final File scriptDir;
	private final Log log;

	public JythonExecutor(Log log, File pythonIntepreter, File scriptDir) throws JythonExecutorException {
		this.log = Objects.requireNonNull(log);
		Objects.requireNonNull(pythonIntepreter);

		if (!pythonIntepreter.exists() || !pythonIntepreter.canExecute()) {
			throw new JythonExecutorException("Python interpreter not found: " + pythonIntepreter.getPath());
		}

		this.python = pythonIntepreter.getAbsolutePath();
		this.scriptDir = Objects.requireNonNull(scriptDir);
	}

	public int execute(String scriptName, String[] args) throws JythonExecutorException {
		try {
			ResourceExtractor re = new ResourceExtractor(this.log);
			re.extractResources(this.scriptDir, SCRIPT_RESOURCE_PACKAGE, SCRIPTS);

			int exitValue = executeScript(scriptName, args);
			return exitValue;
		} catch (IOException e) {
			throw new JythonExecutorException("Error on executing script '" + scriptName + "'!", e);
		}
	}

	private int executeScript(String scriptName, String[] args) throws IOException {
		this.log.debug("Call Jython: " + scriptName + " " + Arrays.toString(args));
		File scriptFile = new File(this.scriptDir, scriptName);
		List<String> inputParam = new ArrayList<String>();

		inputParam.add(this.python);

		inputParam.add(scriptFile.getAbsolutePath());

		if (args != null) {
			for (String arg : args) {
				inputParam.add(arg);
			}
		}

		ProcessBuilder pb = new ProcessBuilder(inputParam);
		pb.redirectErrorStream(true);

		Process process = pb.start();

		try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			String line = null;
			while ((line = br.readLine()) != null) {
				if (line.matches("^ERROR.*: .*")) {
					this.log.error(stripLevel(line));
				} else if (line.matches("^WARN.*: .*")) {
					this.log.warn(stripLevel(line));
				} else if (line.matches("^INFO.*: .*")) {
					this.log.info(stripLevel(line));
				} else if (line.matches("^DEBUG.*: .*")) {
					this.log.debug(stripLevel(line));
				} else {
					this.log.info(line);
				}
			}
		}

		return process.exitValue();
	}

	private String stripLevel(String line) {
		int colon = line.indexOf(':');
		if (colon > 0) {
			return line.substring(colon + 2);
		}
		return line;
	}
}
