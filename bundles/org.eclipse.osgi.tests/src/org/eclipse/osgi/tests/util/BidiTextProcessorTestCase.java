/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/

package org.eclipse.osgi.tests.util;

import org.eclipse.osgi.util.TextProcessor;

/**
 * Tests for strings that use the TextProcessor and are run in a bidi locale.
 *
 */
public class BidiTextProcessorTestCase extends TextProcessorTestCase {

	// left to right marker
	protected static char LRM = '\u200e';
	// left to right embedding
	protected static char LRE = '\u202a';
	// pop directional format	
	protected static char PDF = '\u202c';

	private static String PATH_1_RESULT = LRE + "d" + PDF + ":" + LRM + "\\" + LRM + LRE + "test" + PDF + "\\" + LRM + LRE + "\u05d0\u05d1\u05d2\u05d3 \u05d4\u05d5" + PDF + "\\" + LRM + LRE + "segment" + PDF;
	private static String PATH_2_RESULT = LRM + "\\" + LRM + LRE + "test" + PDF + "\\" + LRM + LRE + "\u05d0\u05d1\u05d2\u05d3 \u05d4\u05d5" + PDF + "\\" + LRM + LRE + "segment" + PDF;
	private static String PATH_3_RESULT = LRE + "d" + PDF + ":" + LRM + "\\" + LRM + LRE + "\u05ea\u05e9\u05e8\u05e7\u05e6 abcdef-\u05e5\u05e4\u05e3" + PDF + "\\" + LRM + LRE + "xyz" + PDF + "\\" + LRM + LRE + "abcdef" + PDF + "\\" + LRM + LRE + "\u05e2\u05e1\u05e0" + PDF;
	private static String PATH_4_RESULT = LRM + "\\" + LRM + LRE + "\u05ea\u05e9\u05e8\u05e7\u05e6 abcdef-\u05e5\u05e4\u05e3" + PDF + "\\" + LRM + LRE + "xyz" + PDF + "\\" + LRM + LRE + "abcdef" + PDF + "\\" + LRM + LRE + "\u05e2\u05e1\05e0" + PDF;
	private static String PATH_5_RESULT = LRE + "d" + PDF + ":" + LRM + "\\" + LRM + LRE + "\u05ea\u05e9\u05e8\u05e7\u05e6 abcdef-\u05e5\u05e4\u05e3" + PDF + "\\" + LRM + LRE + "xyz" + PDF + "\\" + LRM + LRE + "abcdef" + PDF + "\\" + LRM + LRE + "\u05e2\u05e1\05e0" + PDF + "\\" + LRM + LRE + "\u05df\u05fd\u05dd" + PDF + "." + LRM + LRE + "java" + PDF;
	private static String PATH_6_RESULT = LRE + "d" + PDF + ":" + LRM + "\\" + LRM + LRE + "\u05ea\u05e9\u05e8\u05e7\u05e6 abcdef-\u05e5\u05e4\u05e3" + PDF + "\\" + LRM + LRE + "xyz" + PDF + "\\" + LRM + LRE + "abcdef" + PDF + "\\" + LRM + LRE + "\u05e2\u05e1\05e0" + PDF + "\\" + LRM + LRE + "\u05df\u05fd\u05dd" + PDF + "." + LRM + LRE + "\u05dc\u05db\u05da" + PDF;
	private static String PATH_7_RESULT = LRE + "d" + PDF + ":" + LRM + "\\" + LRM + LRE + "\u05ea\u05e9\u05e8\u05e7\u05e6 abcdef-\u05e5\u05e4\u05e3" + PDF + "\\" + LRM + LRE + "xyz" + PDF + "\\" + LRM + LRE + "abcdef" + PDF + "\\" + LRM + LRE + "\u05e2\u05e1\05e0" + PDF + "\\" + LRM + LRE + "Test" + PDF + "." + LRM + LRE + "java" + PDF;
	private static String PATH_8_RESULT = LRM + "\\" + LRM + LRE + "test" + PDF + "\\" + LRM + LRE + "jkl\u05d0\u05d1\u05d2\u05d3 \u05d4\u05d5" + PDF + "\\" + LRM + LRE + "segment" + PDF;
	private static String PATH_9_RESULT = LRM + "\\" + LRM + LRE + "test" + PDF + "\\" + LRM + LRE + "\u05d0\u05d1\u05d2\u05d3 \u05d4\u05d5jkl" + PDF + "\\" + LRM + LRE + "segment" + PDF;
	private static String PATH_10_RESULT = LRE + "d" + PDF + ":" + LRM + "\\" + LRM + LRE + "t" + PDF + "\\" + LRM + LRE + "\u05d0" + PDF + "\\" + LRM + LRE + "segment" + PDF;
	private static String PATH_11_RESULT = "\\" + LRM + LRE + "t" + PDF + "\\" + LRM + LRE + "\u05d0" + PDF + "\\" + LRM + LRE + "segment" + PDF;
	private static String PATH_12_RESULT = LRE + "d" + PDF + ":" + LRM + "\\" + LRM;
	private static String PATH_13_RESULT = LRM + "\\" + LRM + LRE + "test" + PDF;

	private static String OTHER_STRING_NO_DELIM = "\u05ea\u05e9\u05e8\u05e7\u05e6 abcdef-\u05e5\u05e4\u05e3";

	private static String OTHER_STRING_1_RESULT = LRM + "*" + LRM + "." + LRM + LRE + "java" + PDF;
	private static String OTHER_STRING_2_RESULT = LRM + "*" + LRM + "." + LRM + LRE + "\u05d0\u05d1\u05d2" + PDF;
	private static String OTHER_STRING_3_RESULT = LRE + "\u05d0\u05d1\u05d2 " + PDF + "=" + LRM + LRE + " \u05ea\u05e9\u05e8\u05e7\u05e6" + PDF;
	// result strings if null delimiter is passed for *.<string> texts
	private static String OTHER_STRING_1_ND_RESULT = LRE + "*" + PDF + "." + LRM + LRE + "java" + PDF;
	private static String OTHER_STRING_2_ND_RESULT = LRE + "*" + PDF + "." + LRM + LRE + "\u05d0\u05d1\u05d2" + PDF;

	private static String[] RESULT_DEFAULT_PATHS = {PATH_1_RESULT, PATH_2_RESULT, PATH_3_RESULT, PATH_4_RESULT, PATH_5_RESULT, PATH_6_RESULT, PATH_7_RESULT, PATH_8_RESULT, PATH_9_RESULT, PATH_10_RESULT, PATH_11_RESULT, PATH_12_RESULT, PATH_13_RESULT};

	private static String[] RESULT_STAR_PATHS = {OTHER_STRING_1_RESULT, OTHER_STRING_2_RESULT};
	private static String[] RESULT_EQUALS_PATHS = {OTHER_STRING_3_RESULT};
	private static String[] RESULT_STAR_PATHS_ND = {OTHER_STRING_1_ND_RESULT, OTHER_STRING_2_ND_RESULT};

	protected String defaultDelimiters = TextProcessor.getDefaultDelimiters();

	/**
	 * Constructor.
	 * 
	 * @param name test name
	 */
	public BidiTextProcessorTestCase(String name) {
		super(name);
	}

	/*
	 * Test TextProcessor for file paths.
	 */
	public void testBidiPaths() {
		for (int i = 0; i < TEST_DEFAULT_PATHS.length; i++) {
			String result = TextProcessor.process(TEST_DEFAULT_PATHS[i]);
			verifyBidiResult("Path " + (i + 1), result, RESULT_DEFAULT_PATHS[i]);
		}
	}

	public void testBidiPathsWithNullDelimiter() {
		// should use default delimiters
		for (int i = 0; i < TEST_DEFAULT_PATHS.length; i++) {
			String result = TextProcessor.process(TEST_DEFAULT_PATHS[i], null);
			verifyBidiResult("Path " + (i + 1), result, RESULT_DEFAULT_PATHS[i]);
		}
	}

	public void testBidiStringWithNoDelimiters() {
		String result = TextProcessor.process(OTHER_STRING_NO_DELIM);
		assertEquals("Other string containing no delimiters not equivalent.", OTHER_STRING_NO_DELIM, result);
	}

	/*
	 * Test other possible uses for TextProcessor, including file associations and 
	 * variable assignment statements.
	 */
	public void testOtherStrings() {
		int testNum = 1;
		for (int i = 0; i < TEST_STAR_PATHS.length; i++) {
			String result = TextProcessor.process(TEST_STAR_PATHS[i], "*.");
			verifyBidiResult("Other string " + testNum, result, RESULT_STAR_PATHS[i]);
			testNum++;
		}

		for (int i = 0; i < TEST_EQUALS_PATHS.length; i++) {
			String result = TextProcessor.process(TEST_EQUALS_PATHS[i], "=");
			verifyBidiResult("Other string " + testNum, result, RESULT_EQUALS_PATHS[i]);
			testNum++;
		}
	}

	public void testOtherStringsWithNullDelimiter() {
		int testNum = 1;
		for (int i = 0; i < TEST_STAR_PATHS.length; i++) {
			String result = TextProcessor.process(TEST_STAR_PATHS[i], null);
			verifyBidiResult("Other string " + testNum, result, RESULT_STAR_PATHS_ND[i]);
			testNum++;
		}
		// should be the same result as what was input
		for (int i = 0; i < TEST_EQUALS_PATHS.length; i++) {
			String result = TextProcessor.process(TEST_EQUALS_PATHS[i], null);
			verifyBidiResult("Other string " + testNum, result, TEST_EQUALS_PATHS[i]);
			testNum++;
		}
	}

	/*
	 * Test the result to ensure markers aren't added more than once if the 
	 * string is processed multiple times.
	 */
	public void testDoubleProcessPaths() {
		for (int i = 0; i < TEST_DEFAULT_PATHS.length; i++) {
			String result = TextProcessor.process(TEST_DEFAULT_PATHS[i]);
			result = TextProcessor.process(result);
			verifyBidiResult("Path " + (i + 1), result, RESULT_DEFAULT_PATHS[i]);
		}
	}

	/*
	 * Test the result to ensure markers aren't added more than once if the 
	 * string is processed multiple times.
	 */
	public void testDoubleProcessOtherStrings() {
		int testNum = 1;
		for (int i = 0; i < TEST_STAR_PATHS.length; i++) {
			String result = TextProcessor.process(TEST_STAR_PATHS[i], "*.");
			result = TextProcessor.process(result, "*.");
			verifyBidiResult("Other string " + testNum, result, RESULT_STAR_PATHS[i]);
			testNum++;
		}

		for (int i = 0; i < TEST_EQUALS_PATHS.length; i++) {
			String result = TextProcessor.process(TEST_EQUALS_PATHS[i], "=");
			result = TextProcessor.process(result, "=");
			verifyBidiResult("Other string " + testNum, result, RESULT_EQUALS_PATHS[i]);
			testNum++;
		}
	}

	public void testEmptyStringParams() {
		verifyBidiResult("TextProcessor.process(String) for empty string ", TextProcessor.process(""), EMPTY_STRING);
		verifyBidiResult("TextProcessor.process(String, String) for empty strings ", TextProcessor.process("", ""), EMPTY_STRING);
	}

	public void testNullParams() {
		assertNull("TextProcessor.process(String) for null param ", TextProcessor.process(null));
		assertNull("TextProcessor.process(String, String) for params ", TextProcessor.process(null, null));
	}

	private void verifyBidiResult(String testName, String result, String expected) {
		assertTrue(testName + " result string is not the same as expected string.", result.equals(expected));
	}
}
