package com.axway.maven.apigw;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Mojo(name = "dependent-policies", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresProject = true, threadSafe = false, requiresDependencyResolution = ResolutionScope.TEST, requiresDependencyCollection = ResolutionScope.TEST)
public class GenerateDependentPoliciesMojo extends AbstractGatewayMojo {

	public static final String DEPENDENCY_FILE_NAME = ".projdeps.json";

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		List<File> depPolicyDirs;

		getLog().info("Prepare dependent policies ...");
		try {
			cleanDependentPoliciesDir();

			List<Artifact> archives = getDependentPolicyArchives();
			depPolicyDirs = unpackPolicyArchives(archives);

			if (getPackageType() != PackageType.DEPLOYMENT) {
				File dependencyFile = new File(getPoliciesDirectory(), DEPENDENCY_FILE_NAME);
				generateDependencyFile(dependencyFile, depPolicyDirs);
			}
			if (getPackageType() == PackageType.POLICY && this.testServerDirectory.exists()
					&& this.testServerDirectory.isDirectory()) {
				File dependencyFile = new File(this.testServerDirectory, DEPENDENCY_FILE_NAME);
				depPolicyDirs.add(this.sourceDirectory);
				generateDependencyFile(dependencyFile, depPolicyDirs);
			}

		} catch (IOException e) {
			throw new MojoExecutionException("Error on unpacking dependent policy archives.", e);
		}
		getLog().info("... dependent policies prepared.");
	}

	protected void cleanDependentPoliciesDir() throws IOException {
		if (this.sharedProjectsDir.exists() && this.sharedProjectsDir.isDirectory()) {
			FileUtils.deleteDirectory(this.sharedProjectsDir);
		}
	}

	protected List<File> unpackPolicyArchives(List<Artifact> artifacts) throws IOException {
		List<File> targetDirs = new ArrayList<File>();

		for (Artifact a : artifacts) {
			File targetDir = getSharedArtifactDir(a);
			if (targetDir.exists()) {
				FileUtils.deleteDirectory(targetDir);
			}
			unpackFile(a.getFile(), targetDir);
			targetDirs.add(targetDir);
		}

		return targetDirs;
	}

	protected void unpackFile(File file, File targetDir) throws IOException {
		if (!targetDir.exists()) {
			targetDir.mkdirs();
		}

		ZipFile zipFile = new ZipFile(file);

		try {
			Enumeration<? extends ZipEntry> entries = zipFile.entries();

			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				File target = new File(targetDir, entry.getName());

				if (entry.isDirectory()) {
					target.mkdirs();
				} else {
					target.getParentFile().mkdirs();
					InputStream in = zipFile.getInputStream(entry);
					OutputStream out = new FileOutputStream(target);
					IOUtils.copy(in, out);
					IOUtils.closeQuietly(in);
					out.close();
				}
			}
		} finally {
			zipFile.close();
		}
	}

	private void generateDependencyFile(File dependencyFile, List<File> dependentPoliciesDirs) throws IOException {
		getLog().info("Generate dependency file: " + dependencyFile.getPath());
		ObjectMapper mapper = new ObjectMapper();

		ObjectNode obj = mapper.createObjectNode();
		ArrayNode array = obj.putArray("dependentProjects");

		for (File dir : dependentPoliciesDirs) {
			array.add(new File(dir, DIR_POLICIES).getPath());
		}
		mapper.writeValue(dependencyFile, obj);
	}
}
