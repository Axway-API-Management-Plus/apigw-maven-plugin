package com.axway.maven.apigw;

import java.io.File;
import java.util.Objects;

public class ArchiveDir {
	
	public static final String[] EMPTY_FILESET = new String[0];
	
	public final File dir;
	public final String[] includes;
	public final String[] excludes;

	public ArchiveDir(File dir, String[] includes, String[] excludes) {
		this.dir = Objects.requireNonNull(dir);
		this.includes = Objects.requireNonNull(includes);
		this.excludes = excludes != null ? excludes : EMPTY_FILESET;
	}

}
