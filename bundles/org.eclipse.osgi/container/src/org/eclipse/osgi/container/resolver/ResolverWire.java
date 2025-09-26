package org.eclipse.osgi.container.resolver;

import java.util.Collections;
import java.util.Set;
import org.apache.felix.resolver.Util;
import org.eclipse.osgi.container.ModuleContainer;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;

public class ResolverWire implements Wire {

	private final Requirement requirement;
	private final Capability capability;
	private final ResolverResource resource;
	private final boolean isPackage;
	private final Set<String> uses;
	private final String packageName;
	private String ignoreReason;

	public ResolverWire(ResolverResource resource, Requirement requirement, Capability capability) {
		this.resource = resource;
		this.requirement = requirement;
		this.capability = capability;
		this.isPackage = Util.isExportedPackage(capability);
		if (this.isPackage) {
			uses = Util.getUses(capability);
			packageName = Util.getPackageName(capability);
		} else {
			uses = Collections.emptySet();
			packageName = null;
		}
	}

	public ResolverResource getResource() {
		return resource;
	}

	@Override
	public Capability getCapability() {
		return capability;
	}

	@Override
	public Requirement getRequirement() {
		return requirement;
	}

	public boolean isPackage() {
		return isPackage;
	}

	@Override
	public Resource getProvider() {
		return getCapability().getResource();
	}

	@Override
	public Resource getRequirer() {
		return getRequirement().getResource();
	}

	public boolean isSelectable() {
		return ignoreReason == null && resource.canResolve();
	}

	public String getPackageName() {
		return packageName;
	}

	public Set<String> getUses() {
		return uses;
	}

	public void setNotSelectable(String reason) {
		this.ignoreReason = reason;
	}

	public String getReason() {
		if (ignoreReason != null) {
			return ignoreReason;
		}
		if (!resource.canResolve()) {
			return "The resource " + resource + " can't resolve";
		}
		return null;
	}

	@Override
	public String toString() {
		return ModuleContainer.toString(requirement) + " --> " + ModuleContainer.toString(capability);
	}

	public boolean isOptional() {
		return Util.isOptional(requirement);
	}

}
