package com.axway.maven.apigw;

import org.apache.maven.model.Model;

public abstract class AbstractFlattendProjectArchiveMojo extends AbstractProjectArchiveMojo {

	@Override
	protected Model generateResultPom() {
		Model model = this.project.getOriginalModel();
		Model reducedModel = model.clone();

		// Remove all dependences
		reducedModel.getDependencies().clear();

		// Remove all plugins
		reducedModel.getBuild().getPlugins().clear();

		return reducedModel;
	}
}
