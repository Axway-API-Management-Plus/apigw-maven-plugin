package com.axway.maven.apigw;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.axway.maven.apigw.utils.AxwayArchiveFile;

@Mojo(defaultPhase = LifecyclePhase.GENERATE_SOURCES, name = "init", requiresProject = true, threadSafe = false, requiresDirectInvocation = true)
public class InitMojo extends AbstractGatewayMojo {

	@Parameter(property = "axway.template.gateway.fed", defaultValue = "${axway.home}/apigateway/system/conf/templates/BlankConfiguration-VordelGateway.fed")
	protected File templateGateway;

	@Parameter(property = "axway.template.policies.fed", defaultValue = "${axway.home}/apigateway/system/conf/templates/BlankNoSettingsConfiguration-VordelGateway.fed")
	protected File templatePolicies;

	@Parameter(property = "axway.template.config.env", defaultValue = "${axway.home}/apigateway/system/conf/templates/EmptyBlankConfiguration-VordelGateway.env")
	protected File templateConfig;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		checkAxwayHome();

		PackageType pkg = getPackageType();

		getLog().info("Initialize project for package type '" + pkg.getType() + "'");

		switch (pkg) {
		case POLICY:
			initDefaultPolicies();
			initTestGateway();
			break;

		case SERVER:
			initDefaultGateway();
			break;

		case DEPLOYMENT:
			initDefaultConfig();
			break;

		default:
			throw new MojoExecutionException("Unsupported package type: " + getPackageType());
		}
	}

	protected void initTestGateway() throws MojoExecutionException {
		extractFedTemplate(this.templateGateway, this.testServerDirectory, "test gateway");
		createProjectFile(this.testServerDirectory, PolicyStudioMojo.TEST_SERVER_PROJECT_NAME);
	}

	protected void initDefaultPolicies() throws MojoExecutionException {
		File dir = new File(this.sourceDirectory, DIR_POLICIES);
		extractFedTemplate(this.templatePolicies, dir, "default policies");
		createProjectFile(dir, buildProjectName());
	}

	protected void initDefaultGateway() throws MojoExecutionException {
		File dir = new File(this.sourceDirectory, DIR_POLICIES);
		extractFedTemplate(this.templateGateway, dir, "default gateway");
		createProjectFile(dir, buildProjectName());
	}

	protected void initDefaultConfig() throws MojoExecutionException {
		try {
			// write empty JSON configuration file
			File configFile = new File(this.sourceDirectory, DeploymentArchiveMojo.FILE_GATEWAY_CONFIG_JSON);
			if (!configFile.exists()) {
				FileUtils.writeStringToFile(configFile, "{}", "UTF-8");
			} else {
				getLog().info("Nothing to do, configuration file already initialized.");
			}
		} catch (IOException e) {
			throw new MojoExecutionException("Error on initializing deployment archive project", e);
		}
	}

	protected void extractFedTemplate(File fedFile, File targetDir, String componentName)
			throws MojoExecutionException {
		File primaryStore = new File(targetDir, "PrimaryStore.xml");
		if (primaryStore.exists()) {
			getLog().info("Nothing to do, " + componentName + " already initialized.");
			return;
		}

		getLog().info("Initialize " + componentName + " by using template '" + fedFile.getPath() + "' to '" + targetDir
				+ "'.");

		try (AxwayArchiveFile fed = new AxwayArchiveFile(fedFile);) {
			fed.extractPolicies(targetDir);
		} catch (IOException e) {
			throw new MojoExecutionException("Error on initializing " + componentName + " from template", e);
		}
	}

	protected void createProjectFile(File dir, String projectName) throws MojoExecutionException {
		try {
			File prj = new File(dir, ".project");
			StringBuilder xml = new StringBuilder();
			xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			xml.append("<projectDescription>\n");
			xml.append("  <name>").append(StringEscapeUtils.escapeXml(projectName)).append("</name>\n");
			xml.append("  <comment/>\n");
			xml.append("  <projects/>\n");
			xml.append("  <buildSpec/>\n");
			xml.append("  <natures/>\n");
			xml.append("</projectDescription>");

			FileUtils.write(prj, xml.toString(), "UTF-8");
		} catch (IOException e) {
			throw new MojoExecutionException("Error on creating project file", e);
		}
	}
	
	protected String buildProjectName() {
		StringBuilder name = new StringBuilder();
		name.append(this.project.getGroupId());
		if (name.length() > 0)
			name.append(".");
		name.append(this.project.getArtifactId());

		return name.toString();
	}
}
