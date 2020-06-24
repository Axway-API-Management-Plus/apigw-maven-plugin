package com.axway.maven.apigw.utils;

import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DockerCommands extends AbstractCommandExecutor {

    public DockerCommands(String name, Log log) {
        super(name, log);
    }

    @Override
    public int execute ( String task, boolean remove, String containerName, String imageName, String imageTag,
                         Map<String, String> ports, Map<String, String> links,
                         Map<String, String> environmentVariables, String adminNodeManagerHost, String metricsDbUrl,
                         String metricsDbUsername, String metricsDbPassword ) throws IOException {
        switch ( task ) {
             case "Remove Container":
                 return execute(this.executeRemoveContainer(remove, containerName));
             case "Remove Image":
                 return execute(this.executeRemoveImage(remove, imageName, imageTag));
             case "Create Container":
                 return execute(this.executeCreateContainer(containerName, imageName, imageTag, ports, links,
                         environmentVariables, adminNodeManagerHost, metricsDbUrl, metricsDbUsername,
                         metricsDbPassword));
             default:
                 return 100;
        }
    }

    public List<String> executeRemoveContainer( boolean remove, String containerName ) {
        List<String> inputParam = new ArrayList<String>();

        if ( remove ) {
            inputParam.add("rm");
            inputParam.add("-f");
            inputParam.add(containerName);
        }

        return inputParam;
    }

    public List<String> executeRemoveImage ( boolean remove, String imageName, String tag ) {
        tag = tag != null ? tag : "latest";

        List<String> inputParam = new ArrayList<String>();

        if ( remove ) {
            inputParam.add("rmi");
            inputParam.add(imageName + ":" + tag);
        }

        return inputParam;
    }

    public List<String> executeCreateContainer (String containerName, String imageName, String imageTag,
                                                Map<String, String> ports, Map<String, String> links,
                                                Map<String, String> environmentVariables,
                                                String adminNodeManagerHost, String metricsDbUrl,
                                                String metricsDbUsername, String metricsDbPassword) {
        imageTag = imageTag != null ? imageTag : "latest";

        List<String> inputParam = new ArrayList<String>();
        inputParam.add("run");
        inputParam.add("-d");
        inputParam.add("--name");
        inputParam.add(containerName);

        if ( ports != null ) {
            for ( Map.Entry<String, String> entry : ports.entrySet() ) {
                inputParam.add("-p");
                inputParam.add(entry.getKey() + ":" + entry.getValue());
            }
        }

        if ( links != null ) {
            for ( Map.Entry<String, String> entry : links.entrySet() ) {
                inputParam.add("--link");
                inputParam.add(entry.getKey() + ":" + entry.getValue());
            }
        }

        if ( environmentVariables != null ) {
            for ( Map.Entry<String, String> entry : environmentVariables.entrySet() ) {
                inputParam.add("-e");
                inputParam.add(entry.getKey() + ":" + entry.getValue());
            }
        }

        if ( metricsDbUrl != null && metricsDbUsername != null && metricsDbPassword != null ) {
            inputParam.add("-e");
            inputParam.add("METRICS_DB_URL=" + metricsDbUrl);
            inputParam.add("-e");
            inputParam.add("METRICS_DB_USERNAME=" + metricsDbUsername);
            inputParam.add("-e");
            inputParam.add("METRICS_DB_PASS=" + metricsDbPassword);
        }

        inputParam.add("-e");
        inputParam.add("EMT_DEPLOYMENT_ENABLED=true");

        inputParam.add("-e");
        inputParam.add("EMT_ANM_HOSTS=" + adminNodeManagerHost);

        inputParam.add(imageName + ":" + imageTag);

        return inputParam;
    }

    @Override
    protected String getStringCommand( List<String> parameters ) {
        if ( parameters.size() > 0 ) {
            return "docker";
        }
        return null;
    }
}
