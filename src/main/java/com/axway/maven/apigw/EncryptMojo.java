package com.axway.maven.apigw;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import com.axway.maven.apigw.utils.Encryptor;

@Mojo(name = "encrypt", defaultPhase = LifecyclePhase.NONE, requiresProject = true, threadSafe = false)
public class EncryptMojo extends AbstractGatewayMojo {

	public EncryptMojo() {
	}
	
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		if (this.configSecretsFile == null)
			throw new MojoExecutionException("Secrets file not specified, nothing to encrypt!");
		if (this.configSecretsKey == null)
			throw new MojoExecutionException("Key file for secrets not specified!");

		Encryptor e = new Encryptor(this,  this.configSecretsFile);
		int exitCode = e.execute(this.configSecretsKey);
		if (exitCode != 0) {
			throw new MojoExecutionException("failed to encrypt secrets file: exitCode=" + exitCode);
		}
	}
}
