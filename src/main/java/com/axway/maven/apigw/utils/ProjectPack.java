package com.axway.maven.apigw.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.apache.maven.plugin.logging.Log;

public class ProjectPack extends AbstractCommandExecutor {

	private final File axwayGatewayHome;

	private String passphrasePol = null;

	public ProjectPack(File axwayGatewayHome, Log log) {
		super("Project Pack", log);
		this.axwayGatewayHome = Objects.requireNonNull(axwayGatewayHome, "gateway home is null");
	}

	public void setPassphrasePol(String passphrase) {
		this.passphrasePol = passphrase;
	}

	public int execute(File outDir, String name, File policyDir, Map<String, String> policyProperties)
			throws IOException {
		outDir.mkdirs();

		List<String> params = new ArrayList<String>();
		params.add("--create");

		params.add("--name");
		params.add(name);
		params.add("--type");
		params.add("pol");
		params.add("--dir");
		params.add(outDir.getPath());
		if (this.passphrasePol == null) {
			params.add("--passphrase-none");
		} else {
			params.add("--passphrase=" + this.passphrasePol);
		}

		params.add("--add");
		params.add(policyDir.getPath());
		params.add("--projpass-none");

		if (policyProperties != null) {
			for (Entry<String, String> entry : policyProperties.entrySet()) {
				params.add("--polprop");
				params.add(entry.getKey() + ": " + entry.getValue());
			}
		}

		return execute(params);
	}

	@Override
	protected File getCommand() throws IOException {
		File projpackWin = new File(this.axwayGatewayHome, "Win32/bin/projpack.bat");
		File projpackMac = new File(this.axwayGatewayHome, "win32/bin/projpack.bat");
		File projpackUnix = new File(this.axwayGatewayHome, "posix/bin/projpack");

		if (projpackUnix.exists()) {
			return projpackUnix;
		} else if (projpackWin.exists()) {
			return projpackWin;
		} else if ( projpackMac.exists() ) {
			return projpackMac;
		} else {
			throw new IOException(
					"projpack not found! Checked: " + projpackWin.getPath() + " and " + projpackUnix.getPath());
		}
	}
}
