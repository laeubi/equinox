/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.ds.tests.tb26.impl;

import org.eclipse.equinox.ds.tests.tb26.Component;

public class Component1 extends Component {
	@Override
	public String getName() {
		return getClass().getName();
	}

	@Override
	public void update() throws Exception {
		replaceCurrentComponentXmlWith("component2.xml");
	}
}
