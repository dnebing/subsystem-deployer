package com.liferay.subsystem.deployer.internal;

import com.liferay.portal.kernel.concurrent.DefaultNoticeableFuture;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import org.apache.felix.fileinstall.ArtifactInstaller;

import org.osgi.framework.*;
import org.osgi.framework.wiring.FrameworkWiring;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.subsystem.Subsystem;
import org.osgi.service.url.URLConstants;
import org.osgi.service.url.URLStreamHandlerService;

import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author dnebinger
 */
@Component(
	immediate = true,
	property = {
		// TODO enter required service properties
	},
	service = ArtifactInstaller.class
)
public class ESAArtifactInstaller implements ArtifactInstaller {

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
	}

	@Override
	public boolean canHandle(File file) {
		String name = StringUtil.toLowerCase(file.getName());

		return name.endsWith(".esa");
	}

	public void install(File file) throws Exception {
		_rootSubsystem.install(getLocation(file));
	}

	public void update(File file) throws Exception {
		String location = getLocation(file);
		getSubsystem(location).update();
	}

	public void uninstall(File file) throws Exception {
		getSubsystem(getLocation(file)).uninstall();
	}

	protected Subsystem getSubsystem(String location) {
		for (Subsystem s : _rootSubsystem.getChildren()) {
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

	private Subsystem getSubsystem(long id) {
		try {
			for (ServiceReference<Subsystem> ref :
					_bundleContext.getServiceReferences(Subsystem.class, "(subsystem.id=" + id + ")")) {
				Subsystem svc = _bundleContext.getService(ref);
				if (svc != null)
					return svc;
			}
		} catch (InvalidSyntaxException e) {
			throw new RuntimeException(e);
		}
		throw new RuntimeException("Unable to find subsystem " + id);
	}

	private static final Log _log = LogFactoryUtil.getLog(
			ESAArtifactInstaller.class);

	private BundleContext _bundleContext;

	private Subsystem _rootSubsystem;

	public Subsystem getRootSubsystem() {
		return _rootSubsystem;
	}

	@Reference(target = "(subsystem.symbolicName=org.osgi.service.subsystem.root)")
	public void setRootSubsystem(Subsystem subsystem) {
		_rootSubsystem = subsystem;
	}

}