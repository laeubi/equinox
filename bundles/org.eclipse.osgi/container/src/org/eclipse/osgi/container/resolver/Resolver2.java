package org.eclipse.osgi.container.resolver;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.felix.resolver.ResolutionError;
import org.apache.felix.resolver.Util;
import org.eclipse.osgi.container.ModuleContainer;
import org.eclipse.osgi.container.resolver.check.Candidates;
import org.eclipse.osgi.container.resolver.check.PackageSpaces;
import org.eclipse.osgi.container.resolver.check.UseConstraintError;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.resource.Wiring;
import org.osgi.service.resolver.ResolutionException;
import org.osgi.service.resolver.ResolveContext;
import org.osgi.service.resolver.Resolver;

public class Resolver2 implements Resolver {

	private static int index;

	@Override
	public Map<Resource, List<Wire>> resolve(ResolveContext context) throws ResolutionException {
		File logDir = new File("/tmp/resolver2/" + (index++));

		try (ResolveLogger logger = new ResolveLogger(logDir)) {
			Map<Resource, Wiring> wirings = context.getWirings();
			System.out.println("===== resolver2.resolve() =====");
			System.out.println("Wirings:");
			for (Map.Entry<Resource, Wiring> entry : wirings.entrySet()) {
				Resource key = entry.getKey();
				Wiring val = entry.getValue();
				System.out.println(ModuleContainer.toString(key));
			}
			System.out.println("Mandatory Resources: " + context.getMandatoryResources().size());
			Map<Resource, List<Wire>> map = new HashMap<>();
			Map<Resource, ResolverResource> resources = new LinkedHashMap<>();
			for (Resource resource : context.getMandatoryResources()) {
				resources.put(resource, new ResolverResource(resource, context, true));
				logger.log(resource, "Mandatory Resource " + ModuleContainer.toString(resource));
			}
			System.out.println("Optional Resources:" + context.getOptionalResources().size());
			for (Resource resource : context.getOptionalResources()) {
				resources.put(resource, new ResolverResource(resource, context, false));
				logger.log(resource, "Optional Resource " + ModuleContainer.toString(resource));
			}
			for (ResolverResource resolverResource : resources.values()) {
				logger.log(resolverResource, "--- Initial State ---");
				logger.dump(resolverResource);
				resolveResource(resolverResource, logger);
				logger.log(resolverResource, "--- Processed State ---");
				logger.dump(resolverResource);
				map.put(resolverResource.getResource(), resolverResource.wires().collect(Collectors.toList()));
			}
			List<ResolutionError> consistencyErrors = PackageSpaces.checkConsistency(new Candidates(resources),
					context);
			if (!consistencyErrors.isEmpty()) {
				System.out.println("!!!! there are " + consistencyErrors.size() + " use constrain violations !!!!");
				Set<Resource> faulty = new HashSet<>();
				for (ResolutionError resolutionError : consistencyErrors) {
					if (resolutionError instanceof UseConstraintError) {
						UseConstraintError error = (UseConstraintError) resolutionError;
						faulty.add(error.getResource());
						logger.log(error);
					}
				}
				System.out.println("Affected Resources (" + faulty.size() + "):");
				faulty.stream().map(r -> ModuleContainer.toString(r)).sorted(String.CASE_INSENSITIVE_ORDER)
						.forEach(System.out::println);
			}
			return map;
		}
	}

	private void resolveResource(ResolverResource resolverResource, ResolveLogger logger) {
		logger.log(resolverResource, "== resolve " + ModuleContainer.toString(resolverResource.getResource()) + " ==");
		Map<Requirement, Wires> map = resolverResource.getMap();
		int total = 0;
		long before = resolverResource.countUniqueSelected();
		boolean rerun;
		do {
			rerun = false;
			Map<Requirement, ResolverWire> singletons = resolverResource.getSingletons();
			if (singletons.isEmpty()) {
				break;
			}
			logger.log(resolverResource, "Filtering use constrains with " + singletons.size() + " singeltons...");
			for (Entry<Requirement, Wires> entry : map.entrySet()) {
				Requirement key = entry.getKey();
				Wires wirecandidates = entry.getValue();
				int size = wirecandidates.getSelectableWires();
				if (size > 1 && !Util.isOptional(key)) {
					if (total == 0) {
						total = size;
					} else {
						total *= size;
					}
					logger.log(resolverResource,
							"- must resolve " + ModuleContainer.toString(key) + " with " + size + " providers!");
					for (ResolverWire resolverWire : wirecandidates) {
						checkUseConstrainViolation(resolverWire, wirecandidates, singletons, logger);
					}
					boolean singleton = wirecandidates.isSingleton();
					if (singleton) {
						logger.log(resolverResource, "-> is now a singelton!");
					}
					rerun |= singleton;
				}
			}
			logger.log(resolverResource, "Rerun needed: " + rerun);
		} while (rerun);
		if (total > 0) {
			logger.log(resolverResource, "Exhaustive search requires " + total + " permutations!");
		}
		long after = resolverResource.countUniqueSelected();
		if (before != after) {
			logger.log(resolverResource, (after - before) + " more are now unique selected");
		}
	}

	private void checkUseConstrainViolation(ResolverWire resolverWire, Wires wirecandidates,
			Map<Requirement, ResolverWire> singletons, ResolveLogger logger) {
		String packageName = resolverWire.getPackageName();
		ResolverResource resource = resolverWire.getResource();
		if (packageName != null && !packageName.isEmpty()) {
			for (Map.Entry<Requirement, ResolverWire> entry : singletons.entrySet()) {
				ResolverWire singletonWire = entry.getValue();
				Set<String> uses = singletonWire.getUses();
				Resource provider = singletonWire.getProvider();
				if (uses.contains(packageName) && !Objects.equals(provider, resolverWire.getProvider())
						&& wirecandidates.providesCandidate(provider)) {
					String reason = "Uses-constraint conflict with import '" + singletonWire.getPackageName()
							+ " that is provided by " + Util.toString(provider)
							+ " and no other alternative can be selected because it is also a provider for this package";
					logger.log(resource, "\tdisable " + resolverWire + ": " + reason);
					resolverWire.setNotSelectable(reason);
					// TODO actually only if it becomes a singelton
					return;
				}
			}
		}
		logger.log(resource, "\tcandidate: " + resolverWire);
		return;
	}

	private void resolveResource(Resource resource, ResolveContext context, Map<Resource, List<Wire>> map) {
		List<Wire> list = map.computeIfAbsent(resource, nil -> new ArrayList<>());
		List<Requirement> requirements = resource.getRequirements(null);
		System.out.println("\tRequires:");
		for (Requirement requirement : requirements) {
			if (Util.isDynamic(requirement)) {
				continue;
			}
			System.out.println("\t\t" + ModuleContainer.toString(requirement));
			List<Capability> providers = context.findProviders(requirement);
			for (Capability capability : providers) {
				System.out.println("\t\t\t" + ModuleContainer.toString(capability) + " ["
						+ ModuleContainer.toString(capability.getResource()) + "]");
			}
			if (providers.isEmpty()) {
				if (Util.isOptional(requirement)) {
					continue;
				} else {
					System.out.println("can't resolve!");
				}
			}
			Capability capability = providers.get(0);

			list.add(new WireImpl(resource, requirement, capability.getResource(), capability));
		}

	}

	@Override
	public Map<Resource, List<Wire>> resolveDynamic(ResolveContext context, Wiring hostWiring,
			Requirement dynamicRequirement) throws ResolutionException {
		System.out.println("Resolver2.resolveDynamic()");
		// TODO Auto-generated method stub
		return new HashMap<>();
	}

}
