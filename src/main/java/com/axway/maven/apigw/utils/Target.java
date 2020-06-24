package com.axway.maven.apigw.utils;

public class Target {
    public final String group;
    public final String passphrase;

    public Target(String group, String passphrase) {
        if (group == null || group.isEmpty()) {
            throw new IllegalArgumentException("group is null or empty");
        }
        this.group = group;
        this.passphrase = passphrase;
    }
}
