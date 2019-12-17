package com.axway.maven.apigw;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import com.axway.maven.apigw.utils.ProjectPack;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Mojo(name = "axsar", defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true, threadSafe = false, requiresDependencyResolution = ResolutionScope.COMPILE, requiresDependencyCollection = ResolutionScope.COMPILE)
public class ServerArchiveMojo extends AbstractFlattendProjectArchiveMojo {

	public static final String DIR_ARCHIVE_ROOT = "archive";
	public static final String DIR_LIBS = "lib";
	public static final String FILE_GATEWAY = "gateway";
	public static final String FILE_GATEWAY_POL = FILE_GATEWAY + ".pol";
	public static final String FILE_GATEWAY_ENV = FILE_GATEWAY + ".env";
	public static final String FILE_GATEWAY_INFO = FILE_GATEWAY + ".info.json";
	public static final String FILE_README = "readme-server-archive.txt";

	@Override
	protected String getArchiveExtension() {
		return PackageType.SERVER.getExtension();
	}

	@Override
	protected String getType() {
		return PackageType.SERVER.getType();
	}

	@Override
	protected List<ArchiveDir> prepareDirs() throws MojoExecutionException {
		File outDir = executeProjPack();
		File polFile = new File(outDir, FILE_GATEWAY_POL);
		File envFile = new File(outDir, FILE_GATEWAY_ENV);
		
		File archiveBuildDir = getArchiveBuildDir();

		try {
			FileUtils.deleteDirectory(archiveBuildDir);
			archiveBuildDir.mkdirs();

			FileUtils.copyFileToDirectory(polFile, archiveBuildDir);
			FileUtils.copyFileToDirectory(envFile, archiveBuildDir);

			prepareReadme(archiveBuildDir);
			prepareInfoJson(archiveBuildDir);
			prepareStaticFiles(new File(archiveBuildDir, DIR_STATIC_FILES));
			prepareJars(new File(archiveBuildDir, DIR_LIBS));

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
		str.append("= Server Archive\n");
		str.append("Created at ").append(df.format(new Date())).append('\n');
		str.append("== Included Policy Archives\n");
		for (Artifact a : getDependentPolicyArchives()) {
			str.append(" * ").append(a.getId()).append('\n');
		}

		FileUtils.writeStringToFile(readme, str.toString(), "UTF-8");
	}
	
	private void prepareInfoJson(File targetDir) throws IOException, MojoExecutionException {
		ObjectMapper mapper = new ObjectMapper();
		File info = new File(targetDir, FILE_GATEWAY_INFO);
	
		ObjectNode root = buildBasicArtifactInfo();
		
		String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
		
		FileUtils.writeStringToFile(info, json, "UTF-8");
	}

	private void prepareStaticFiles(File targetDir) throws IOException, MojoExecutionException {
		File staticFiles;

		// Copy static files of dependent policy archives
		for (Artifact a : getDependentPolicyArchives()) {
			staticFiles = new File(getSharedArtifactDir(a), DIR_STATIC_FILES);
			if (staticFiles.exists() && staticFiles.isDirectory()) {
				FileUtils.copyDirectory(staticFiles, targetDir);
			}
		}

		// Copy static files of current project
		staticFiles = new File(this.resourcesDirectory, DIR_STATIC_FILES);
		if (staticFiles.exists() && staticFiles.isDirectory()) {
			FileUtils.copyDirectory(staticFiles, targetDir);
		}
	}

	private void prepareJars(File targetDir) throws IOException, MojoExecutionException {
		// Copy dependent JARs
		File lib;
		for (Artifact a : getDependentJars()) {
			lib = a.getFile();
			FileUtils.copyFile(lib, new File(targetDir, lib.getName()));
		}
	}

	private File executeProjPack() throws MojoExecutionException {
		try {
			File outDir = new File(getTargetDir(), DIR_ARCHIVE_ROOT);
			
			ProjectPack packer = new ProjectPack(this.homeAxwayGW, getLog());
			packer.setPassphrasePol(this.passphrasePol);
			Map<String, String> polProps = new HashMap<>();
			polProps.put("Name", this.project.getGroupId() + ":" + this.project.getArtifactId());
			polProps.put("Version", this.project.getVersion());			
			polProps.put("Type", getType());
			
			int index = 0;
			for (Artifact a : getDependentPolicyArchives()) {
				polProps.put("Dependency_" + index, a.getId());
				index++;
			}
			
			packer.execute(outDir, FILE_GATEWAY, getPoliciesDirectory(), polProps);

			return outDir;
		} catch (IOException e) {
			throw new MojoExecutionException("Error on creating policy project", e);
		}
	}
}
