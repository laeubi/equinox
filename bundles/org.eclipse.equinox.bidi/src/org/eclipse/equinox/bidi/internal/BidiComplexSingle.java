/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.equinox.bidi.internal;

import org.eclipse.equinox.bidi.custom.BidiComplexFeatures;
import org.eclipse.equinox.bidi.custom.BidiComplexProcessor;

/**
 *  <code>BidiComplexSingle</code> is a processor for complex expressions
 *  composed of two parts separated by an operator.
 *  The first occurrence of the operator delimits the end of the first part
 *  and the start of the second part. Further occurrences of the operator,
 *  if any, are treated like regular characters of the second text part.
 *  The processor makes sure that the expression be presented in the form
 *  (assuming that the equal sign is the operator):
 *  <pre>
 *  part1=part2
 *  </pre>
 *  The {@link BidiComplexFeatures#getOperators operators}
 *  field in the {@link BidiComplexFeatures features}
 *  of this processor should contain exactly one character.
 *  Additional characters will be ignored.
 *
 *  @author Matitiahu Allouche
 */
public abstract class BidiComplexSingle extends BidiComplexProcessor {

	/**
	 *  This method locates occurrences of the operator.
	 */
	public int indexOfSpecial(BidiComplexFeatures features, String text, byte[] dirProps, int[] offsets, int caseNumber, int fromIndex) {
		return text.indexOf(features.getOperators().charAt(0), fromIndex);
	}

	/**
	 *  This method inserts a mark before the operator if needed and
	 *  skips to the end of the source string.
	 */
	public int processSpecial(BidiComplexFeatures features, String text, byte[] dirProps, int[] offsets, int[] state, int caseNumber, int operLocation) {
		BidiComplexProcessor.processOperator(features, text, dirProps, offsets, operLocation);
		return text.length();
	}

}
