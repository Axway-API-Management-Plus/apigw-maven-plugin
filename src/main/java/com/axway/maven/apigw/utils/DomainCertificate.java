package com.axway.maven.apigw.utils;

import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DomainCertificate extends AbstractCommandExecutor {
    private final File axwayContainerScriptHome;

    public DomainCertificate(String name, File containerScript, Log log) {
        super(name, log);
        this.axwayContainerScriptHome = Objects.requireNonNull(containerScript,
                "scripts home is null");
    }

    protected int execute() throws IOException {
        List<String> createDomainCert = new ArrayList<>();
        createDomainCert.add("--default-cert");
        createDomainCert.add("--force");

        return execute(createDomainCert);
    }

    @Override
    protected File getCommand() throws IOException {

        File domainCertificateCreation = new File(this.axwayContainerScriptHome, "gen_domain_cert.py.py");

        if (domainCertificateCreation.exists()) {
            return domainCertificateCreation;
        } else {
            throw new IOException(
                    "build_gw_image.py not found! Checked: " + domainCertificateCreation.getPath());
        }
    }
}
