package com.axway.maven.apigw.utils;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.apache.commons.io.IOUtils;

public class AxwayArchiveFile implements Closeable {
	private static final String ATTR_ID = "Id";

	private final JarFile file;
	private final String id;
	private final String policyDir;

	public AxwayArchiveFile(File file) throws IOException {
		this.file = new JarFile(Objects.requireNonNull(file));
		this.id = this.file.getManifest().getMainAttributes().getValue(ATTR_ID);
		if (this.id == null || this.id.isEmpty())
			throw new IllegalStateException("Missing attribute '" + ATTR_ID + "' in manifest.");
		this.policyDir = this.id + "/";
	}

	@Override
	public void close() throws IOException {
		if (this.file != null)
			this.file.close();
	}

	public String getId() {
		return this.id;
	}

	public InputStream getEnvSettingsStore() throws IOException {
		ZipEntry entry = this.file.getEntry(getId() + "/EnvSettingsStore.xml");
		InputStream in = this.file.getInputStream(entry);
		return in;
	}

	public void extractPolicies(File targetDir) throws IOException {
		extractSubDir(targetDir, policyDir, false);
		extractSubDir(new File(targetDir, "meta-inf"), "meta-inf/", true);
	}

	public void extract(File targetDir) throws IOException {
		Objects.requireNonNull(targetDir);

		targetDir.mkdirs();

		Enumeration<JarEntry> entries = this.file.entries();
		while (entries.hasMoreElements()) {
			JarEntry entry = entries.nextElement();
			File target = new File(targetDir, entry.getName());

			if (entry.isDirectory()) {
				target.mkdirs();
			} else {
				extractEntry(entry, target);
			}
		}
	}

	protected void extractSubDir(File targetDir, String subDir, boolean includeSubDirs)
			throws FileNotFoundException, IOException {
		Objects.requireNonNull(targetDir);
		Objects.requireNonNull(subDir);

		if (!subDir.endsWith("/")) {
			throw new IllegalArgumentException(
					"sub directory '" + subDir + "' is not a directory; missing '/' at the end");
		}

		targetDir.mkdirs();

		Enumeration<JarEntry> entries = this.file.entries();
		while (entries.hasMoreElements()) {
			JarEntry entry = entries.nextElement();

			if (!entry.isDirectory()) {
				String name = entry.getName();
				if (name.startsWith(subDir)) {
					String fileName = name.substring(subDir.length());
					if (includeSubDirs || fileName.indexOf('/') < 0) {
						File target = new File(targetDir, fileName);

						extractEntry(entry, target);
					}
				}

			}
		}
	}

	private void extractEntry(JarEntry entry, File target) throws IOException {
		target.getParentFile().mkdirs();

		try (OutputStream out = new FileOutputStream(target);) {
			InputStream in = this.file.getInputStream(entry);
			IOUtils.copy(in, out);
			IOUtils.closeQuietly(in);
		}
	}
}
