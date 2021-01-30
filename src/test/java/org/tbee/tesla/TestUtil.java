package org.tbee.tesla;

import org.junit.Assert;

public class TestUtil {

	static public void assertContains(String s, String contains) {
		Assert.assertTrue("Text '" + contains + "' not found in '" + s + "'", s.contains(contains));
	}

	static public void assertNotContains(String s, String contains) {
		Assert.assertFalse("Text '" + contains + "' found in '" + s + "'", s.contains(contains));
	}
}
