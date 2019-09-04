package com.axway.maven.apigw;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

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
	
	@Parameter(defaultValue = "${project}", readonly = true)
	protected MavenProject project;
	
	@Parameter(property = "axway.passphrase.in", required = false)
	protected String passphraseIn = null;

	@Parameter(property = "axway.passphrase.out", required = false)
	protected String passphraseOut = null;

	protected PackageType getPackageType() throws MojoExecutionException {
		String type = this.project.getArtifact().getType();
		try {
			return PackageType.fromType(type);
		} catch (IllegalArgumentException e) {
			throw new MojoExecutionException("Unsupported package type: " + type);
		}
	}

	protected File getJython() throws MojoExecutionException {
		File jythonWin = new File(this.homeAxwayGW, "Win32/bin/jython.bat");
		File jythonUnix = new File(this.homeAxwayGW, "posix/bin/jython");

		if (jythonWin.exists()) {
			return jythonWin;
		} else if (jythonUnix.exists()) {
			return jythonUnix;
		} else {
			throw new MojoExecutionException(
					"Jython not found! Checked: " + jythonWin.getPath() + " and " + jythonUnix.getPath());
		}
	}

	protected File getProjectPack() throws MojoExecutionException {
		File projpackWin = new File(this.homeAxwayGW, "Win32/bin/projpack.bat");
		File projpackUnix = new File(this.homeAxwayGW, "posix/bin/projpack");

		if (projpackWin.exists()) {
			return projpackWin;
		} else if (projpackUnix.exists()) {
			return projpackUnix;
		} else {
			throw new MojoExecutionException(
					"projpack not found! Checked: " + projpackWin.getPath() + " and " + projpackUnix.getPath());
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

	protected File getTargetDir() {
		return new File(this.project.getBuild().getDirectory());
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
}
