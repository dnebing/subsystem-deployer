
package com.liferay.subsystem.deployer.internal;

import com.liferay.portal.kernel.deploy.auto.AutoDeployException;
import com.liferay.portal.kernel.deploy.auto.AutoDeployer;
import com.liferay.portal.kernel.deploy.auto.context.AutoDeploymentContext;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.tools.deploy.BaseDeployer;
import com.liferay.portal.util.PropsUtil;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * @author dnebinger
 */
public class ESAAutoDeployer extends BaseDeployer {

	@Override
	public int deployFile(AutoDeploymentContext autoDeploymentContext)
		throws Exception {

		String[] moduleFrameworkAutoDeployDirs = PropsUtil.getArray(
			PropsKeys.MODULE_FRAMEWORK_AUTO_DEPLOY_DIRS);

		String destDir = null;

		for (String moduleFrameworkAutoDeployDir :
				moduleFrameworkAutoDeployDirs) {

			if (moduleFrameworkAutoDeployDir.endsWith("modules")) {
				destDir = moduleFrameworkAutoDeployDir;
			}
		}

		FileUtil.mkdirs(destDir);

		try {
			FileUtils.copyFileToDirectory(
				autoDeploymentContext.getFile(), new File(destDir));
		}
		catch (IOException ioe) {
			throw new AutoDeployException(ioe);
		}

		return AutoDeployer.CODE_DEFAULT;
	}

}