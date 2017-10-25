package com.liferay.subsystem.deployer.internal;

import com.liferay.portal.kernel.deploy.auto.AutoDeployDir;
import com.liferay.portal.kernel.deploy.auto.AutoDeployScanner;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;

/**
 * @author dnebinger
 */
public class ESAAutoDeployScanner extends Thread {

	public ESAAutoDeployScanner(
		ThreadGroup threadGroup, String name, ESAAutoDeployDir autoDeployDir) {

		super(threadGroup, name);

		_autoDeployDir = autoDeployDir;

		Class<?> clazz = getClass();

		setContextClassLoader(clazz.getClassLoader());

		setDaemon(true);
		setPriority(MIN_PRIORITY);
	}

	public void pause() {
		_started = false;
	}

	@Override
	public void run() {
		try {
			sleep(1000 * 10);
		}
		catch (InterruptedException ie) {
		}

		while (_started) {
			try {
				sleep(_autoDeployDir.getInterval());
			}
			catch (InterruptedException ie) {
			}

			try {
				_autoDeployDir.scanDirectory();
			}
			catch (Exception e) {
				if (_log.isWarnEnabled()) {
					_log.warn("Unable to scan the auto deploy directory", e);
				}
			}
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
			ESAAutoDeployScanner.class);

	private final ESAAutoDeployDir _autoDeployDir;
	private boolean _started = true;

}