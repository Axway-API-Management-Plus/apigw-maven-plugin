package com.axway.maven.apigw;

import com.axway.maven.apigw.utils.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.org.apache.xpath.internal.operations.Bool;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Mojo(name = "container", defaultPhase = LifecyclePhase.NONE, requiresProject = true, threadSafe = false)
@Execute(phase = LifecyclePhase.PACKAGE)
public class ContainerMojo extends AbstractGatewayMojo {

	public static final String DEPLOY_DIR_NAME = "axway-deploy";

	private static final String PROJECT_NAME = "gateway";

	@Parameter(property = "axway.deploy.group", required = true)
	private String deployGroup;

	@Parameter(property = "axway.passphrase.deploy", required = false)
	private String passphraseDeploy = null;

	/**
	 * Properties to create and deploy new docker containers.
	 */
	@Parameter(property = "axway.container.scripts", required = true)
	protected File containerScripts;

	@Parameter(property = "axway.container.name", required = true)
	protected String containerName;

	@Parameter(property = "axway.remove.container", required = false)
	protected String axwayRemoveContainer;

	@Parameter(property = "axway.image.name", required = true)
	protected String imageName;

	@Parameter(property = "axway.image.tag", required = false)
	protected String imageTag;

	@Parameter(property = "axway.remove.image", required = false)
	protected String axwayRemoveImage;

	@Parameter(property = "axway.parent.image.name", required = false)
	protected String parentImageName;

	@Parameter(property = "axway.parent.image.tag", required = false)
	protected String parentImageTag;

	@Parameter(property = "axway.license", required = true)
	protected String license;

	@Parameter(property = "axway.merge.dir", required = false)
	protected String mergeDir;

	@Parameter(property = "axway.ports", required = false)
	private String axwayPorts;

	@Parameter(property = "axway.links", required = false)
	private String axwayLinks;

	@Parameter(property = "axway.environment.variables", required = false)
	private String axwayEnvironmentVariables;

	@Parameter(property = "axway.domain.cert", required = false)
	private String axwayDomainCert;

	private boolean removeContainer = false;
	private boolean removeImage = false;

	private Map<String, String> containerPorts;
	private Map<String, String> containerLinks;
	private Map<String, String> containerEnvironmentVariables;

	public boolean isRemoveContainer() {
		if ( !this.removeContainer ) {
			this.removeContainer = Boolean.parseBoolean(axwayRemoveContainer);
		}
		return this.removeContainer;
	}

	public boolean isRemoveImage() {
		if ( !this.removeImage ) {
			this.getLog().info("Checking to remove image: " + Boolean.parseBoolean(axwayRemoveImage));
			this.removeImage = Boolean.parseBoolean(axwayRemoveImage);
			this.getLog().info("removeImage: " + String.valueOf(this.removeImage));
		}
		this.getLog().info("removeImage: " + String.valueOf(this.removeImage));
		return this.removeImage;
	}

	public Map<String, String> getContainerPorts() {
		if ( containerPorts == null ) {
			containerPorts = splitString(axwayPorts);
		}
		return containerPorts;
	}

	public Map<String, String> getContainerLinks() {
		if ( containerLinks == null ) {
			containerLinks = splitString(axwayLinks);
		}
		return containerLinks;
	}

	public Map<String, String> getContainerEnvironmentVariables() {
		if ( containerEnvironmentVariables == null ) {
			containerEnvironmentVariables = splitString(axwayEnvironmentVariables);
		}
		return containerEnvironmentVariables;
	}

	private Map<String, String> splitString(String splitMe) {
		String[] split = splitMe.split(",");
		Map<String, String> map = new HashMap<String, String>();
		for ( String mapping : split ) {
			String[] mappings = mapping.split(";");
			map.put(mappings[0], mappings[1]);
		}
		return map;
	}

	public ContainerMojo() {
	}

	private File getTempDir() {
		return new File(getTargetDir(), DEPLOY_DIR_NAME);
	}

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		PackageType pkg = getPackageType();

		switch (pkg) {
		case POLICY:
			deployPolicyProject();
			break;

		case SERVER:
			deployServerProject();
			break;

		case DEPLOYMENT:
			deployDeploymentProject();
			break;

		default:
			throw new MojoExecutionException("Unsupported package type: " + getPackageType());
		}
	}

	private void deployPolicyProject() throws MojoExecutionException {
		try {
			// pack test server project
			ProjectPack packer = new ProjectPack(this.homeAxwayGW, getLog());
			packer.setPassphrasePol(this.passphrasePol);
			int exitCode = packer.execute(getTempDir(), PROJECT_NAME, this.testServerDirectory, null);
			if (exitCode != 0) {
				throw new MojoExecutionException("failed to build packed project");
			}

			// configure fed
			File pol = new File(getTempDir(), PROJECT_NAME + ".pol");
			File env = new File(getTempDir(), PROJECT_NAME + ".env");
			File info = new File(getTempDir(), PROJECT_NAME + ".info.json");

			ObjectMapper mapper = new ObjectMapper();
			ObjectNode root = buildBasicArtifactInfo();
			String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
			FileUtils.writeStringToFile(info, json, "UTF-8");

			File fed = configFed(pol, env, info);

			// deploy to server
			deployFed(fed);
		} catch (IOException e) {
			throw new MojoExecutionException("Error on packing project", e);
		}
	}

	private void deployServerProject() throws MojoExecutionException {
		File archiveBuildDir = getArchiveBuildDir();

		// configure fed
		File pol = new File(archiveBuildDir, ServerArchiveMojo.FILE_GATEWAY_POL);
		File env = new File(archiveBuildDir, ServerArchiveMojo.FILE_GATEWAY_ENV);
		File info = new File(archiveBuildDir, ServerArchiveMojo.FILE_GATEWAY_INFO);

		File fed = configFed(pol, env, info);

		// deploy to server
		deployFed(fed);
	}

	private void deployDeploymentProject() throws MojoExecutionException {
		File fed = new File(getArchiveBuildDir(), DeploymentArchiveMojo.FILE_FED_NAME);

		deployFed(fed);
	}

	private File configFed(File pol, File env, File info) throws MojoExecutionException {
		FedBuilder fb = new FedBuilder(this, pol, env, this.configConfigFile, info);
		fb.setPassphrasePol(this.passphrasePol);
		fb.setPassphraseFed(this.passphraseFed);

		fb.addPropertyFiles(getPropertyFiles());

		fb.setCertificatesFile(this.configCertsFile);
		fb.enableVerboseMode(this.verboseCfgTools);

		File fed = new File(getTempDir(), PROJECT_NAME + ".fed");

		int exitCode = fb.execute(fed, null);
		if (exitCode != 0) {
			throw new MojoExecutionException("failed to configure project: exitCode=" + exitCode);
		}

		return fed;
	}

	private void deployFed(File fed) throws MojoExecutionException {
		try {
			Map<String, String> polProps = new HashMap<>();
			polProps.put("Name", this.project.getGroupId() + ":" + this.project.getArtifactId());
			polProps.put("Version", this.project.getVersion());
			polProps.put("Type", "Test Deployment");

			Source source = new Source(fed, this.passphraseFed);
			Target target = new Target(this.deployGroup, this.passphraseDeploy);

			AbstractCommandExecutor deploy;

			this.getLog().info("Here we go::::  this.container:  " + this.containerName);

			// containerName is populated, so we are going to create a new container
			this.getLog().info("Doing my docker work to deploy a fed");
			AbstractCommandExecutor dockerCommands = new DockerCommands("Docker Commands", getLog());
			int exitCode = dockerCommands.execute("Remove Container", this.isRemoveContainer(), this.containerName,
					null, null, null, null, null);
			if ( exitCode != 0 ) {
				throw new MojoExecutionException("Failed to remove existing container: exitCode: " + exitCode);
			}

			exitCode = dockerCommands.execute("Remove Image", this.isRemoveImage(), this.containerName, this.imageName,
					this.imageTag, null, null, null);
			if ( exitCode != 0 ) {
				throw new MojoExecutionException("Failed to remove existing image: exitCode: " + exitCode);
			}

			deploy = new DockerImage(this.containerScripts, this.imageName, this.imageTag, this.parentImageName,
					this.parentImageTag, this.license, this.mergeDir, this.axwayDomainCert, getLog());
			exitCode = deploy.execute(source, target, polProps, null);
			if ( exitCode != 0 ) {
				throw new MojoExecutionException("Failed to create new Docker Image: exitCode: " + exitCode);
			}

			exitCode = dockerCommands.execute("Create Container", false, this.containerName,
					this.imageName, this.imageTag, this.getContainerPorts(), this.getContainerLinks(),
					this.getContainerEnvironmentVariables());
			if ( exitCode != 0 ) {
				throw new MojoExecutionException("Failed to create new container: exitCode: " + exitCode);
			}
		} catch (IOException e) {
			throw new MojoExecutionException("Error on deploying project", e);
		}
	}
}
