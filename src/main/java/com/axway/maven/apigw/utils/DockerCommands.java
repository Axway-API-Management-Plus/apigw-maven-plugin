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
    public int execute ( String task, String containerName, String imageName, String imageTag,
                         Map<String, String> ports, Map<String, String> links,
                         Map<String, String> environmentVariables ) throws IOException {
        switch ( task ) {
             case "Remove Container":
                 return execute(this.executeRemoveContainer(containerName));
             case "Remove Image":
                 return execute(this.executeRemoveImage(imageName, imageTag));
             case "Create Container":
                 return execute(this.executeCreateContainer(containerName, imageName, imageTag, ports, links,
                         environmentVariables));
             default:
                 return 100;
        }
    }

    public List<String> executeRemoveContainer(String containerName ) {

        List<String> inputParam = new ArrayList<String>();
        inputParam.add("rm");
        inputParam.add("-f");
        inputParam.add(containerName);

        return inputParam;
    }

    public List<String> executeRemoveImage ( String imageName, String tag ) {
        tag = tag != null ? tag : "latest";

        List<String> inputParam = new ArrayList<String>();
        inputParam.add("rmi");
        inputParam.add(imageName + ":" + tag);

        return inputParam;
    }

    public List<String> executeCreateContainer (String containerName, String imageName, String imageTag,
                                                Map<String, String> ports, Map<String, String> links,
                                                Map<String, String> environmentVariables) {
        imageTag = imageTag != null ? imageTag : "latest";

        List<String> inputParam = new ArrayList<String>();
        inputParam.add("run");
        inputParam.add("-d");
        inputParam.add("--name");
        inputParam.add(containerName);

        if ( ports != null ) {
            for ( Map.Entry<String, String> entry : links.entrySet() ) {
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

        inputParam.add(imageName + ":" + imageTag);

        return inputParam;
    }

    @Override
    protected String getStringCommand() {
        return "docker";
    }

    @Override
    public int execute(Source source, Target target, Map<String, String> polProperties, Map<String, String> envProperties) throws IOException {
        return 0;
    }
}
