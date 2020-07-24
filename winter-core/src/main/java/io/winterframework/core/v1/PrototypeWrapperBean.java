/*
 * Copyright 2020 Jeremy KUHN
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
package io.winterframework.core.v1;

import java.util.WeakHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

import io.winterframework.core.v1.Module.Bean;

/**
 * <p>
 * A prototype wrapper {@link Bean} implementation.
 * </p>
 * 
 * <p>
 * A Prototype bean is instantiated each time it is requested, a distinct
 * instance is injected into each dependent bean.
 * </p>
 * 
 * <p>
 * As for {@link PrototypeModuleBean}, particular care must be taken when
 * creating prototype beans instances outside of a module (eg.
 * moduleInstance.prototypeBean()), see {@link PrototypeModuleBean}
 * documentation for more information.
 * </p>
 * 
 * <p>
 * The actual bean instance of a wrapper bean is provided by a wrapper instance
 * to which instantiation, initialization and destruction operations are
 * delegated. To each bean instance corresponds a wrapper instance. There is no
 * requirement that a new or distinct result be returned each time the wrapper
 * is invoked, however when initialization and destruction operations are
 * specified, the wrapper, as indicated by its name, will usually wrap a single
 * instance so that destruction operations can be invoked at later stage when
 * the bean is destroyed. In that case, particular care must be taken to make
 * sure the wrapper instance does not hold a strong reference to the actual
 * instance, otherwise bean instances created outside the module might not be
 * reclaimed by the garbage collector leading to memory leaks. A
 * {@link WeakReference} should be then used in such situations. Note that this
 * issue does not exist for singleton wrapper beans.
 * </p>
 * 
 * @author jkuhn
 * @since 1.0
 * @see Bean
 *
 * @param <W> the type of the wrapper bean
 * @param <T> the actual type of the bean
 */
abstract class PrototypeWrapperBean<W extends Supplier<T>, T> extends AbstractWrapperBean<W, T> {

	/**
	 * The bean logger.
	 */
	protected static final Logger LOGGER = Logger.getLogger(PrototypeWrapperBean.class.getName());
	
	/**
	 * A weak hash map holding the bean instances issued by the bean as keys and
	 * their corresponding wrapper instance as value.
	 */
	private WeakHashMap<T, W> instances;
	
	/**
	 * <p>
	 * Creates a prototype wrapper bean with the specified name.
	 * </p>
	 * 
	 * @param name the bean name
	 */
	public PrototypeWrapperBean(String name) {
		super(name);
	}
	
	/**
	 * <p>
	 * Creates the prototype bean.
	 * </p>
	 * 
	 * <p>
	 * Since a new bean instance must be created each time the bean is requested,
	 * this method basically does nothing, instances being created in the
	 * {@link #get()} method.
	 * </p>
	 */
	@Override
	public synchronized final void create() {
		if (this.instances == null) {
			LOGGER.fine(() -> "Creating Prototype Bean " + (this.parent != null ? this.parent.getName() : "") + ":" + this.name);
			this.instances = new WeakHashMap<>();
			this.parent.recordBean(this);
		}
	}
	
	/**
	 * <p>
	 * Returns a new bean instance.
	 * </p>
	 * 
	 * <p>
	 * This method delegates bean instance creation to a wrapper instance returned
	 * by {@link #createWrapper()} method.
	 * </p>
	 * 
	 * @return a bean instance
	 */
	@Override
	public final T doGet() {
		W wrapper = this.createWrapper();
		T instance = wrapper.get();
		this.instances.put(instance, wrapper);
		return instance;
	}

	/**
	 * <p>
	 * Destroys the prototype bean and as a result all bean wrapper instances it has
	 * issued.
	 * </p>
	 * 
	 * <p>
	 * This method delegates bean instance destruction to the
	 * {@link #destroyWrapper(Object)} method.
	 * </p>
	 */
	@Override
	public synchronized final void destroy() {
		if (this.instances != null) {
			LOGGER.fine(() -> "Destroying Prototype Bean " + (this.parent != null ? this.parent.getName() : "") + ":" + this.name);
			this.instances.values().stream()
				.forEach(wrapper -> this.destroyWrapper(wrapper));
			this.instances.clear();
			this.instances = null;
		}
	}
}
