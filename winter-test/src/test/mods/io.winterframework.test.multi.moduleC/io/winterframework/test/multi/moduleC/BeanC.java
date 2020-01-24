/*
 * Copyright 2019 Jeremy KUHN
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.winterframework.test.multi.moduleC;

import io.winterframework.core.annotation.Bean;
import io.winterframework.test.multi.moduleA.BeanA;
import io.winterframework.test.multi.moduleB.BeanB;

@Bean
public class BeanC {
	
	public BeanA beanA;
	
	public BeanB beanB;

	public BeanC(BeanA beanA, BeanB beanB) {
		this.beanA = beanA;
		this.beanB = beanB;
	}
}
