/**********************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: 
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.runtime;

/**
 * Products are Eclipse unit of branding.  From the runtime point of view they have
 * a name, id and description and identify the Eclipse application to run.  
 * <p>
 * Since the bulk of the branding related information is
 * specific to the UI, products also carry an arbitrary set of properties.  The valid set of 
 * key-value pairs and their interpretation defined by the UI of the target environment.
 * </p><p>
 * Products can be defined using extensions to the <code>org.eclipse.core.runtime.products</code>
 * extension point or by using facilities provided by IProductProvider implementations.
 * </p><p>
 * For readers familiar with Eclipse 2.1 and earlier, products are roughly equivalent to 
 * <i>primary features</i>. 
 * </p>
 * 
 * @see IProductProvider
 * @since 3.0
 */
public interface IProduct {
	/**
	 * Returns the applicatoin associated with this product.  This information is used 
	 * to guide the runtime as to what application extension to create and execute.
	 * @return this product's application or null if none
	 * @since 3.0
	 */
	public String getApplication();
	/**
	 * Returns the name of this product.  The name is typcially used in the title
	 * bar of UI windows.
	 * @return the name of this product or null if none
	 */
	public String getName();
	/**
	 * Returns the text desciption of this product
	 * @return the description of this product or null if none
	 */
	public String getDescription();
	/** Returns the unique product id of this product.
	 * @return the id of this product
	 */
	public String getId();
	
	/**
	 * Returns the property of this product with the given key.
	 * null is returned if there is no such key/value pair.
	 * @param key the name of the property to return
	 * @return the value associated with the given key
	 */
	public String getProperty(String key);
}
