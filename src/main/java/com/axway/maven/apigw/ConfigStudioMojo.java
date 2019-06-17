package com.axway.maven.apigw;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "configstudio", defaultPhase = LifecyclePhase.PROCESS_SOURCES, requiresProject = true, threadSafe = false, requiresDirectInvocation = true, requiresDependencyResolution = ResolutionScope.TEST, requiresDependencyCollection = ResolutionScope.TEST)
@Execute(goal = "dependent-policies")
public class ConfigStudioMojo extends AbstractGatewayMojo {
	
	public static final String FILE_CONNECTIONS = "csconnections.xml";
	
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		if (getPackageType() != PackageType.DEPLOYMENT) {
			throw new MojoExecutionException("Wrong artifact type. Configuartion Studio is only applicable to artifacts of type '" + PackageType.DEPLOYMENT + "'.");
		}
		try {
			if (!getConfigDir().exists()) {
				getLog().warn("!! ConfigurationStudio is not initialized for this project.");
				getLog().info("!!");
				getLog().info("!! ConfigurationStudio has to be initialized first.");
				getLog().info("!! Please exit ConfigurationStudio after startup and invoke the goal again.");
				getLog().info("!!");
				getLog().info("!! Press any key to continue ...");
				System.in.read();
			} else {
				generateProjectConnectionFile();
			}

			executeConfigStudio();;
		} catch (IOException e) {
			throw new MojoExecutionException("Error on starting ConfigurationStudio", e);
		}
	}

	private void executeConfigStudio() throws IOException, MojoExecutionException {
		this.configStudioDataDir.mkdirs();
		this.configStudioDir.mkdirs();

		List<String> cmd = new ArrayList<String>();
		cmd.add(this.configStudioCmd.getPath());

		cmd.add("-data");
		cmd.add(this.configStudioDataDir.getPath());

		cmd.add("-configuration");
		cmd.add(getConfigDir().getPath());

		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.redirectErrorStream(true);
		Process process = pb.start();
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line = null;
			getLog().info("--- ConfigurationStudio (Start) ----------------------------");
			while ((line = br.readLine()) != null) {
				getLog().info(line);
			}
			getLog().info("--- ConfigurationStudio (End) ------------------------------");
		} finally {
			br.close();
		}
	}

	private void generateProjectConnectionFile() throws MojoExecutionException {
		try {
			String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" + 
					"<root>\r\n" + 
					"<files>\r\n" + 
					"<polfile url=\"file:/" + StringEscapeUtils.escapeXml(getPolicyDeploymentFile().getPath()) + "\"/>\r\n" + 
					"<envfile url=\"file:/" + StringEscapeUtils.escapeXml(new File(this.sourceDirectory, "gateway.env").getPath()) + "\"/>\r\n" + 
					"</files>\r\n" + 
					"</root>";

			File prj = getProjectConnectionFile();
			prj.getParentFile().mkdirs();
			FileUtils.write(prj, xml, "UTF-8");
		} catch (IOException e) {
			throw new MojoExecutionException("Error on creating project connections file.", e);
		}
	}
	
	private File getPolicyDeploymentFile() throws MojoExecutionException {
		Artifact a = getDependentPolicyArchives().get(0);
		return new File(getSharedArtifactDir(a), "gateway.pol");
	}


	private File getConfigDir() {
		return new File(this.configStudioDir, "configuration");
	}

	private File getProjectConnectionFile() {
		return new File(new File(getConfigDir(), "login"), FILE_CONNECTIONS);
	}
}
