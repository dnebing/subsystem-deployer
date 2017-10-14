package com.liferay.subsystem.deployer.internal;

import com.liferay.portal.deploy.DeployUtil;
import com.liferay.portal.kernel.deploy.auto.AutoDeployListener;
import com.liferay.portal.kernel.deploy.auto.AutoDeployUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.*;
import com.liferay.portal.util.PrefsPropsUtil;
import com.liferay.portal.util.PropsValues;
import org.apache.felix.fileinstall.ArtifactInstaller;

import org.osgi.framework.*;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.subsystem.Subsystem;

import java.io.File;
import java.net.MalformedURLException;
import java.util.*;

/**
 * class ESAArtifactInstaller: This is an extension for the Felix FileInstall service that will
 * handle the installation of Subsystem .esa files.
 *
 * @author dnebinger
 */
@Component(
	immediate = true,
	service = ArtifactInstaller.class
)
public class ESAArtifactInstaller implements ArtifactInstaller {

	public static final String DEFAULT_NAME = "esaAutoDeployDir";

	/**
	 * deactivate: Called when the module is being stopped.
	 */
	@Deactivate
	public void deactivate() {
		if (_log.isDebugEnabled()) {
			_log.debug("Deactivating the ESAArtifactInstaller and corresponding scanner.");
		}

		// unregister our custom AutoDeployDir instance
		AutoDeployUtil.unregisterDir(DEFAULT_NAME);
	}

	/**
	 * activate: Called when the module is being started.
	 * @param bundleContext
	 */
	@Activate
	public void activate(BundleContext bundleContext) {
		_bundleContext = bundleContext;

		if (_log.isDebugEnabled()) {
			if (Validator.isNull(_rootSubsystem)) {
				_log.debug("Missing root subsystem.");
			} else {
				_log.debug("Have root subsystem with id " + _rootSubsystem.getSubsystemId() + " and name [" + _rootSubsystem.getSymbolicName() + "].");
			}
		}

		// since we're activating, let's also activate the scanner.
		try {
			// find the deploy dir that we're going to scan
			File deployDir = new File(
					PrefsPropsUtil.getString(
							PropsKeys.AUTO_DEPLOY_DEPLOY_DIR,
							PropsValues.AUTO_DEPLOY_DEPLOY_DIR));
			// find the default dest dir
			File destDir = new File(DeployUtil.getAutoDeployDestDir());

			// get the interval for directory scans.
			long interval = PrefsPropsUtil.getLong(
					PropsKeys.AUTO_DEPLOY_INTERVAL,
					PropsValues.AUTO_DEPLOY_INTERVAL);

			// create a list of autodeploylisteners, will only have our ESAAutoDeployListener instance.
			List<AutoDeployListener> autoDeployListeners =
					getAutoDeployListeners(false);

			// create our deploy dir manager
			ESAAutoDeployDir autoDeployDir = new ESAAutoDeployDir(
					DEFAULT_NAME, deployDir, destDir, interval,
					autoDeployListeners);

			// if auto deploy is enabled
			if (PrefsPropsUtil.getBoolean(
					PropsKeys.AUTO_DEPLOY_ENABLED,
					PropsValues.AUTO_DEPLOY_ENABLED)) {

				if (_log.isInfoEnabled()) {
					_log.info("Registering auto deploy directories");
				}

				// register the scanner
				AutoDeployUtil.registerDir(autoDeployDir);
			}
			else {
				if (_log.isInfoEnabled()) {
					_log.info("Not registering auto deploy directories");
				}
			}
		}
		catch (Exception e) {
			_log.error("Unable to register auto deploy directories", e);
		}
	}

	public static List<AutoDeployListener> getAutoDeployListeners(
			boolean reset) {

		if ((_autoDeployListeners != null) && !reset) {
			return _autoDeployListeners;
		}

		List<AutoDeployListener> autoDeployListeners = new ArrayList<>();

		String autoDeployListenerClassName = ESAAutoDeployListener.class.getName();

		try {
			if (_log.isDebugEnabled()) {
				_log.debug("Instantiating " + autoDeployListenerClassName);
			}

			AutoDeployListener autoDeployListener =
					(AutoDeployListener) InstanceFactory.newInstance(
							autoDeployListenerClassName);

			autoDeployListeners.add(autoDeployListener);
		}
		catch (Exception e) {
			_log.error("Unable to initialiaze auto deploy listener", e);
		}

		_autoDeployListeners = autoDeployListeners;

		return _autoDeployListeners;
	}

	private static List<AutoDeployListener> _autoDeployListeners;

	@Override
	public boolean canHandle(File file) {
		String name = StringUtil.toLowerCase(file.getName());

		return name.endsWith(".esa");
	}

	/**
	 * install: Called by FileInstall to handle a specific install.
	 * @param file
	 * @throws Exception
	 */
	public void install(File file) throws Exception {
		String location = getLocation(file);

		if (_log.isDebugEnabled()) {
			_log.debug("About to install subsystem from [" + location + "].");
		}

		// have the root subsystem install the given subsystem
		Subsystem installed = getRootSubsystem().install(location);

		if (Validator.isNotNull(installed)) {
			// auto-start the subsystem if it is installed only.
			switch (installed.getState()) {
				case ACTIVE:
				case STARTING:
					if (_log.isDebugEnabled()) {
						_log.debug("Subsystem is already starting...");
					}
					break;
				case INSTALLED:
					if (_log.isDebugEnabled()) {
						_log.debug("Subsystem installed, starting it...");
					}

					installed.start();
					break;
				default:
					_log.warn("Subsystem installed but is in state [" + installed.getState() + "], unable to start.");
					break;
			}
		}
	}

	/**
	 * update: Called by FileInstall when the file artifact was changed (i.e. a new version deployed).  Unfortunately
	 * the Subsystem spec does not support handling updates.  This actually makes sense; can you imagine how hard it
	 * would be to selectively pick out bundles or subsystems, determine if they have changed, and update them individually?
	 *
	 * Instead, we're just going to uninstall the file as it currently is and will do a regular install again.
	 *
	 * @param file
	 * @throws Exception
	 */
	public void update(File file) throws Exception {
		// The subsystem specification does not cover updates.
		// I get it, it would be a hard problem to solve and prone to lots of issues.

		// To support the update, therefore, we're going to uninstall and then install.

		if (_log.isDebugEnabled()) {
			_log.debug("Simulating update via uninstall followed by install, uninstall starting now...");
		}

		uninstall(file);

		if (_log.isDebugEnabled()) {
			_log.debug("Uninstall successful, moving on to install...");
		}

		install(file);

		if (_log.isDebugEnabled()) {
			_log.debug("Update simulation successful.");
		}
	}

	/**
	 * uninstall: Called when the .esa file is being uninstalled.
	 * @param file
	 * @throws Exception
	 */
	public void uninstall(File file) throws Exception {
		String location = getLocation(file);

		if (_log.isDebugEnabled()) {
			_log.debug("About to uninstall subsystem from [" + location + "].");
		}

		Subsystem current = getSubsystem(location);

		if (Validator.isNotNull(current)) {
			if (current.getState() == Subsystem.State.ACTIVE) {
				if (_log.isDebugEnabled()) {
					_log.debug("Subsystem is currently started, need to stop it first.");
				}

				current.stop();
			}

			current.uninstall();
		}
	}

	protected Subsystem getSubsystem(String location) {
		for (Subsystem s : getRootSubsystem().getChildren()) {
			if (s.getLocation().equals(location)) {
				return s;
			}
		}
		return null;
	}

	protected String getLocation(File file) throws MalformedURLException {
		if (file.isDirectory()) {
			return "jardir:" + file.getPath();
		} else {
			return file.toURI().toURL().toExternalForm();
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
			ESAArtifactInstaller.class);

	private BundleContext _bundleContext;

	private Subsystem _rootSubsystem;

	protected Subsystem getRootSubsystem() {
		return _rootSubsystem;
	}

	@Reference(target = "(subsystem.symbolicName=org.osgi.service.subsystem.root)")
	protected void setRootSubsystem(Subsystem subsystem) {
		_rootSubsystem = subsystem;
	}

}