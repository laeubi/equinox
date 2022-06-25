/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package serviceclient1;

import java.util.Dictionary;
import java.util.Hashtable;
import org.osgi.framework.*;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

public class Activator implements BundleActivator {

	@Override
	public void start(final BundleContext context) throws Exception {
		new ServiceTracker<>(context, pkg2.a.A.class, new ServiceTrackerCustomizer<pkg2.a.A, pkg2.a.A>() {

			@Override
			public pkg2.a.A addingService(ServiceReference<pkg2.a.A> reference) {
				Dictionary<String, Object> props = new Hashtable<>();
				props.put("bundle.id", context.getBundle().getBundleId());
				context.registerService(Boolean.class, Boolean.TRUE, props);
				return null;
			}

			@Override
			public void modifiedService(ServiceReference<pkg2.a.A> reference, pkg2.a.A service) {
				// TODO Auto-generated method stub

			}

			@Override
			public void removedService(ServiceReference<pkg2.a.A> reference, pkg2.a.A service) {
				// TODO Auto-generated method stub

			}
		}).open();
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		// nothing
	}

}
