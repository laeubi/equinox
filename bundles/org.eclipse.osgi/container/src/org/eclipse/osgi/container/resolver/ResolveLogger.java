package org.eclipse.osgi.container.resolver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.apache.felix.resolver.Util;
import org.eclipse.osgi.container.ModuleContainer;
import org.eclipse.osgi.container.resolver.check.UseConstraintError;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

public class ResolveLogger implements AutoCloseable {

	private Map<String, PrintWriter> writers = new ConcurrentHashMap<>();
	private File logDir;

	public ResolveLogger(File logDir) {
		this.logDir = logDir;
		System.out.println("Logging to directory " + logDir);
		logDir.mkdirs();
	}

	public void log(Resource resource, String string) {
		getWriter(resource).println(string);
	}

	private PrintWriter getWriter(Resource resource) {
		String key = Util.getSymbolicName(resource) + "_" + Util.getVersion(resource) + ".log";
		return writers.computeIfAbsent(key, fn -> {
			try {
				return new PrintWriter(new File(logDir, fn));
			} catch (FileNotFoundException e) {
				return new PrintWriter(new StringWriter());
			}
		});

	}

	@Override
	public void close() {
		writers.values().forEach(pw -> pw.close());
	}

	public void log(ResolverResource resolverResource, String string) {
		log(resolverResource.getResource(), string);
	}

	public void dump(ResolverResource resolverResource) {
		PrintWriter pw = getWriter(resolverResource.getResource());
		Map<Requirement, Wires> map = resolverResource.getMap();
		for (Entry<Requirement, Wires> entry : map.entrySet()) {
			Wires wires = entry.getValue();
			pw.print("[" + wires.size() + "]");
			if (wires.isSubstitution()) {
				pw.print("[S]");
			}
			pw.print(" ");
			pw.print(ModuleContainer.toString(entry.getKey()));
			if (Util.isOptional(entry.getKey())) {
				pw.print(" (optional)");
			}
			pw.println(":");
			wires.wires().forEach(new Consumer<ResolverWire>() {
				int prio = 1;

				@Override
				public void accept(ResolverWire resolverWire) {
					pw.print("\t");
					String reason = resolverWire.getReason();
					if (reason == null) {
						pw.print("[" + prio + "] ");
						prio++;
					} else {
						pw.print("[X] ");
					}
					pw.print(ModuleContainer.toString(resolverWire.getCapability()));
					if (reason == null) {
						pw.println();
					} else {
						pw.print(" - ");
						pw.println(reason);
					}
				}
			});
		}
		pw.println();
	}

	public void log(UseConstraintError error) {
		PrintWriter pw = getWriter(error.getResource());
		pw.print("-- UseConstraintError on ");
		error.getUnresolvedRequirements().forEach(r -> pw.print(ModuleContainer.toString(r)));
		pw.println(" ---");
		pw.println(error);

	}

}
