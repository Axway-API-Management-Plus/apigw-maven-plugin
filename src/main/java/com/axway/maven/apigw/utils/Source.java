package com.axway.maven.apigw.utils;

import java.io.File;
import java.util.Objects;

public class Source {
    public final File fed;
    public final String passphrase;

    public Source(File fed, String passphrase) {
        this.fed = Objects.requireNonNull(fed, ".fed file is null");
        this.passphrase = passphrase;
    }
}
