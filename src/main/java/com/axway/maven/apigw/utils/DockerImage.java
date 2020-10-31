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
    private final String domainCert;
    private final String domainKey;
    private final String domainKeyPassFile;

    public DockerImage(File axwayContainerScriptHome, String axwayImageName, String axwayImageTag,
                       String parentImageName, String parentImageTag, String license, String mergeDir,
                       String domainCert, String domainKey, String domainKeyPassFile, Log log) {
        super("Docker Image", log);
        this.axwayContainerScriptHome = Objects.requireNonNull(axwayContainerScriptHome,
                "scripts home is null");
        this.axwayImageName = Objects.requireNonNull(axwayImageName, "image name is null");
        this.axwayImageTag = axwayImageTag != null ? axwayImageTag : "latest";
        this.parentImageName = parentImageName;
        this.parentImageTag = parentImageTag != null ? parentImageTag : "latest";
        this.license = Objects.requireNonNull(license, "license is null");
        this.mergeDir = mergeDir;
        this.domainCert = domainCert;
        this.domainKey = domainKey;
        this.domainKeyPassFile = domainKeyPassFile;
    }

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

        if ( mergeDir != null ) {
            inputParam.add("--merge-dir");
            inputParam.add(mergeDir);
        }

        if ( domainCert == null || domainKey == null || domainKeyPassFile == null ) {
            DomainCertificate certificate = new DomainCertificate("Domain Certificate", axwayContainerScriptHome,
                    this.getLog());

            int exitCode = certificate.execute();
            if ( exitCode != 0 ) {
                return 1;
            }

            inputParam.add("--domain-cert");
            inputParam.add(this.axwayContainerScriptHome + "/certs/DefaultDomain/DefaultDomain-cert.pem");
            inputParam.add("--domain-key");
            inputParam.add(this.axwayContainerScriptHome + "/certs/DefaultDomain/DefaultDomain-key.pem");
            inputParam.add("--domain-key-pass-file");
            inputParam.add(this.axwayContainerScriptHome + "/certs/tmp/pass.txt");
        } else {
            inputParam.add("--domain-cert");
            inputParam.add(this.domainCert);
            inputParam.add("--domain-key");
            inputParam.add(this.domainKey);
            inputParam.add("--domain-key-pass-file");
            inputParam.add(this.domainKeyPassFile);
        }

        return execute(inputParam);
    }

    @Override
    protected File getCommand() throws IOException {
        File gatewayImageCreation = new File(this.axwayContainerScriptHome, "build_gw_image.py");

        if (gatewayImageCreation.exists()) {
            return gatewayImageCreation;
        } else {
            throw new IOException(
                    "build_gw_image.py not found! Checked: " + gatewayImageCreation.getPath());
        }
    }
}
