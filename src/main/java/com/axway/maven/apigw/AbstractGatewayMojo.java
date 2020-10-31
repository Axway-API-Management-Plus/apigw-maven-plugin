package com.axway.maven.apigw;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 *
 */
public abstract class AbstractGatewayMojo extends AbstractMojo {

	public static final String DIR_POLICIES = "policies";
	public static final String DIR_STATIC_FILES = "staticFiles";

	public static final String DEPENDENCY_FILE_NAME = ".projdeps.json";

	@Parameter(property = "axway.home", required = true)
	protected File homeAxway;

	@Parameter(defaultValue = "${project.build.finalName}", readonly = true, required = true)
	protected String finalName;

	@Parameter(property = "axway.dir.source", defaultValue = "${basedir}/src/main/axwgw", required = true)
	protected File sourceDirectory;

	@Parameter(property = "axway.dir.resources", defaultValue = "${basedir}/src/main/resources", required = true)
	protected File resourcesDirectory;

	@Parameter(property = "axway.dir.sharedProjects", defaultValue = "${project.build.directory}/sharedProjects", required = true)
	protected File sharedProjectsDir;

	@Parameter(property = "axway.dir.testServer", defaultValue = "${basedir}/src/test/policies")
	protected File testServerDirectory;

	@Parameter(property = "axway.home.apigw", defaultValue = "${axway.home}/apigateway", required = true)
	protected File homeAxwayGW;

	@Parameter(property = "axway.home.policystudio", defaultValue = "${axway.home}/policystudio", required = true)
	protected File homePolicyStudio;

	@Parameter(property = "axway.policystudio.data", defaultValue = "${basedir}/.studio/data")
	protected File policyStudioDataDir;

	@Parameter(property = "axway.policystudio.config", defaultValue = "${basedir}/.studio/conf")
	protected File policyStudioConfigDir;

	@Parameter(property = "axway.tools.cfg.verbose", defaultValue = "false", required = true)
	protected boolean verboseCfgTools;

	/**
	 * Passphrase for .pol and .env files.
	 */
	@Parameter(property = "axway.passphrase.pol", required = false)
	protected String passphrasePol = null;

	/**
	 * Passphrase for .fed file.
	 */
	@Parameter(property = "axway.passphrase.fed", required = false)
	protected String passphraseFed = null;

	/**
	 * Path to configuration file for environmentalized fields.
	 */
	@Parameter(property = "axway.config.envs", required = false)
	protected File configConfigFile;

	/**
	 * Path to configuration file for properties.
	 * 
	 * Properties may be used by the environmentalized fields and certificates
	 * configuration file.
	 */
	@Parameter(property = "axway.config.props", required = false)
	protected File configPropertyFile;

	@Parameter(required = false)
	protected File[] configPropertyFiles;

	@Parameter(property = "axway.config.props.files", required = false)
	protected File[] configPropertyFilesAdditional;
	
	/**
	 * Path to the certificates configuration file.
	 */
	@Parameter(property = "axway.config.certs", required = false)
	protected File configCertsFile;

	@Parameter(property = "axway.config.certs.basedir", required = false)
	protected File configCertsBaseDir = null;
	
	@Parameter(property = "axway.tools.cfg.cert.expirationDays", required = false)
	protected int certExpirationDays = 10;

	@Parameter(property = "axway.tools.cfg.cert.updateConfigured", required = false)
	protected boolean updateCertConfigFile = false;
	
	@Parameter(property = "axway.config.secrets.file", required = false)
	protected File configSecretsFile = null;
	
	@Parameter(property = "axway.config.secrets.key", required = false)
	protected File configSecretsKey = null;
	
	@Parameter(property = "axway.project.version", required = false, defaultValue="${project.version}")
	protected String projectVersion;

	
	@Parameter(defaultValue = "${project}", readonly = true)
	protected MavenProject project;

	
	public MavenProject getProject() {
		return this.project;
	}

	public File getTargetDir() {
		return new File(this.project.getBuild().getDirectory());
	}

	public File getHomeAxay() {
		return this.homeAxway;
	}

	public File getHomeAxwayGateway() {
		return this.homeAxwayGW;
	}


	protected PackageType getPackageType() throws MojoExecutionException {
		String type = this.project.getArtifact().getType();
		try {
			return PackageType.fromType(type);
		} catch (IllegalArgumentException e) {
			throw new MojoExecutionException("Unsupported package type: " + type);
		}
	}

	protected File getPolicyStudio() throws MojoExecutionException {
		File studioWin = new File(this.homePolicyStudio, "policystudio.exe");
		File studioUnix = new File(this.homePolicyStudio, "policystudio");

		if (studioWin.exists()) {
			return studioWin;
		} else if (studioUnix.exists()) {
			return studioUnix;
		} else {
			throw new MojoExecutionException(
					"PolicyStudio not found! Checked: " + studioWin.getPath() + " and " + studioUnix.getPath());
		}
	}

	/**
	 * Returns the folder for building archives.
	 * 
	 * @return archive folder
	 */
	protected File getArchiveBuildDir() {
		return new File(getTargetDir(), "axway-archive");
	}

	protected void checkAxwayHome() throws MojoExecutionException {
		if (!this.homeAxway.isDirectory() || !new File(this.homeAxway, "apigateway").isDirectory()) {
			throw new MojoExecutionException(
					"Directory '" + this.homeAxway.getPath() + "' is not a valid Axway home directory!");
		}
	}

	protected File getPoliciesDirectory() throws MojoExecutionException {
		return getPoliciesDirectory(this.sourceDirectory);
	}

	protected Optional<File> getStaticFilesDirectory() throws MojoExecutionException {
		return getStaticFilesDirectory(this.resourcesDirectory);
	}

	protected File getPoliciesDirectory(File srcDir) throws MojoExecutionException {
		File policiesDirectory = new File(srcDir, DIR_POLICIES);

		if (!policiesDirectory.exists()) {
			throw new MojoExecutionException("Invalid source directory layout: missing '" + DIR_POLICIES
					+ "' directory: " + policiesDirectory.getPath());
		}
		if (!policiesDirectory.isDirectory()) {
			throw new MojoExecutionException(
					"Invalid source directory layout: '" + policiesDirectory.getPath() + "' is not a directory!");
		}
		return policiesDirectory;
	}

	protected Optional<File> getStaticFilesDirectory(File srcDir) throws MojoExecutionException {
		File staticFilesDirectory = new File(srcDir, DIR_STATIC_FILES);

		if (!staticFilesDirectory.exists())
			return Optional.empty();

		if (!staticFilesDirectory.isDirectory())
			throw new MojoExecutionException(
					"Invalid static files directory: '" + staticFilesDirectory.getPath() + "' is not a directory!");

		return Optional.of(staticFilesDirectory);
	}

	protected List<Artifact> getDependentPolicyArchives() throws MojoExecutionException {
		PackageType pg = getPackageType();
		Set<String> includedTypes = new HashSet<String>();
		if (pg == PackageType.DEPLOYMENT) {
			includedTypes.add(PackageType.SERVER.getType());
		} else {
			includedTypes.add(PackageType.POLICY.getType());
		}
		return getDependencies(includedTypes);
	}

	protected List<Artifact> getDependentJars() throws MojoExecutionException {
		Set<String> includedTypes = new HashSet<String>();
		includedTypes.add("jar");
		return getDependencies(includedTypes);
	}

	protected List<Artifact> getDependencies(Set<String> includedTypes) {
		Set<Artifact> artifacts = this.project.getArtifacts();
		List<Artifact> deps = new ArrayList<Artifact>();

		for (Artifact a : artifacts) {
			if (includedTypes == null || includedTypes.contains(a.getType())) {
				getLog().info("Found dependency: " + a.getArtifactId());
				deps.add(a);
			}
		}

		return deps;
	}

	protected File getSharedArtifactDir(Artifact a) {
		return new File(this.sharedProjectsDir, a.getArtifactId());
	}

	protected String buildProjectName() {
		StringBuilder name = new StringBuilder();
		name.append(this.project.getGroupId());
		if (name.length() > 0)
			name.append(".");
		name.append(this.project.getArtifactId());

		return name.toString();
	}
	
	protected List<File> getPropertyFiles() {
		List<File> propFiles = new ArrayList<File>();

		if (this.configPropertyFile != null) {
			propFiles.add(this.configPropertyFile);
		}
		if(this.configPropertyFiles != null) {
			for (File pf : this.configPropertyFiles) {
				propFiles.add(pf);
			}
		}
		if(this.configPropertyFilesAdditional != null) {
			for (File pf : this.configPropertyFilesAdditional) {
				propFiles.add(pf);
			}
		}

		return propFiles;
	}
	
	protected ObjectNode buildBasicArtifactInfo() {
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		ObjectMapper mapper = new ObjectMapper();
		
		ObjectNode artifact = mapper.createObjectNode();
		artifact.put("groupID", this.project.getGroupId());
		artifact.put("artifactID",  this.project.getArtifactId());
		artifact.put("version",  this.project.getVersion());
		
		ObjectNode root = mapper.createObjectNode();
		root.put("id", this.project.getId());
		root.put("name", this.project.getName());
		root.put("description",  this.project.getDescription());
		root.put("buildTime", df.format(new Date()));
		root.set("artifact", artifact);
		
		ArrayNode deps = mapper.createArrayNode();
		for (Artifact a : getDependencies(null)) {
			deps.add(a.getId());
		}
		root.set("dependencies", deps);

		return root;
	}
	
	protected Map<String, String> buildPolicyProperties() throws MojoExecutionException  {
		Map<String, String> polProps = new HashMap<>();
		polProps.put("Name", this.project.getGroupId() + ":" + this.project.getArtifactId());
		polProps.put("Version", this.projectVersion);			
		polProps.put("Type", getPackageType().getType());
		
		return polProps;
	}
}
