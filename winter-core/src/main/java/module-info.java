/*
 * Copyright 2018 Jeremy KUHN
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

/**
 * <p>
 * The Winter Framework core module, providing base elements required by Module
 * classes generated by the Winter Framework compiler.
 * </p>
 * 
 * <p>
 * At compile time, the framework processes module classes to generates a Module
 * class providing bean creation, exposure and dependency injection. The types
 * defined in this module are used to provide the remaining glue to create,
 * start and stop a module.
 * </p>
 * 
 * @author jkuhn
 * @since 1.0
 */
module io.winterframework.core {
	requires java.logging;
	requires transitive java.compiler;
	
	requires static transitive io.winterframework.core.annotation;

	exports io.winterframework.core.v1;
}