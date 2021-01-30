package org.tbee.tesla;

/*-
 * #%L
 * TeslaAPI
 * %%
 * Copyright (C) 2020 - 2021 Tom Eugelink
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.junit.Assert;

public class TestUtil {

	static public void assertContains(String s, String contains) {
		Assert.assertTrue("Text '" + contains + "' not found in '" + s + "'", s.contains(contains));
	}

	static public void assertNotContains(String s, String contains) {
		Assert.assertFalse("Text '" + contains + "' found in '" + s + "'", s.contains(contains));
	}
}
