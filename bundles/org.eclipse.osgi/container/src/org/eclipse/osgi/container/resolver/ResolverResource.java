package org.eclipse.osgi.container.resolver;

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
	private Map<Requirement, Wires> wireMap = new LinkedHashMap<>();

	public ResolverResource(Resource resource, ResolveContext context, boolean mandatory) {
		this.resource = resource;
		this.mandatory = mandatory;
		this.requirements = resource.getRequirements(null);
		for (Requirement requirement : requirements) {
			if (Util.isDynamic(requirement)) {
				continue;
			}
			List<Capability> providers = context.findProviders(requirement);
			wireMap.put(requirement, new Wires(this, requirement, providers));
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

	public Map<Requirement, Wires> getMap() {
		return wireMap;
	}

	public Map<Requirement, ResolverWire> getSingletons() {

		return wireMap.entrySet().stream().filter(e -> e.getValue().isSingleton())
				.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().getSingelton().get()));

	}

	public boolean canResolve() {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public String toString() {
		return Util.getSymbolicName(resource) + " " + Util.getVersion(resource);
	}

	public Capability getFirstCandidate(Requirement req) {
		Wires list = wireMap.get(req);
		if (list != null) {
			for (ResolverWire resolverWire : list) {
				if (resolverWire.isSelectable()) {
					return resolverWire.getCapability();
				}
			}
		}
		return null;
	}

	public List<Capability> getCandidates(Requirement req) {
		Wires list = wireMap.get(req);
		if (list != null) {
			return list.stream().filter(rw -> rw.isSelectable()).map(rw -> rw.getCapability())
					.collect(Collectors.toList());
		}
		return null;
	}

	public int countUniqueSelected() {
		int unique = 0;
		for (Wires wires : wireMap.values()) {
			if (wires.size() == 1 || wires.stream().filter(rw -> rw.getReason() == null).count() == 1) {
				unique++;
			}
		}
		return unique;
	}

}
