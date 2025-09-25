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
package org.eclipse.osgi.container.resolver.check;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.eclipse.osgi.container.resolver.ResolverResource;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

public class Candidates
{

	private Map<Resource, ResolverResource> resources;

	public Candidates(Map<Resource, ResolverResource> resources) {
		this.resources = resources;
	}

	public int getNbResources() {
		return resources.size();
	}

	public Capability getFirstCandidate(Requirement req) {
		ResolverResource resolverResource = resources.get(req.getResource());
		if (resolverResource == null) {
			return null;
		}
		return resolverResource.getFirstCandidate(req);
	}

	public List<Capability> getCandidates(Requirement req) {
		ResolverResource resolverResource = resources.get(req.getResource());
		if (resolverResource == null) {
			return null;
		}
		List<Capability> list = resolverResource.getCandidates(req);
		if (list.isEmpty()) {
			return null;
		}
		return list;
	}

	public Candidates copy() {
		return this;
	}

	public Collection<Resource> getResources() {
		return resources.keySet();
	}

}
