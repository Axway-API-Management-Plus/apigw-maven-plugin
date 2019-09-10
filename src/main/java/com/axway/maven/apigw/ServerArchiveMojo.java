package com.axway.maven.apigw;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "axsar", defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true, threadSafe = false, requiresDependencyResolution = ResolutionScope.COMPILE, requiresDependencyCollection = ResolutionScope.COMPILE)
public class ServerArchiveMojo extends AbstractFlattendProjectArchiveMojo {

	public static final String DIR_ARCHIVE_ROOT = "archive";
	public static final String DIR_LIBS = "lib";
	public static final String FILE_GATEWAY = "gateway";
	public static final String FILE_GATEWAY_POL = FILE_GATEWAY + ".pol";
	public static final String FILE_GATEWAY_ENV = FILE_GATEWAY + ".env";
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

		try {
			FileUtils.deleteDirectory(this.archiveBuildDir);
			this.archiveBuildDir.mkdirs();

			FileUtils.copyFileToDirectory(polFile, this.archiveBuildDir);
			FileUtils.copyFileToDirectory(envFile, this.archiveBuildDir);

			prepareReadme(this.archiveBuildDir);
			prepareStaticFiles(new File(this.archiveBuildDir, DIR_STATIC_FILES));
			prepareJars(new File(this.archiveBuildDir, DIR_LIBS));

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
		str.append("= Server Archive\n");
		str.append("Created at ").append(df.format(new Date())).append('\n');
		str.append("== Included Policy Archives\n");
		for (Artifact a : getDependentPolicyArchives()) {
			str.append(" * ").append(a.getId()).append('\n');
		}

		FileUtils.writeStringToFile(readme, str.toString(), "UTF-8");
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
			getLog().info("Prepare project pack command");
			File outDir = new File(getTargetDir(), DIR_ARCHIVE_ROOT);

			outDir.mkdirs();

			List<String> inputParam = new ArrayList<String>();
			inputParam.add(getProjectPack().getAbsolutePath());
			inputParam.add("--create");

			inputParam.add("--name");
			inputParam.add(FILE_GATEWAY);
			inputParam.add("--type");
			inputParam.add("pol");
			inputParam.add("--dir");
			inputParam.add(outDir.getPath());
			if (this.passphraseOut == null) {
				inputParam.add("--passphrase-none");
			} else {
				inputParam.add("--passphrase=" + this.passphraseOut);
			}

			inputParam.add("--add");
			inputParam.add(getPoliciesDirectory().getPath());
			inputParam.add("--projpass-none");

			inputParam.add("--polprop");
			inputParam.add("ArtifactGroup: " + this.project.getArtifact().getGroupId());

			inputParam.add("--polprop");
			inputParam.add("ArtifactName: " + this.project.getArtifact().getArtifactId());

			inputParam.add("--polprop");
			inputParam.add("ArtifactType: " + getType());

			inputParam.add("--polprop");
			inputParam.add("ArtifactVersion: " + this.project.getVersion());

			int index = 0;
			for (Artifact a : getDependentPolicyArchives()) {
				inputParam.add("--polprop");
				inputParam.add("Dependency_" + index + ": " + a.getId());
				index++;
			}

			ProcessBuilder pb = new ProcessBuilder(inputParam);
			pb.redirectErrorStream(true);
			Process process = pb.start();
			BufferedReader br = null;
			try {
				br = new BufferedReader(new InputStreamReader(process.getInputStream()));
				String line = null;
				getLog().info("--- Project Pack (Start) ----------------------------");
				while ((line = br.readLine()) != null) {
					getLog().info(line);
				}
				getLog().info("--- Project Pack (End) ------------------------------");
			} finally {
				br.close();
			}

			return outDir;
		} catch (IOException e) {
			throw new MojoExecutionException("Error on creating policy project", e);
		}
	}
}
