package com.axway.maven.apigw;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.model.Model;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "axpar", defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true, threadSafe = false)
public class PolicyArchiveMojo extends AbstractProjectArchiveMojo {

	@Override
	protected String getArchiveExtension() {
		return PackageType.POLICY.getExtension();
	}

	@Override
	protected String getType() {
		return PackageType.POLICY.getType();
	}

	@Override
	protected List<ArchiveDir> prepareDirs() {
		List<ArchiveDir> dirs = new ArrayList<ArchiveDir>();
		dirs.add(new ArchiveDir(this.sourceDirectory, new String[] { DIR_POLICIES + "/**" }, new String[] {"**/.projdeps.json"}));
		if (this.resourcesDirectory.isDirectory()) {
			dirs.add(new ArchiveDir(this.resourcesDirectory, new String[] { DIR_STATIC_FILES + "/**" }, null));
		}
		return dirs;
	}

	@Override
	protected Model generateResultPom() {
		return null;
	}	
}
