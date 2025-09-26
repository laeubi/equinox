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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.felix.resolver.ResolutionError;
import org.apache.felix.resolver.Util;
import org.eclipse.osgi.container.ModuleContainer;
import org.eclipse.osgi.container.resolver.check.Blame;
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
			boolean restart;
			int round = 0;
			do {
				round++;
				restart = false;
				for (ResolverResource resolverResource : resources.values()) {
					logger.log(resolverResource, "--- Initial State [Round " + round + "] ---");
					logger.dump(resolverResource);
					resolveUseConstrainViolations(resolverResource, logger);
					logger.log(resolverResource, "--- Processed State [Round " + round + "] ---");
					logger.dump(resolverResource);
				}
				List<ResolutionError> consistencyErrors = PackageSpaces.checkConsistency(new Candidates(resources),
						context);
				if (!consistencyErrors.isEmpty()) {
					System.out.println("Round " + round + " !!!! there are " + consistencyErrors.size()
							+ " use constrain violations !!!!");
					Set<Resource> faulty = new HashSet<>();
					for (ResolutionError resolutionError : consistencyErrors) {
						if (resolutionError instanceof UseConstraintError) {
							UseConstraintError error = (UseConstraintError) resolutionError;
							faulty.add(error.getResource());
							logger.log(error);
							Blame ourBlame = error.getOurBlame();
							logger.log(error.getResource(), "-- our blame here is:");
							logger.log(error.getResource(), ModuleContainer.toString(ourBlame.m_cap));
							for (Requirement req : ourBlame.m_reqs) {
								logger.log(error.getResource(), ModuleContainer.toString(req));
							}
							Blame otherBlame = error.getOtherBlame();
							if (otherBlame != null) {
								logger.log(error.getResource(), "-- Another resource is to blame here:");
								logger.log(error.getResource(), ModuleContainer.toString(otherBlame.m_cap));
								for (Requirement req : otherBlame.m_reqs) {
									logger.log(error.getResource(), ModuleContainer.toString(req));
								}
								if (resolveBlame(ourBlame, otherBlame, resources, logger)) {
									restart = true;
								}
							}
						}
					}
					System.out.println("Affected Resources (" + faulty.size() + "):");
					faulty.stream().map(r -> ModuleContainer.toString(r)).sorted(String.CASE_INSENSITIVE_ORDER)
							.forEach(System.out::println);
				}
			} while (restart);
			Map<Resource, List<Wire>> map = new HashMap<>();
			for (ResolverResource resolverResource : resources.values()) {
				map.put(resolverResource.getResource(), resolverResource.wires().collect(Collectors.toList()));
			}
			return map;
		}
	}

	private boolean resolveBlame(Blame ourBlame, Blame otherBlame, Map<Resource, ResolverResource> resources,
			ResolveLogger logger) {
		if (ourBlame.m_reqs.size() == 1) {
			Requirement source = ourBlame.m_reqs.get(0);
			if (Util.isOptional(source)) {
				// TODO we might want to treat this differently e.g. an optional requirement
				// might simply be left out?
				return false;
			}
			Resource resource = source.getResource();
			ResolverResource resolverResource = resources.get(resource);
			if (resolverResource == null) {
				return false;
			}
			Optional<ResolverWire> singleton = resolverResource.getSingleton(source);
			if (!singleton.isPresent()) {
				// if it is not a singleton we should handle it differently
				return false;
			}
			for (Requirement other : otherBlame.m_reqs) {
				Resource otherResource = other.getResource();
				if (!Objects.equals(otherResource, resource)) {
					// we found it?!?
					logger.log(resolverResource, "Try to modify " + ModuleContainer.toString(otherResource)
							+ " requirement " + ModuleContainer.toString(other) + "...");
					ResolverResource otherResolverResource = resources.get(otherResource);
					if (otherResolverResource == null) {
						return false; // Should this ever happen maybe with dynamic resolve???
					}
					Optional<Wires> selectableWires = otherResolverResource.getSelectableWires(other);
					if (selectableWires.isPresent() && selectableWires.get().size() > 1) {
						Wires wires = selectableWires.get();
						Resource provider = ourBlame.m_cap.getResource();
						if (wires.providesCandidate(provider)) {
							for (ResolverWire resolverWire : wires) {
								if (!resolverWire.providedBy(provider)) {
									logger.log(resolverResource,
											"Disable provider " + ModuleContainer.toString(resolverWire.getCapability())
													+ " for this requirement");
									// need to disable
									String reason = "conflicts with use of " + ModuleContainer.toString(source) + " from "
											+ ModuleContainer.toString(resource);
									logger.log(otherResolverResource, "Disable provider "
											+ ModuleContainer.toString(resolverWire.getCapability())
											+ " for requirement " + ModuleContainer.toString(other) + " because it "
											+ reason);
									resolverWire.setNotSelectable(
											reason);
									if (wires.isSingleton()) {
										// if it is now a singleton we should resolve again to disable even more
										// things...
										resolveUseConstrainViolations(otherResolverResource, logger);
									}
									return true;
								}
							}
						} else {
							logger.log(resolverResource,
									"... not possible, our resource is not a provider of the alternatives!");
						}
					} else {
						// TODO actually if other is an optional requirement it would be possible to
						// disable the import!
						logger.log(resolverResource, "... not possible, no alternatives!");
					}
				}
			}
		}
		return false;
	}

	private void resolveUseConstrainViolations(ResolverResource resolverResource, ResolveLogger logger) {
		logger.log(resolverResource, "== resolve " + ModuleContainer.toString(resolverResource.getResource()) + " ==");
		Map<Requirement, Wires> map = resolverResource.getMap();
		long before = resolverResource.countUniqueSelected();
		boolean rerun;
		long start = System.currentTimeMillis();
		do {
			logger.log(resolverResource, "> Check forward constrains <");
			rerun = checkForwardUseConstrainViolations(resolverResource, logger, map);
			logger.log(resolverResource, "> Check backward constrains <");
			rerun |= checkBackwardUseConstrainViolations(resolverResource, logger, map);
			if (rerun) {
				logger.log(resolverResource, "> rerun needed <");
			} else {
				logger.log(resolverResource, "> completed after " + (System.currentTimeMillis() - start) + "ms <");
			}
		} while (rerun);

		long after = resolverResource.countUniqueSelected();
		if (before != after) {
			logger.log(resolverResource, (after - before) + " more are now unique selected");
		}
	}

	/**
	 * Checks for "backward" use constrain violation, that is for each requirement
	 * that has more than one selectable wire we check if any of this has a use
	 * constrain and if this is the case check if we also import / use this package
	 * in any of the singelton selected packages
	 * 
	 * @param resolverResource
	 * @param logger
	 * @param map
	 * @return
	 */
	private boolean checkBackwardUseConstrainViolations(ResolverResource resolverResource, ResolveLogger logger,
			Map<Requirement, Wires> map) {
		Map<Requirement, ResolverWire> singletons = resolverResource.getSingletons();
		if (singletons.isEmpty()) {
			return false;
		}
		for (Entry<Requirement, Wires> entry : map.entrySet()) {
			Requirement key = entry.getKey();
			Wires wirecandidates = entry.getValue();
			Wires wires = wirecandidates.getSelectableWires();
			if (wires.size() > 1) {
				logger.log(resolverResource,
						"- must resolve " + ModuleContainer.toString(key) + " with " + wires.size() + " providers!");
				for (ResolverWire resolverWire : wires) {
					if (checkBackwardUseConstrainViolations(resolverResource, resolverWire, singletons, wires,
							logger)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private boolean checkBackwardUseConstrainViolations(ResolverResource resolverResource, ResolverWire resolverWire,
			Map<Requirement, ResolverWire> singletons, Wires wires, ResolveLogger logger) {
		Set<String> uses = resolverWire.getUses();
		for (Entry<Requirement, ResolverWire> entry : singletons.entrySet()) {
			ResolverWire singelton = entry.getValue();
			if (singelton.isOptional()) {
				continue;
			}
			String packageName = singelton.getPackageName();
			Resource provider = singelton.getProvider();
			if (uses.contains(packageName) && wires.providesCandidate(provider)) {
				if (!Objects.equals(provider, resolverWire.getProvider())) {
					resolverWire.setNotSelectable(
							"violates the use-constrain because it uses the package '" + packageName
									+ "' that is uniquely provided by " + ModuleContainer.toString(provider)
									+ " already");
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Checks for "forward" use constrain violations, that is for each requirement
	 * that has more than one selectable wire, we check all singleton wires if the
	 * have a use-constrain on this package and are a provider of the package. If
	 * this is the case, we need to strike out providers not provideded by this
	 * resource.
	 * 
	 * @param resolverResource
	 * @param logger
	 * @param map
	 * @return
	 */
	private boolean checkForwardUseConstrainViolations(ResolverResource resolverResource, ResolveLogger logger,
			Map<Requirement, Wires> map) {
		boolean rerun;
		boolean changed = false;
		do {
			rerun = false;
			Map<Requirement, ResolverWire> singletons = resolverResource.getSingletons();
			if (singletons.isEmpty()) {
				break;
			}
			logger.log(resolverResource, "Filtering use constrains with " + singletons.size() + " singletons...");
			for (Entry<Requirement, Wires> entry : map.entrySet()) {
				Requirement key = entry.getKey();
				Wires wirecandidates = entry.getValue();
				Wires wires = wirecandidates.getSelectableWires();
				if (wires.size() > 1) {
					logger.log(resolverResource,
							"- must resolve " + ModuleContainer.toString(key) + " with " + wires.size()
									+ " providers!");
					for (ResolverWire resolverWire : wires) {
						checkForwardUseConstrainViolation(resolverWire, wirecandidates, singletons, logger);
					}
					boolean singleton = wirecandidates.isSingleton();
					if (singleton) {
						logger.log(resolverResource, "-> is now a singleton!");
					}
					rerun |= singleton;
					changed |= singleton;
				}
			}
			logger.log(resolverResource, "rerun needed: " + rerun);
		} while (rerun);
		return changed;
	}

	private void checkForwardUseConstrainViolation(ResolverWire resolverWire, Wires wirecandidates,
			Map<Requirement, ResolverWire> singletons, ResolveLogger logger) {
		String packageName = resolverWire.getPackageName();
		ResolverResource resource = resolverWire.getResource();
		if (packageName != null && !packageName.isEmpty()) {
			for (Map.Entry<Requirement, ResolverWire> entry : singletons.entrySet()) {
				ResolverWire singletonWire = entry.getValue();
				if (singletonWire.isOptional()) {
					continue;
				}
				Set<String> uses = singletonWire.getUses();
				Resource provider = singletonWire.getProvider();
				if (uses.contains(packageName) && !Objects.equals(provider, resolverWire.getProvider())
						&& wirecandidates.providesCandidate(provider)) {
					String reason = "Uses-constraint conflict with import '" + singletonWire.getPackageName()
							+ " that is provided by " + Util.toString(provider)
							+ " and no other alternative can be selected because it is also a provider for this package";
					logger.log(resource, "\tdisable " + resolverWire + ": " + reason);
					resolverWire.setNotSelectable(reason);
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
