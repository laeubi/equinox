package org.eclipse.osgi.container.resolver;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.felix.resolver.Util;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

public class Wires implements Iterable<ResolverWire> {
	private final List<ResolverWire> wires;
	private Requirement requirement;
	private boolean substitution;

	public Wires(ResolverResource resource, Requirement requirement, List<Capability> providers) {
		this.requirement = requirement;
		wires = new ArrayList<>(providers.size());
		for (Capability capability : providers) {
			wires.add(new ResolverWire(resource, requirement, capability));
		}
		this.substitution = providers.stream().anyMatch(c -> Util.isSubstitutionPackage(requirement, c));
	}

	public boolean isSubstitution() {
		return substitution;
	}

	public Stream<ResolverWire> stream() {
		return wires.stream();
	}

	public int size() {
		return wires.size();
	}

	public boolean isSingleton() {
		if (Util.isOptional(requirement)) {
			return false;
		}
		return getSelectableWires() == 1;
	}

	public boolean providesCandidate(Resource provider) {
		for (ResolverWire wire : wires) {
			if (Objects.equals(wire.getProvider(), provider)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Iterator<ResolverWire> iterator() {
		return wires.iterator();
	}

	public int getSelectableWires() {
		int cnt = 0;
		for (ResolverWire wire : wires) {
			if (wire.isSelectable()) {
				cnt++;
			}
		}
		return cnt;
	}

	public Optional<ResolverWire> getSingelton() {
		ResolverWire found = null;
		for (ResolverWire wire : wires) {
			if (wire.isSelectable()) {
				if (found == null) {
					found = wire;
					return Optional.of(wire);
				} else {
					return Optional.empty();
				}
			}
		}
		return Optional.ofNullable(found);
	}

}
