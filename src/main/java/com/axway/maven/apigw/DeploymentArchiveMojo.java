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

import com.axway.maven.apigw.utils.FedBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Mojo(name = "axdar", defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true, threadSafe = false, requiresDependencyResolution = ResolutionScope.COMPILE, requiresDependencyCollection = ResolutionScope.COMPILE)
public class DeploymentArchiveMojo extends AbstractFlattendProjectArchiveMojo {

	public static final String FILE_FED_NAME = "gateway.fed";
	public static final String FILE_GATEWAY_INFO = "gateway.info.json";
	public static final String FILE_README = "readme-deployment-archive.txt";
	public static final String FILE_GATEWAY_CONFIG_JSON = "gateway.config.json";

	private Artifact serverArchive;

	@Parameter(property = "axway.tools.cfg.cert.expirationDays", required = false)
	private int certExpirationDays = 10;

	@Parameter(property = "axway.tools.cfg.cert.updateConfigured", required = false)
	private boolean updateCertConfigFile = false;

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
		File srcEnvFile = new File(getSharedArtifactDir(serverArchive), ServerArchiveMojo.FILE_GATEWAY_ENV);
		File srcPolFile = new File(getSharedArtifactDir(serverArchive), ServerArchiveMojo.FILE_GATEWAY_POL);

		File archiveBuildDir = getArchiveBuildDir();

		try {
			FileUtils.deleteDirectory(archiveBuildDir);
			archiveBuildDir.mkdirs();

			File infoFile = prepareInfoJson(archiveBuildDir);
			buildFedArchive(archiveBuildDir, srcPolFile, srcEnvFile, infoFile);

			prepareReadme(archiveBuildDir);
			FileUtils.copyFileToDirectory(
					new File(getSharedArtifactDir(this.serverArchive), ServerArchiveMojo.FILE_README), archiveBuildDir);
			
			prepareStaticFiles(new File(archiveBuildDir, DIR_STATIC_FILES));
			prepareJars(new File(archiveBuildDir, ServerArchiveMojo.DIR_LIBS));

			List<ArchiveDir> dirs = new ArrayList<ArchiveDir>();
			dirs.add(new ArchiveDir(archiveBuildDir, new String[] { "**" }, null));

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

	private File prepareInfoJson(File targetDir) throws IOException, MojoExecutionException {
		ObjectMapper mapper = new ObjectMapper();
		File info = new File(targetDir, FILE_GATEWAY_INFO);
		
		ObjectNode root = buildBasicArtifactInfo();
		
		ObjectNode serverJson;
		File serverInfo = new File(getSharedArtifactDir(this.serverArchive), ServerArchiveMojo.FILE_GATEWAY_INFO);
		if (serverInfo.canRead()) {
			serverJson = (ObjectNode) mapper.readTree(serverInfo);
		} else {
			serverJson = mapper.createObjectNode();
			serverJson.put("id", this.serverArchive.getId());
		}
		root.set("serverArchive", serverJson);
		
		String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
		
		FileUtils.writeStringToFile(info, json, "UTF-8");
		
		return info;
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
		File libs = new File(getSharedArtifactDir(this.serverArchive), ServerArchiveMojo.DIR_LIBS);
		if (libs.exists() && libs.isDirectory()) {
			FileUtils.copyDirectory(libs, targetDir);
		}
	}

	private void buildFedArchive(File targetDir, File srcPolFile, File srcEnvFile, File infoFile) throws MojoExecutionException {
		File configFile = this.configConfigFile;
		if (configFile == null) {
			configFile = new File(this.sourceDirectory, FILE_GATEWAY_CONFIG_JSON);
		}

		File outFedFile = new File(targetDir, FILE_FED_NAME);

		FedBuilder fedBuilder = new FedBuilder(this, srcPolFile, srcEnvFile, configFile, infoFile);

		fedBuilder.addPropertyFiles(getPropertyFiles());

		if (this.configCertsFile != null) {
			fedBuilder.setCertificatesFile(this.configCertsFile);
			fedBuilder.setCertificateExpirationDays(this.certExpirationDays);
			fedBuilder.enableCertificateConfigFileUpdate(this.updateCertConfigFile);
		}

		fedBuilder.setPassphrasePol(this.passphrasePol);
		fedBuilder.setPassphraseFed(this.passphraseFed);

		fedBuilder.enableVerboseMode(this.verboseCfgTools);

		int exitCode = fedBuilder.execute(outFedFile, null);
		if (exitCode != 0) {
			throw new MojoExecutionException("Build configured .fed package failed: exitCode=" + exitCode);
		}
	}
}
