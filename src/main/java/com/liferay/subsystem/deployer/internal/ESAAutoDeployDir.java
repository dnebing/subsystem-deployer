package com.liferay.subsystem.deployer.internal;

import com.liferay.portal.kernel.deploy.auto.AutoDeployDir;
import com.liferay.portal.kernel.deploy.auto.AutoDeployException;
import com.liferay.portal.kernel.deploy.auto.AutoDeployListener;
import com.liferay.portal.kernel.deploy.auto.AutoDeployScanner;
import com.liferay.portal.kernel.deploy.auto.context.AutoDeploymentContext;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.*;
import com.liferay.registry.Registry;
import com.liferay.registry.RegistryUtil;
import com.liferay.registry.ServiceTracker;

import java.io.File;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author dnebinger
 */
public class ESAAutoDeployDir extends AutoDeployDir {

	public static final String DEFAULT_NAME = "defaultAutoDeployDir";

	public void deployContext(AutoDeploymentContext autoDeploymentContext)
		throws AutoDeployException {

		String[] dirNames = PropsUtil.getArray(
			PropsKeys.MODULE_FRAMEWORK_AUTO_DEPLOY_DIRS);

		if (ArrayUtil.isEmpty(dirNames)) {
			throw new AutoDeployException(
				"The portal property \"" +
					PropsKeys.MODULE_FRAMEWORK_AUTO_DEPLOY_DIRS +
						"\" is not set");
		}

		String dirName = dirNames[0];

		File file = autoDeploymentContext.getFile();

		String fileName = file.getName();

		for (String curDirName : dirNames) {
			if (curDirName.endsWith("/modules")) {
				dirName = curDirName;

				break;
			}
		}

		FileUtil.move(file, new File(dirName, fileName));
	}

	public ESAAutoDeployDir(
		String name, File deployDir, File destDir, long interval,
		List<AutoDeployListener> autoDeployListeners) {

		super(name, deployDir, destDir, interval, autoDeployListeners);

		_blacklistFileTimestamps = new HashMap<>();

	}

	protected void processFile(File file) {
		String fileName = file.getName();

		if (!file.canRead()) {
			_log.error("Unable to read " + fileName);

			return;
		}

		if (!file.canWrite()) {
			_log.error("Unable to write " + fileName);

			return;
		}

		if (_blacklistFileTimestamps.containsKey(fileName) &&
				(_blacklistFileTimestamps.get(fileName) == file.lastModified())) {

			if (_log.isDebugEnabled()) {
				_log.debug(
						"Skip processing of " + fileName + " because it is " +
								"blacklisted");
			}

			return;
		}

		if (_log.isInfoEnabled()) {
			_log.info("Processing " + fileName);
		}

		try {
			AutoDeploymentContext autoDeploymentContext =
					buildAutoDeploymentContext(file);

			deployContext(autoDeploymentContext);

			return;
		}
		catch (Exception e) {
			_log.error(e, e);
		}

		if (_log.isInfoEnabled()) {
			_log.info("Add " + fileName + " to the blacklist");
		}

		_blacklistFileTimestamps.put(fileName, file.lastModified());
	}

	public void start() {
		if (!getDeployDir().exists()) {
			if (_log.isInfoEnabled()) {
				_log.info("Creating missing directory " + getDeployDir());
			}

			boolean created = getDeployDir().mkdirs();

			if (!created) {
				_log.error("Directory " + getDeployDir() + " could not be created");
			}
		}

		if ((getInterval() > 0) &&
			((_autoDeployScanner == null) || !_autoDeployScanner.isAlive())) {

			try {
				scanDirectory();

				Thread currentThread = Thread.currentThread();

				_autoDeployScanner = new ESAAutoDeployScanner(
					currentThread.getThreadGroup(),
					AutoDeployScanner.class.getName(), this);

				_autoDeployScanner.start();

				if (_log.isInfoEnabled()) {
					_log.info("Auto deploy scanner started for " + getDeployDir());
				}
			}
			catch (Exception e) {
				_log.error(e, e);

				stop();

				return;
			}
		}
		else {
			if (_log.isInfoEnabled()) {
				_log.info("Auto deploy scanning is disabled for " + getDeployDir());
			}
		}
	}

	protected void scanDirectory() {
		File[] files = getDeployDir().listFiles();

		if (files == null) {
			return;
		}

		Set<String> blacklistedFileNames = _blacklistFileTimestamps.keySet();

		Iterator<String> iterator = blacklistedFileNames.iterator();

		while (iterator.hasNext()) {
			String blacklistedFileName = iterator.next();

			boolean blacklistedFileExists = false;

			for (File file : files) {
				if (StringUtil.equalsIgnoreCase(
						blacklistedFileName, file.getName())) {

					blacklistedFileExists = true;
				}
			}

			if (!blacklistedFileExists) {
				if (_log.isDebugEnabled()) {
					_log.debug(
						"Remove blacklisted file " + blacklistedFileName +
							" because it was deleted");
				}

				iterator.remove();
			}
		}

		for (File file : files) {
			String fileName = file.getName();

			fileName = StringUtil.toLowerCase(fileName);

			if (file.isFile() && (fileName.endsWith(".esa"))) {
				processFile(file);
			}
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(ESAAutoDeployDir.class);

	private static ESAAutoDeployScanner _autoDeployScanner;
	private final Map<String, Long> _blacklistFileTimestamps;

}