package org.eclipse.osgi.container;

import java.util.concurrent.Executor;
import org.apache.felix.resolver.Logger;
import org.apache.felix.resolver.ResolverImpl;
import org.eclipse.osgi.container.ModuleResolver.ResolveProcess.ResolveLogger;
import org.eclipse.osgi.container.resolver.Resolver2;
import org.osgi.service.resolver.Resolver;

class ResolverFactory {

	static Resolver create() {
		if (Boolean.getBoolean("resolver2")) {
			return new Resolver2();
		}
		return new ResolverImpl(new Logger(0), null);
	}

	public static Resolver create(ResolveLogger logger, Executor executor) {
		if (Boolean.getBoolean("resolver2")) {
			return new Resolver2();
		}
		return new ResolverImpl(logger, executor);
	}

}
