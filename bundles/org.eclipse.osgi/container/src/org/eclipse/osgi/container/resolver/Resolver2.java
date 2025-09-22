package org.eclipse.osgi.container.resolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.felix.resolver.Util;
import org.eclipse.osgi.container.ModuleContainer;
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
		System.out.println("Mandatory Resources:");
		Map<Resource, List<Wire>> map = new HashMap<>();
		for (Resource resource : context.getMandatoryResources()) {
			System.out.println("\t" + ModuleContainer.toString(resource));
			resolveResource(resource, context, map);
		}
		System.out.println("Optional Resources:");
		for (Resource resource : context.getOptionalResources()) {
			System.out.println("\t" + ModuleContainer.toString(resource));
			resolveResource(resource, context, map);
		}
		return map;
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

			list
					.add(new WireImpl(resource, requirement, capability.getResource(), capability));
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
