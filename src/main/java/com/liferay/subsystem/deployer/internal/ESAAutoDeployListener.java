
package com.liferay.subsystem.deployer.internal;

import com.liferay.portal.deploy.auto.ThreadSafeAutoDeployer;
import com.liferay.portal.kernel.deploy.auto.AutoDeployException;
import com.liferay.portal.kernel.deploy.auto.AutoDeployer;
import com.liferay.portal.kernel.deploy.auto.BaseAutoDeployListener;

import java.io.File;

/**
 * @author dnebinger
 */
public class ESAAutoDeployListener extends BaseAutoDeployListener {

	@Override
	protected AutoDeployer buildAutoDeployer() {
		return new ThreadSafeAutoDeployer(new ESAAutoDeployer());
	}

	@Override
	protected String getPluginPathInfoMessage(File file) {
		return "Copied Enterprise Subsystem Archive for " + file.getPath();
	}

	@Override
	protected String getSuccessMessage(File file) {
		return "Enterprise Subsystem Archive for " + file.getPath() + " copied successfully";
	}

	@Override
	protected boolean isDeployable(File file) throws AutoDeployException {
		return file.getName().toLowerCase().endsWith(".esa");
	}
}