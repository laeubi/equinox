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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.osgi.service.resolver.ResolveContext;

// Note this class is not thread safe.
// Only use in the context of a single thread.
class ResolveSession implements AutoCloseable {

	private ExecutorService executorService;
	private ResolveContext context;
	private ConcurrentHashMap<String, List<String>> cache = new ConcurrentHashMap<>();

	public ResolveSession(ResolveContext context) {
		this.context = context;
		executorService = Executors.newWorkStealingPool();
	}

	public boolean isDynamic() {
		return false;
	}

	public boolean isCancelled() {
		return false;
	}

	public Executor getExecutor() {
		return executorService;
	}

	@Override
	public void close() {
		executorService.shutdownNow();
	}

	public ResolveContext getContext() {
		return context;
	}

	public boolean checkMultiple(UsedBlames usedBlames, Blame usedBlame, Candidates allCandidates) {
		// TODO Auto-generated method stub
		return false;
	}

	public Map<String, List<String>> getUsesCache() {
		return cache;
	}

}