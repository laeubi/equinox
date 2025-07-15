/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.resolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

/**
 * The idea of the {@link ProblemReduction} class is to strike out
 * {@link Capability}s that might satisfy {@link Requirement}s but violates some
 * contracts that would lead to a guaranteed unresolvable state.
 */
class ProblemReduction {

    private static final Capability[] EMPTY_CAPABILITIES = new Capability[0];

    /**
     * Removes all violating providers for a given {@link Requirement} and
     * {@link Candidates} in a local search, that is if the requirement has any uses
     * it checks if there are other packages used by this one and removes any
     * offending providers from the top of the list.
     * 
     * @param candidates  candidates to filter
     * @param requirement the requirement where the search should start
     * @return a list of Candidates that where dropped as part of the filtering
     */
    static List<Candidates> removeUsesViolations(Candidates candidates, Requirement requirement, Logger logger) {
        Resource targetResource = requirement.getResource();
        // fetch the current candidate for this requirement
        Capability currentCandidate = candidates.getFirstCandidate(requirement);
		if (currentCandidate == null) {
			return Collections.emptyList();
		}
        Resource candidateResource = currentCandidate.getResource();
        // now check if it has any uses constraints
        Set<String> uses = new TreeSet<>(Util.getUses(currentCandidate));
        if (uses.isEmpty()) {
            // there is nothing this one can conflict in this current set of candidates
            return Collections.emptyList();
        }


//        if (logger.isDebugEnabled()) {
//            logger.logRequirement("=== remove uses violations for %s", requirement);
//            logger.logCapability("== current candidate is %s", currentCandidate);
//            logger.logCandidates(targetResource, req -> getCapabilityList(candidates, req));
//        }
        boolean repeat;
        int round = 0;
        List<Candidates> dropped = new ArrayList<>();
        do {
            repeat = false;
            round++;
            if (logger.isDebugEnabled()) {
                logger.debug("Round " + round + ":");
                for (String usedPackage : uses) {
                    logger.debug(" uses: " + usedPackage);
                }
            }
            // now look at all other imports of the target resource if it is a package that
            // is part of a used package
            for (Requirement packageRequirement : targetResource.getRequirements(PackageNamespace.PACKAGE_NAMESPACE)) {
                if (packageRequirement == requirement) {
                    continue;
                }
                Capability providedPackage = candidates.getCapability(candidateResource, packageRequirement);
                if (providedPackage == null) {
                    // we do not provide anything for this package
                    continue;
                }
                if (uses.contains(Util.getPackageName(providedPackage))) {
                    // this is a package where we are a candidate and that has a uses constraint, so
                    // this package must be provided by us as well or we run into a uses-violation
                    // later on!
                    Capability capability = removeViolators(candidates, candidateResource, packageRequirement, dropped);
                    // if we have added any additional uses we need to reiterate...
                    repeat |= uses.addAll(Util.getUses(capability));
                }
            }
        } while (repeat);
        if (logger.isDebugEnabled() && !dropped.isEmpty()) {
            logger.debug("After removal (" + dropped.size() + " dropped)");
            logger.logCandidates(targetResource, req -> getCapabilityList(candidates, req));
        }
        return dropped;
    }

	/**
	 * Removes all invalid package providers for a given {@link Requirement} and
	 * {@link Candidates} in a local search, that is if the requirement is a package
	 * and that package is used by any unique selected package for another import,
	 * then only the same provider can be a valid candidate without leading to a
	 * use-constraint violation otherwise.
	 * 
	 * @param candidates  candidates to filter
	 * @param requirement the requirement where the search should start
	 * @return a list of Candidates that where dropped as part of the filtering
	 */
	static List<Candidates> removeInvalidPackageProvider(Candidates candidates, Requirement requirement,
			Logger logger) {
		List<Candidates> dropped = new ArrayList<>();
		Resource targetResource = requirement.getResource();
		for (Requirement packageRequirement : targetResource.getRequirements(PackageNamespace.PACKAGE_NAMESPACE)) {
			Capability singleProvider = getSingleProvider(candidates, packageRequirement);
			if (singleProvider == null) {
				continue;
			}
			Set<String> uses = Util.getUses(singleProvider);
			if (uses.isEmpty()) {
				// no uses --> no problems!
				continue;
			}
			Capability firstCandidate;
			while ((firstCandidate = candidates.getFirstCandidate(requirement)) != null) {
				// now check if the package name is inside the uses of the package import
				String packageName = Util.getPackageName(firstCandidate);
				if (uses.contains(packageName)) {
					// if that is the case, then the provider must be the same!
					if (firstCandidate.getResource() != singleProvider.getResource()) {
						// we must drop this, as the current provider do not match the one already
						// uniquely selected!
						dropped.add(candidates.copy());
						candidates.removeFirstCandidate(requirement);
						System.out.println("Drop provider " + firstCandidate + " for requirement " + requirement
								+ " because it violates already selected "
								+ singleProvider);
						continue; // next round
					}
				}
				break;
			}
		}
		return dropped;
	}

	private static Capability getSingleProvider(Candidates candidates, Requirement packageRequirement) {
		List<Capability> providers = candidates.getCandidates(packageRequirement);
		if (providers != null && providers.size() == 1) {
			return providers.get(0);
		}
		return null;
	}

    private static Capability removeViolators(Candidates candidates, Resource candidateResource,
            Requirement packageRequirement, List<Candidates> dropped) {
        Capability capability;
        while ((capability = candidates.getFirstCandidate(packageRequirement)).getResource() != candidateResource) {
            dropped.add(candidates.copy());
            candidates.removeFirstCandidate(packageRequirement);
        }
        return capability;
    }

    private static List<Capability> getCapabilityList(Candidates candidates, Requirement requirement) {
        List<Capability> list = candidates.getCandidates(requirement);
        if (list == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(list);
    }

	public static List<Candidates> reduce(Candidates initialCandidates, Requirement requirement, Logger m_logger) {
		ArrayList<Candidates> result = new ArrayList<>();
		result.addAll(removeUsesViolations(initialCandidates, requirement, m_logger));
		result.addAll(removeInvalidPackageProvider(initialCandidates, requirement, m_logger));
		return result;
	}

}
