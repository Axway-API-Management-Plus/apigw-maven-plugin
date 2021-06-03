package com.axway.maven.apigw.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.maven.plugin.MojoExecutionException;

import com.axway.maven.apigw.AbstractGatewayMojo;

public class FedBuilder {

	private final AbstractGatewayMojo mojo;
	private final File polFile;
	private final File envFile;
	private final File configFile;
	private final File infoFile;

	private final List<File> propertyFiles = new ArrayList<File>();
	private File certsFile = null;
	private List<File> certsBaseDirs = null;

	private int certExpirationDays = -1;

	private String passphrasePol = null;
	private String passphraseFed = null;

	private boolean updateCertConfigFile = false;
	private boolean verboseCfgTools = false;

	private File secretsFile = null;
	private File secretsKey = null;

	public FedBuilder(AbstractGatewayMojo mojo, File polFile, File envFile, File configFile, File infoFile)
			throws MojoExecutionException {
		if (mojo == null)
			throw new NullPointerException("mojo is null");
		if (polFile == null)
			throw new NullPointerException(".pol file is null");
		if (!polFile.canRead())
			throw new MojoExecutionException(".pol file not readable: " + polFile.getPath());
		if (envFile == null)
			throw new NullPointerException(".env file is null");
		if (!envFile.canRead())
			throw new MojoExecutionException(".env file not readable: " + envFile.getPath());
		if (configFile == null)
			throw new NullPointerException("config file is null");
		if (!configFile.canRead())
			throw new MojoExecutionException("config file not readable: " + configFile.getPath());

		this.mojo = mojo;
		this.polFile = polFile;
		this.envFile = envFile;
		this.configFile = configFile;
		this.infoFile = infoFile;
	}

	public void addPropertyFile(File propertyFile) {
		this.propertyFiles.add(propertyFile);
	}

	public void addPropertyFiles(List<File> propertyFiles) {
		this.propertyFiles.addAll(propertyFiles);
	}

	public void setCertificatesFile(File certsFile) {
		this.certsFile = certsFile;
	}

	public void setCertificatesBasePath(List<File> baseDirs) {
		this.certsBaseDirs = baseDirs;
	}

	public void setCertificateExpirationDays(int days) {
		this.certExpirationDays = days;
	}

	public void enableVerboseMode(boolean enabled) {
		this.verboseCfgTools = enabled;
	}

	public void enableCertificateConfigFileUpdate(boolean enabled) {
		this.updateCertConfigFile = enabled;
	}

	public void setPassphrasePol(String passphrase) {
		this.passphrasePol = passphrase;
	}

	public void setPassphraseFed(String passphrase) {
		this.passphraseFed = passphrase;
	}

	public void setSecrets(File secretsFile, File key) throws MojoExecutionException {
		if (secretsFile != null && key == null)
			throw new MojoExecutionException("No key file for secrets is specified!");
		this.secretsFile = secretsFile;
		this.secretsKey = key;
	}

	public int execute(File targetFed, Map<String, String> props) throws MojoExecutionException {
		File outFedFile = targetFed;

		try {
			JythonExecutor jython = new JythonExecutor(mojo.getHomeAxwayGateway(), mojo.getLog(),
					new File(mojo.getTargetDir(), "temp-scripts"));

			ArrayList<String> args = new ArrayList<>();

			args.add("--pol");
			args.add(this.polFile.getPath());
			args.add("--env");
			args.add(this.envFile.getPath());
			args.add("--config");
			args.add(configFile.getPath());
			for (File propertyFile : this.propertyFiles) {
				args.add("--prop");
				args.add(propertyFile.getPath());
			}
			if (this.certsFile != null) {
				args.add("--cert");
				args.add(this.certsFile.getPath());

				if (this.certExpirationDays >= 0) {
					args.add("--cert-expiration=" + this.certExpirationDays);
				}

				if (this.updateCertConfigFile) {
					args.add("--cert-config-update");
				}
				if (this.certsBaseDirs != null) {
					for (File bd : this.certsBaseDirs) {
						args.add("--base-dir");
						args.add(bd.getAbsolutePath());
					}
				}
			}
			if (this.secretsFile != null) {
				args.add("--secrets-file");
				args.add(this.secretsFile.getPath());
				args.add("--secrets-key");
				args.add(this.secretsKey.getPath());
			}
			args.add("--output-fed");
			args.add(outFedFile.getPath());

			if (this.passphrasePol != null && !this.passphrasePol.isEmpty()) {
				args.add("--passphrase-in=" + this.passphrasePol);
			}
			if (this.passphraseFed != null && !this.passphraseFed.isEmpty()) {
				args.add("--passphrase-out=" + this.passphraseFed);
			}

			if (this.verboseCfgTools) {
				args.add("--verbose");
			}

			args.add("-D");
			args.add("_system.artifact.group:" + mojo.getProject().getArtifact().getGroupId());
			args.add("-D");
			args.add("_system.artifact.name:" + mojo.getProject().getArtifact().getArtifactId());
			args.add("-D");
			args.add("_system.artifact.ver:" + mojo.getProject().getArtifact().getVersion());
			args.add("-D");
			args.add("_system.artifact.id:" + mojo.getProject().getArtifact().getId());

			if (this.infoFile != null && this.infoFile.canRead()) {
				args.add("-F");
				args.add("_system.artifact.info:" + this.infoFile.getPath());
			} else {
				args.add("-D");
				args.add("_system.artifact.info:{}");
			}

			if (props != null) {
				for (Entry<String, String> entry : props.entrySet()) {
					args.add("-D");
					args.add(entry.getKey() + ":" + entry.getValue());
				}
			}

			return jython.execute("buildfed.py", args.toArray(new String[0]));
		} catch (JythonExecutorException e) {
			throw new MojoExecutionException("Error on executing .fed builder", e);
		}
	}
}
