package com.axway.maven.apigw;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Starts PolicyStudio for the project.
 * 
 * 
 * @author mlook
 */
@Mojo(name = "policystudio", defaultPhase = LifecyclePhase.PROCESS_SOURCES, requiresProject = true, threadSafe = false, requiresDirectInvocation = true, requiresDependencyResolution = ResolutionScope.TEST, requiresDependencyCollection = ResolutionScope.TEST)
@Execute(goal = "dependent-policies")
public class PolicyStudioMojo extends AbstractGatewayMojo {

	public static final String FILE_PROJECT_CONNECTIONS = "projectconnections.xml";

	public static final String TEST_SERVER_PROJECT_NAME = "Test Server";

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		if (getPackageType() != PackageType.POLICY && getPackageType() != PackageType.SERVER) {
			throw new MojoExecutionException(
					"Wrong artifact type. Policy Studio is only applicable to artifacts of type '" + PackageType.POLICY
							+ "' and '" + PackageType.SERVER + "'.");
		}

		try {
			if (!getProjectConnectionFile().exists()) {
				getLog().warn("!! PolicyStudio is not initialized for this project.");
				getLog().info("!! PolicyStudio has to be initialized first.");
				getLog().info("!!");
				getLog().info("!! Please exit PolicyStudio after startup and invoke the goal again.");
				getLog().info("!!");
				getLog().info("!! Press any key to continue ...");
				System.in.read();
			} else {
				generateProjectConnectionFile();
			}
			//prepareDependentPolicies();
			executePolicyStudio();
		} catch (IOException e) {
			throw new MojoExecutionException("Error on starting PolicyStudio", e);
		}
	}

	private void executePolicyStudio() throws IOException, MojoExecutionException {
		this.policyStudioDataDir.mkdirs();
		this.policyStudioDir.mkdirs();

		List<String> cmd = new ArrayList<String>();
		cmd.add(getPolicyStudio().getPath());

		cmd.add("-data");
		cmd.add(this.policyStudioDataDir.getPath());

		cmd.add("-configuration");
		cmd.add(getConfigDir().getPath());

		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.redirectErrorStream(true);
		Process process = pb.start();
		
		try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			String line = null;
			getLog().info("--- PolicyStudio (Start) ----------------------------");
			while ((line = br.readLine()) != null) {
				getLog().info(line);
			}
			getLog().info("--- PolicyStudio (End) ------------------------------");
		}
	}

	private void generateProjectConnectionFile() throws MojoExecutionException {
		try {
			StringBuilder xml = new StringBuilder();
			xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
			xml.append("<root>");
			xml.append("<files>");
			xml.append("<file location=\"")
					.append(StringEscapeUtils.escapeXml(getPoliciesDirectory().getAbsolutePath())).append("\" url=\"")
					.append(StringEscapeUtils.escapeXml(this.project.getArtifactId())).append("\"/>");
			if (getPackageType() == PackageType.POLICY && this.testServerDirectory.exists()) {
				xml.append("<file location=\"")
						.append(StringEscapeUtils.escapeXml(this.testServerDirectory.getAbsolutePath()))
						.append("\" url=\"").append(StringEscapeUtils.escapeXml(TEST_SERVER_PROJECT_NAME))
						.append("\"/>");
			}
			xml.append("</files>");
			xml.append("</root>");

			File prj = getProjectConnectionFile();
			prj.getParentFile().mkdirs();
			FileUtils.write(prj, xml.toString(), "UTF-8");
		} catch (IOException e) {
			throw new MojoExecutionException("Error on creating project connections file.", e);
		}
	}

	private File getConfigDir() {
		return new File(this.policyStudioDir, "configuration");
	}

	private File getProjectConnectionFile() {
		return new File(new File(getConfigDir(), "login"), FILE_PROJECT_CONNECTIONS);
	}
}
