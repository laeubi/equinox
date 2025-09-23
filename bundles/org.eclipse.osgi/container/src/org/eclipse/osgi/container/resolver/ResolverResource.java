package org.eclipse.osgi.container.resolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.felix.resolver.Util;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.service.resolver.ResolveContext;

public class ResolverResource {

	private Resource resource;
	private boolean mandatory;
	private List<Requirement> requirements;
	private Map<Requirement, List<ResolverWire>> wireMap = new LinkedHashMap<>();

	public ResolverResource(Resource resource, ResolveContext context, boolean mandatory) {
		this.resource = resource;
		this.mandatory = mandatory;
		this.requirements = resource.getRequirements(null);
		for (Requirement requirement : requirements) {
			if (Util.isDynamic(requirement)) {
				continue;
			}
			List<Capability> providers = context.findProviders(requirement);
			List<ResolverWire> wires = new ArrayList<>(providers.size());
			for (Capability capability : providers) {
				wires.add(new ResolverWire(this, requirement, capability));
			}
			wireMap.put(requirement, wires);
		}
	}

	public Resource getResource() {
		return resource;
	}

	public boolean isMandatory() {
		return mandatory;
	}

	public List<Requirement> getRequirements() {
		return requirements;
	}

	public Stream<Wire> wires() {
		return wireMap.values().stream().flatMap(wl -> {
			Optional<ResolverWire> first = wl.stream().filter(rw -> rw.isSelectable()).findFirst();
			if (first.isPresent()) {
				return Stream.of(first.get());
			}
			return Stream.empty();
		});
	}

	public Map<Requirement, List<ResolverWire>> getMap() {
		return wireMap;
	}

	public Map<Requirement, ResolverWire> getSingletons() {
		return wireMap.entrySet().stream().filter(e -> isSingleton(e.getKey(), e.getValue()))
				.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().get(0)));

	}

	private boolean isSingleton(Requirement requirement, List<ResolverWire> value) {
		if (Util.isOptional(requirement)) {
			return false;
		}
		return value.size() == 1;
	}

	public boolean canResolve() {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public String toString() {
		return Util.getSymbolicName(resource) + " " + Util.getVersion(resource);
	}

}
