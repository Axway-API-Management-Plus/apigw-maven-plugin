package com.axway.maven.apigw;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import com.axway.maven.apigw.utils.JythonExecutor;
import com.axway.maven.apigw.utils.JythonExecutorException;

@Mojo(name = "axdar", defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true, threadSafe = false, requiresDependencyResolution = ResolutionScope.COMPILE, requiresDependencyCollection = ResolutionScope.COMPILE)
public class DeploymentArchiveMojo extends AbstractFlattendProjectArchiveMojo {

	public static final String FILE_README = "readme-deployment-archive.txt";
	public static final String FILE_GATEWAY_CONFIG_JSON = "gateway.config.json";

	private Artifact serverArchive;

	@Parameter(property = "propertyFile", required = false)
	private File propertyFile;

	@Parameter(property = "certsFile", required = false)
	private File certsFile;

	@Override
	protected String getArchiveExtension() {
		return PackageType.DEPLOYMENT.getExtension();
	}

	@Override
	public void execute() throws MojoExecutionException {
		List<Artifact> deps = getDependentPolicyArchives();
		if (deps.size() == 0)
			throw new MojoExecutionException("No server archive in dependencies.");
		if (deps.size() > 1)
			throw new MojoExecutionException("Too many server archives in dependencies.");

		serverArchive = deps.get(0);
		if (!PackageType.SERVER.getType().equals(serverArchive.getType())) {
			throw new MojoExecutionException("Not a server archive: " + serverArchive.getId());
		}

		super.execute();
	}

	@Override
	protected String getType() {
		return PackageType.DEPLOYMENT.getType();
	}

	@Override
	protected List<ArchiveDir> prepareDirs() throws MojoExecutionException {
		File srcEnvFile = new File(getSharedArtifactDir(serverArchive), ServerPolicyArchive.FILE_GATEWAY_ENV);
		File srcPolFile = new File(getSharedArtifactDir(serverArchive), ServerPolicyArchive.FILE_GATEWAY_POL);

		try {
			FileUtils.deleteDirectory(this.archiveBuildDir);
			this.archiveBuildDir.mkdirs();

			buildFedArchive(this.archiveBuildDir, srcPolFile, srcEnvFile);
			FileUtils.copyFile(srcPolFile, new File(this.archiveBuildDir, getGatewayFileName(".pol")));

			prepareReadme(this.archiveBuildDir);
			FileUtils.copyFileToDirectory(
					new File(getSharedArtifactDir(this.serverArchive), ServerPolicyArchive.FILE_README),
					this.archiveBuildDir);

			prepareStaticFiles(new File(this.archiveBuildDir, DIR_STATIC_FILES));
			prepareJars(new File(this.archiveBuildDir, ServerPolicyArchive.DIR_LIBS));

			List<ArchiveDir> dirs = new ArrayList<ArchiveDir>();
			dirs.add(new ArchiveDir(this.archiveBuildDir, new String[] { "**" }, null));

			return dirs;
		} catch (IOException e) {
			throw new MojoExecutionException("Error on preparing archive directories", e);
		}
	}

	private void prepareReadme(File targetDir) throws IOException, MojoExecutionException {
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		File readme = new File(targetDir, FILE_README);

		StringBuilder str = new StringBuilder();
		str.append("= Deployment Archive\n");
		str.append("Created at ").append(df.format(new Date())).append('\n');
		str.append("== Included Server Archive\n");
		str.append(" * ").append(this.serverArchive.getId()).append('\n');

		FileUtils.writeStringToFile(readme, str.toString(), "UTF-8");
	}

	private void prepareStaticFiles(File targetDir) throws IOException, MojoExecutionException {
		File staticFiles;

		// Copy static files of dependent server archive
		staticFiles = new File(getSharedArtifactDir(this.serverArchive), DIR_STATIC_FILES);
		if (staticFiles.exists() && staticFiles.isDirectory()) {
			FileUtils.copyDirectory(staticFiles, targetDir);
		}

		// Copy static files of current project
		staticFiles = new File(this.resourcesDirectory, DIR_STATIC_FILES);
		if (staticFiles.exists() && staticFiles.isDirectory()) {
			FileUtils.copyDirectory(staticFiles, targetDir);
		}
	}

	private void prepareJars(File targetDir) throws IOException, MojoExecutionException {
		// Copy dependent JARs
		File libs = new File(getSharedArtifactDir(this.serverArchive), ServerPolicyArchive.DIR_LIBS);
		if (libs.exists() && libs.isDirectory()) {
			FileUtils.copyDirectory(libs, targetDir);
		}
	}

	private void buildFedArchive(File targetDir, File srcPolFile, File srcEnvFile) throws MojoExecutionException {
		File configFile = new File(this.sourceDirectory, FILE_GATEWAY_CONFIG_JSON);

		File outFedFile = new File(targetDir, getGatewayFileName(".fed"));
		File outEnvFile = new File(targetDir, getGatewayFileName(".env"));

		try {
			JythonExecutor jython = new JythonExecutor(getLog(), this.jythonCmd,
					new File(getTargetDir(), "temp-scripts"));

			ArrayList<String> args = new ArrayList<>();
			args.add("--pol");
			args.add(srcPolFile.getPath());
			args.add("--env");
			args.add(srcEnvFile.getPath());
			args.add("--config");
			args.add(configFile.getPath());
			if (this.propertyFile != null) {
				args.add("--prop");
				args.add(this.propertyFile.getPath());
			}
			if (this.certsFile != null) {
				args.add("--cert");
				args.add(this.certsFile.getPath());
			}
			args.add("--output-fed");
			args.add(outFedFile.getPath());
			args.add("--output-env");
			args.add(outEnvFile.getPath());

			int exitCode = jython.execute("buildfed.py", args.toArray(new String[0]));

			if (exitCode != 0) {
				throw new MojoExecutionException("Failed to build .fed file: exitCode=" + exitCode);
			}
		} catch (JythonExecutorException e) {
			throw new MojoExecutionException("Error on executing .fed builder", e);
		}
	}

	private String getGatewayFileName(String prefix) {
		return "gateway" + prefix;
	}
}
