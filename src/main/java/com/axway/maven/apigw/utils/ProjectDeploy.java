package com.axway.maven.apigw.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.apache.maven.plugin.logging.Log;

public class ProjectDeploy extends AbstractCommandExecutor {

	private final File axwayGatewayHome;
	private final Domain domain;

	public static class Domain {
		public final String anmHost;
		public final int anmPort;
		public final String user;
		public final String password;

		public Domain(String anmHost, int port, String user, String password) {
			this.anmHost = Objects.requireNonNull(anmHost, "host of admin node manager is null");
			if (port <= 0 || port > 65535)
				throw new IllegalArgumentException("port out of range: " + port);
			this.anmPort = port;
			this.user = Objects.requireNonNull(user, "user is null");
			this.password = password;
		}
	}

	public ProjectDeploy(File axwayGatewayHome, Domain domain, Log log) {
		super("Project Deploy", log);
		this.axwayGatewayHome = Objects.requireNonNull(axwayGatewayHome, "gateway home is null");
		this.domain = Objects.requireNonNull(domain, "domain is null");
	}

	public int execute(Source source, Target target, Map<String, String> polProperties,
			Map<String, String> envProperties) throws IOException {

		Objects.requireNonNull(source, "source is null");
		Objects.requireNonNull(target, "target is null");

		if (!source.fed.canRead()) {
			throw new IOException("can't read .fed file: " + source.fed.getPath());
		}

		String name = source.fed.getName();
		if (!name.endsWith(".fed")) {
			throw new IOException("not a .fed file: " + source.fed.getPath());
		}
		name = name.substring(0, name.length() - ".fed".length());

		File workingDir = source.fed.getParentFile();

		List<String> inputParam = new ArrayList<String>();
		inputParam.add("-d");
		inputParam.add(workingDir.getAbsolutePath());

		inputParam.add("-n");
		inputParam.add(name);

		if (source.passphrase == null || source.passphrase.isEmpty()) {
			inputParam.add("--passphrase-none");
		} else {
			inputParam.add("--passphrase=" + source.passphrase);
		}

		inputParam.add("--type=fed");

		inputParam.add("--deploy-to");
		inputParam.add("--host-name=" + this.domain.anmHost);
		inputParam.add("--port=" + this.domain.anmPort);
		inputParam.add("--user-name=" + this.domain.user);
		inputParam.add("--password=" + this.domain.password);
		inputParam.add("--group-name=" + target.group);
		if (target.passphrase == null) {
			// passphrase will not be changed; no parameter requried
		} else if (target.passphrase.isEmpty()) {
			inputParam.add("--change-pass-to-none");
		} else {
			inputParam.add("--change-pass-to=" + target.passphrase);
		}

		if (polProperties != null) {
			for (Entry<String, String> entry : polProperties.entrySet()) {
				inputParam.add("--polprop=" + entry.getKey() + ": " + entry.getValue());
			}
		}

		if (envProperties != null) {
			for (Entry<String, String> entry : envProperties.entrySet()) {
				inputParam.add("--envprop=" + entry.getKey() + ": " + entry.getValue());
			}
		}

		return execute(inputParam);
	}

	@Override
	protected File getCommand() throws IOException {
		File projdeployWin = new File(this.axwayGatewayHome, "Win32/bin/projdeploy.bat");
		File projdeployUnix = new File(this.axwayGatewayHome, "posix/bin/projdeploy");

		if (projdeployWin.exists()) {
			return projdeployWin;
		} else if (projdeployUnix.exists()) {
			return projdeployUnix;
		} else {
			throw new IOException(
					"projpack not found! Checked: " + projdeployWin.getPath() + " and " + projdeployUnix.getPath());
		}
	}
}
