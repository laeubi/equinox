package org.eclipse.osgi.container.resolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.felix.resolver.ResolutionError;
import org.apache.felix.resolver.Util;
import org.eclipse.osgi.container.ModuleContainer;
import org.eclipse.osgi.container.resolver.check.Candidates;
import org.eclipse.osgi.container.resolver.check.PackageSpaces;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.resource.Wiring;
import org.osgi.service.resolver.ResolutionException;
import org.osgi.service.resolver.ResolveContext;
import org.osgi.service.resolver.Resolver;

public class Resolver2 implements Resolver {

	@Override
	public Map<Resource, List<Wire>> resolve(ResolveContext context) throws ResolutionException {
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
		List<ResolverResource> resources = new ArrayList<>();
		for (Resource resource : context.getMandatoryResources()) {
			resources.add(new ResolverResource(resource, context, true));
		}
		System.out.println("Optional Resources:" + context.getOptionalResources().size());
		for (Resource resource : context.getOptionalResources()) {
			resources.add(new ResolverResource(resource, context, false));
		}
		for (ResolverResource resolverResource : resources) {
//			resolveResource(resolverResource);
			map.put(resolverResource.getResource(), resolverResource.wires().collect(Collectors.toList()));
		}
		ResolutionError consistency = PackageSpaces.checkConsistency(new Candidates(map, resources), context);
		System.out.println("consistency=" + consistency);
		return map;
	}

	private void resolveResource(ResolverResource resolverResource) {
		System.out.println("== resolve " + ModuleContainer.toString(resolverResource.getResource()) + " ==");
		Map<Requirement, List<ResolverWire>> map = resolverResource.getMap();
		int total = 0;
		for (Map.Entry<Requirement, List<ResolverWire>> entry : map.entrySet()) {
			Requirement key = entry.getKey();
			List<ResolverWire> val = entry.getValue();
			int size = val.size();
			if (size > 1 && !Util.isOptional(key)) {
				if (total == 0) {
					total = size;
				} else {
					total *= size;
				}
				System.out.println("- must resolve " + ModuleContainer.toString(key) + " with " + size + " providers!");
				for (ResolverWire resolverWire : val) {
					checkUseConstrainViolation(resolverWire);
				}
			}
		}
		if (total > 0) {
			System.out.println("Exhaustive search requires " + total + " permutations!");
		}

	}

	private void checkUseConstrainViolation(ResolverWire resolverWire) {
		String packageName = resolverWire.getPackageName();
		if (packageName != null && !packageName.isEmpty()) {
			Map<Requirement, ResolverWire> singletons = resolverWire.getResource().getSingletons();
			for (Map.Entry<Requirement, ResolverWire> entry : singletons.entrySet()) {
				Requirement key = entry.getKey();
				ResolverWire val = entry.getValue();
				Set<String> uses = val.getUses();
				if (uses.contains(packageName) && !Objects.equals(val.getProvider(), resolverWire.getProvider())) {
					// TODO only invalid if we are a provider of the resolver wire requirement as
					// well!
					String reason = "Uses-constraint conflict with import '" + val.getPackageName() + " that is provided by "
							+ Util.toString(val.getProvider()) + " and no other alterative can be selected";
					System.out.println("\tdisable " + resolverWire + ": " + reason);
					resolverWire.setNotSelectable(
							reason);
					return;
				}
			}
		}
		System.out.println("\tcandidate: " + resolverWire);
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
