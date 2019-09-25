package com.axway.maven.apigw;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.zip.ZipArchiver;

public abstract class AbstractProjectArchiveMojo extends AbstractGatewayMojo {

	/**
	 * Skip to build a package if the target package already exists.
	 * 
	 * This is useful to separate package and deployment goals for CI/CD.
	 */
	@Parameter(property = "axway.skipPackaging", required = false)
	protected boolean skipPackaging = false;


	@Component
	private Map<String, Archiver> archivers;

	@Component
	private MavenProjectHelper projectHelper;

	@Parameter(defaultValue = "${session}", readonly = true, required = true)
	private MavenSession session;

	protected abstract String getArchiveExtension();

	protected abstract String getType();

	@Override
	public void execute() throws MojoExecutionException {
		File archiveFile = getArchiveFile();
		
		if (!archiveFile.exists() || !this.skipPackaging) {
			createZipArchive(archiveFile, prepareDirs());
		} else {
			getLog().info("Target file already exists and package skipping requested; package will not be re-generated.");
		}

		this.project.getArtifact().setFile(archiveFile);

		Model pom = generateResultPom();
		if (pom != null) {
			String name = StringUtils.substringBeforeLast(archiveFile.getName(), ".") + ".pom";

			File pomFile = new File(archiveFile.getParentFile(), name);
			writePom(pom, pomFile);

			this.project.setPomFile(pomFile);
		}
	}

	/**
	 * Generates the result POM model.
	 * 
	 * @return result POM model or <i>null</i> to keep original model
	 */
	protected abstract Model generateResultPom();

	private void writePom(Model pom, File pomFile) throws MojoExecutionException {
		try {
			MavenXpp3Writer writer = new MavenXpp3Writer();
			FileWriter out = new FileWriter(pomFile);
			writer.write(out, pom);
			out.close();
		} catch (IOException e) {
			throw new MojoExecutionException("Error on writing POM file", e);
		}
	}

	protected abstract List<ArchiveDir> prepareDirs() throws MojoExecutionException;
	
	protected void createZipArchive(File archiveFile, List<ArchiveDir> dirs) throws MojoExecutionException {
		ZipArchiver zip = new ZipArchiver();
		zip.setFilesonly(true);
		zip.setForced(true);
		zip.setDestFile(archiveFile);
		zip.setIncludeEmptyDirs(false);

		try {
			for (ArchiveDir ad : dirs) {
				zip.addDirectory(ad.dir, ad.includes, ad.excludes);
			}

			zip.createArchive();
		} catch (Exception e) {
			throw new MojoExecutionException("Error assembling archive", e);
		}
	}
	
	protected File getArchiveFile() {
		return getArchiveFile(getTargetDir(), this.finalName);
	}

	protected File getArchiveFile(File basedir, String resultFinalName) {
		if (basedir == null)
			throw new IllegalArgumentException("basedir must not be null");
		if (resultFinalName == null)
			throw new IllegalArgumentException("resultFinalName must not be null");

		StringBuilder fileName = new StringBuilder(resultFinalName);
		fileName.append(getArchiveExtension());

		return new File(basedir, fileName.toString());
	}
}
