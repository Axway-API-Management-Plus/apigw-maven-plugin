package com.axway.maven.apigw;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import com.axway.maven.apigw.utils.JythonExecutor;
import com.axway.maven.apigw.utils.ResourceExtractor;

@Mojo(defaultPhase = LifecyclePhase.GENERATE_RESOURCES, name = "tools", requiresProject = true, threadSafe = false, requiresDirectInvocation = true)
public class ToolsMojo extends AbstractMojo {

	public static String CMD_PACKAGE = "scripts";
	public static String[] COMMANDS = { "buildfed.cmd", "buildfed.sh", "encrypt.cmd", "encrypt.sh" };

	@Parameter(defaultValue = "${project}", readonly = true)
	protected MavenProject project;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			File targetDir = new File(this.project.getBuild().getDirectory(), "tools");
			File scriptDir = new File(targetDir, "lib");

			ResourceExtractor re = new ResourceExtractor(this.getLog());
			re.extractResources(targetDir, CMD_PACKAGE, COMMANDS);
			re.extractResources(scriptDir, JythonExecutor.SCRIPT_RESOURCE_PACKAGE, JythonExecutor.SCRIPTS);
			
			getLog().info("Tools extracted to " + targetDir.getPath());
		} catch (IOException e) {
			throw new MojoExecutionException("Error on extracting tools", e);
		}
	}
}
