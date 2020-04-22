package com.axway.maven.apigw.utils;

import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DockerImage extends AbstractCommandExecutor {

    private final File axwayContainerScriptHome;
    private final String axwayImageName;
    private final String axwayImageTag;
    private final String parentImageName;
    private final String parentImageTag;
    private final String license;
    private final String mergeDir;

    public DockerImage(File axwayContainerScriptHome, String axwayImageName, String axwayImageTag,
                       String parentImageName, String parentImageTag, String license, String mergeDir, Log log) {
        super("Docker Image", log);
        this.axwayContainerScriptHome = Objects.requireNonNull(axwayContainerScriptHome,
                "scripts home is null");
        this.axwayImageName = Objects.requireNonNull(axwayImageName, "image name is null");
        this.axwayImageTag = axwayImageTag != null ? axwayImageTag : "latest";
        this.parentImageName = parentImageName;
        this.parentImageTag = parentImageTag != null ? parentImageTag : "latest";
        this.license = Objects.requireNonNull(license, "license is null");
        this.mergeDir = mergeDir;

    }

    @Override
    public int execute(Source source, Target target, Map<String, String> polProperties, Map<String,
            String> envProperties) throws IOException {
        List<String> inputParam = new ArrayList<String>();

        inputParam.add("--license");
        inputParam.add(this.license);

        inputParam.add("--out-image");
        inputParam.add(axwayImageName + ":" + axwayImageTag);

        if ( parentImageName != null ) {
            inputParam.add("--parent-image");
            inputParam.add(parentImageName + ":" + parentImageTag);
        }

        inputParam.add("--fed");
        File fedFile = source.fed.getAbsoluteFile();
        inputParam.add(fedFile.getAbsolutePath());

        this.getLog().info("fed file: " + fedFile.getAbsoluteFile().getCanonicalPath());

        if ( mergeDir != null ) {
            inputParam.add("--merge-dir");
            inputParam.add(mergeDir);
        }

        inputParam.add("--default-cert");

        return execute(inputParam);
    }

    @Override
    protected File getCommand() throws IOException {
//        ./build_gw_image.py
//                --license=/tmp/api_gw.lic
//                --domain-cert=certs/mydomain/mydomain-cert.pem
//                --domain-key=certs/mydomain/mydomain-key.pem
//                --domain-key-pass-file=/tmp/pass.txt
//                --parent-image=my-gw-base:1.0
//                --fed=my-group-fed.fed
//                --fed-pass-file=/tmp/my-group-fedpass.txt
//                --group-id=my-group
//                --merge-dir=/tmp/apigateway
        File gatewayImageCreation = new File(this.axwayContainerScriptHome, "build_gw_image.py");

        if (gatewayImageCreation.exists()) {
            return gatewayImageCreation;
        } else {
            throw new IOException(
                    "build_gw_image.py not found! Checked: " + gatewayImageCreation.getPath());
        }
    }
}
