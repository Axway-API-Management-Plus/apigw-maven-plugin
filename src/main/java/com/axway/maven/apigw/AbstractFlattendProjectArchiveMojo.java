package com.axway.maven.apigw;

import java.util.List;

import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;

public abstract class AbstractFlattendProjectArchiveMojo extends AbstractProjectArchiveMojo {

	@Override
	protected Model generateResultPom() {
		Model model = this.project.getOriginalModel();
		Model reducedModel = model.clone();

		// Remove all dependences
		List<Dependency> deps = reducedModel.getDependencies();
		if (deps != null) {
			deps.clear();
		}

		// Remove all plugins
		Build build = reducedModel.getBuild();
		if (build != null) {
			List<Plugin> plugins = build.getPlugins();
			if (plugins != null) {
				plugins.clear();
			}
		}

		return reducedModel;
	}
}
