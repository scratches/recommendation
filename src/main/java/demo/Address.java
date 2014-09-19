/*
 * Copyright 2014 the original author or authors.
 *
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
 */
package demo;

import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Value object to represent an {@link Address}.
 * 
 * @author Oliver Gierke
 */
@Data
@RequiredArgsConstructor
public class Address {

	private final String street, city, zip;
	private final Point location;
	
	protected Address() {
		location = null;
		street = city = zip = null;
	}

	@Data
	@RequiredArgsConstructor
	public static class Point {
		private final double x;
		private final double y;
		protected Point() { x = y = 0; }
	}
}
