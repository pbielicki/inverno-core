/*
 * Copyright 2018 Jeremy KUHN
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
package io.winterframework.core.compiler;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;

import io.winterframework.core.annotation.Bean;
import io.winterframework.core.annotation.WiredTo;
import io.winterframework.core.compiler.ModuleClassGeneration.GenerationMode;
import io.winterframework.core.compiler.spi.BeanInfo;
import io.winterframework.core.compiler.spi.ConfigurationInfo;
import io.winterframework.core.compiler.spi.ConfigurationPropertyInfo;
import io.winterframework.core.compiler.spi.ConfigurationSocketBeanInfo;
import io.winterframework.core.compiler.spi.ModuleBeanInfo;
import io.winterframework.core.compiler.spi.ModuleBeanMultiSocketInfo;
import io.winterframework.core.compiler.spi.ModuleBeanSingleSocketInfo;
import io.winterframework.core.compiler.spi.ModuleBeanSocketInfo;
import io.winterframework.core.compiler.spi.ModuleInfo;
import io.winterframework.core.compiler.spi.ModuleInfoVisitor;
import io.winterframework.core.compiler.spi.MultiSocketBeanInfo;
import io.winterframework.core.compiler.spi.MultiSocketInfo;
import io.winterframework.core.compiler.spi.MultiSocketType;
import io.winterframework.core.compiler.spi.NestedConfigurationPropertyInfo;
import io.winterframework.core.compiler.spi.SingleSocketBeanInfo;
import io.winterframework.core.compiler.spi.SingleSocketInfo;
import io.winterframework.core.compiler.spi.SocketBeanInfo;
import io.winterframework.core.compiler.spi.SocketInfo;
import io.winterframework.core.compiler.spi.WrapperBeanInfo;

/**
 * <p>A {@link ModuleInfoVisitor} implementation that generates a Winter module class.</p>
 * 
 * @author jkuhn
 *
 */
class ModuleClassGenerator implements ModuleInfoVisitor<String, ModuleClassGeneration> {

	private static final String WINTER_CORE_PACKAGE = "io.winterframework.core.v1";
	
	private static final String WINTER_CORE_MODULE_CLASS = WINTER_CORE_PACKAGE + ".Module";
	private static final String WINTER_CORE_MODULE_MODULEBUILDER_CLASS = WINTER_CORE_PACKAGE + ".Module.ModuleBuilder";
	private static final String WINTER_CORE_MODULE_LINKER_CLASS = WINTER_CORE_PACKAGE + ".Module.ModuleLinker";
	private static final String WINTER_CORE_MODULE_BEAN_CLASS = WINTER_CORE_PACKAGE + ".Module.Bean";
	private static final String WINTER_CORE_MODULE_WRAPPERBEANBUILDER_CLASS = WINTER_CORE_PACKAGE + ".Module.WrapperBeanBuilder";
	private static final String WINTER_CORE_MODULE_MODULEBEANBUILDER_CLASS = WINTER_CORE_PACKAGE + ".Module.ModuleBeanBuilder";
	private static final String WINTER_CORE_MODULE_BEANAGGREGATOR_CLASS = WINTER_CORE_PACKAGE + ".Module.BeanAggregator";
	
	@Override
	public String visit(ModuleInfo moduleInfo, ModuleClassGeneration generation) {
		String className = moduleInfo.getQualifiedName().getClassName();
		String packageName = className.lastIndexOf(".") != -1 ? className.substring(0, className.lastIndexOf(".")) : "";
		className = className.substring(packageName.length() + 1);
		
		if(generation.getMode() == GenerationMode.MODULE_CLASS) {
			TypeMirror generatedType = generation.getElementUtils().getTypeElement(generation.getElementUtils().getModuleElement("java.compiler"), "javax.annotation.processing.Generated").asType();
			TypeMirror moduleType = generation.getElementUtils().getTypeElement(WINTER_CORE_MODULE_CLASS).asType();

			generation.addImport(className, moduleInfo.getQualifiedName().getClassName());
			generation.addImport("Builder", moduleInfo.getQualifiedName().getClassName() + ".Builder");
			
			// Fields
			String module_field_beans = Arrays.stream(moduleInfo.getBeans())
				.map(moduleBeanInfo -> this.visit(moduleBeanInfo, generation.forModule(moduleInfo.getQualifiedName()).withMode(GenerationMode.BEAN_FIELD)))
				.collect(Collectors.joining("\n"));
			String module_field_modules = Arrays.stream(moduleInfo.getModules())
				.map(componentModuleInfo -> this.visit(componentModuleInfo, generation.forModule(moduleInfo.getQualifiedName()).withMode(GenerationMode.COMPONENT_MODULE_FIELD)))
				.collect(Collectors.joining("\n"));
			
			String module_constructor_parameters = Stream.concat(Arrays.stream(moduleInfo.getSockets()), Arrays.stream(moduleInfo.getConfigurations()).map(configurationInfo -> configurationInfo.getSocket()))//Arrays.stream(moduleInfo.getSockets())
				.map(socketInfo -> this.visit(socketInfo , generation.forModule(moduleInfo.getQualifiedName()).withMode(GenerationMode.SOCKET_PARAMETER)))
				.collect(Collectors.joining(", "));
			
			String module_constructor_modules = Arrays.stream(moduleInfo.getModules())
				.map(componentModuleInfo -> this.visit(componentModuleInfo, generation.forModule(moduleInfo.getQualifiedName()).withMode(GenerationMode.COMPONENT_MODULE_NEW)))
				.collect(Collectors.joining("\n"));
			
			String module_constructor_beans = Arrays.stream(moduleInfo.getBeans())
				.map(moduleBeanInfo -> this.visit(moduleBeanInfo, generation.forModule(moduleInfo.getQualifiedName()).withMode(GenerationMode.BEAN_NEW)))
				.collect(Collectors.joining("\n"));
			
			String module_method_beans = Arrays.stream(moduleInfo.getBeans())
				.map(moduleBeanInfo -> this.visit(moduleBeanInfo, generation.forModule(moduleInfo.getQualifiedName()).withMode(GenerationMode.BEAN_ACCESSOR)))
				.collect(Collectors.joining("\n"));
			
			String module_builder = this.visit(moduleInfo, generation.forModule(moduleInfo.getQualifiedName()).withMode(GenerationMode.MODULE_BUILDER_CLASS));
			String module_linker = this.visit(moduleInfo, generation.forModule(moduleInfo.getQualifiedName()).withMode(GenerationMode.MODULE_LINKER_CLASS));
			
			String module_configurations = Arrays.stream(moduleInfo.getConfigurations())
				.map(configurationInfo -> this.visit(configurationInfo, generation.forModule(moduleInfo.getQualifiedName()).withMode(GenerationMode.CONFIGURATION_CLASS)))
				.collect(Collectors.joining("\n"));
			String module_configuration_configurators = Arrays.stream(moduleInfo.getConfigurations())
				.map(configurationInfo -> this.visit(configurationInfo, generation.forModule(moduleInfo.getQualifiedName()).withMode(GenerationMode.CONFIGURATION_BUILDER_CLASS)))
				.collect(Collectors.joining("\n"));

			String moduleClass = "";

			if(!packageName.equals("")) {
				moduleClass += "package " + packageName + ";" + "\n\n";
			}
			
			generation.removeImport(className);
			generation.removeImport("Builder");
			generation.removeImport("BeanAggregator");
			generation.removeImport("ModuleLinker");
			generation.removeImport("ModuleBuilder");
			generation.removeImport("BeanBuilder");
			generation.removeImport("ModuleBeanBuilder");
			generation.removeImport("WrapperBeanBuilder");
			generation.removeImport("ConfigurationSocket");
			generation.removeImport("Bean");
			
			generation.getTypeName(generatedType);
			generation.getTypeName(moduleType);
			
			moduleClass += generation.getImports().stream().sorted().filter(i -> i.lastIndexOf(".") > 0 && !i.substring(0, i.lastIndexOf(".")).equals(packageName)).map(i -> "import " + i + ";").collect(Collectors.joining("\n")) + "\n\n";
			
			// TODO add version
			moduleClass += "@" + generation.getTypeName(generatedType) + "(value= {\"" + ModuleAnnotationProcessor.class.getCanonicalName() + "\", \"" + moduleInfo.getVersion() + "\"}, date = \"" + ZonedDateTime.now().toString() +"\")\n";
			moduleClass += "public class " + className + " extends " + generation.getTypeName(moduleType) + " {" + "\n\n";

			if(module_field_modules != null && !module_field_modules.equals("")) {
				moduleClass += module_field_modules + "\n\n";
			}
			if(module_field_beans != null && !module_field_beans.equals("")) {
				moduleClass += module_field_beans + "\n\n";
			}
			
			moduleClass += generation.indent(1) + "private " + className + "(" + module_constructor_parameters + ") {\n";
			moduleClass += generation.indent(2) + "super(\"" + moduleInfo.getQualifiedName().getValue() + "\");\n";
			
			if(module_constructor_modules != null && !module_constructor_modules.equals("")) {
				moduleClass += "\n" + module_constructor_modules + "\n";
			}
			if(module_constructor_beans != null && !module_constructor_beans.equals("")) {
				moduleClass += "\n" + module_constructor_beans + "\n";
			}
			
			moduleClass += generation.indent(1) + "}\n";
			
			if(module_method_beans != null && !module_method_beans.equals("")) {
				moduleClass += "\n" + module_method_beans + "\n";
			}
			
			moduleClass += module_builder + "\n\n";
			moduleClass += module_linker;
			
			if(module_configurations != null && !module_configurations.equals("")) {
				moduleClass += "\n\n" + module_configurations;
			}
			if(module_configuration_configurators != null && !module_configuration_configurators.equals("")) {
				moduleClass += "\n\n" + module_configuration_configurators;
			}
			
			moduleClass += "\n}\n";
			
			return moduleClass;
		}
		else if(generation.getMode() == GenerationMode.MODULE_BUILDER_CLASS) {
			TypeMirror moduleBuilderType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement(WINTER_CORE_MODULE_MODULEBUILDER_CLASS).asType());
			
			String module_builder_fields = Stream.concat(Arrays.stream(moduleInfo.getSockets()), Arrays.stream(moduleInfo.getConfigurations()).map(configurationInfo -> configurationInfo.getSocket()))
				.map(moduleSocketInfo -> this.visit(moduleSocketInfo, generation.forModule(moduleInfo.getQualifiedName()).withMode(GenerationMode.SOCKET_FIELD)))
				.collect(Collectors.joining("\n"));
			
			String module_builder_constructor_parameters = Arrays.stream(moduleInfo.getSockets())
				.filter(moduleSocketInfo -> !moduleSocketInfo.isOptional())
				.map(moduleSocketInfo -> (moduleSocketInfo instanceof MultiSocketInfo ? generation.getMultiTypeName(moduleSocketInfo.getType(), ((MultiSocketInfo)moduleSocketInfo).getMultiType()) : generation.getTypeName(moduleSocketInfo.getType())) + " " + moduleSocketInfo.getQualifiedName().normalize())
				.collect(Collectors.joining(", "));
			
			String module_builder_constructor_super_args = Arrays.stream(moduleInfo.getSockets())
					.filter(moduleSocketInfo -> !moduleSocketInfo.isOptional())
					.map(moduleSocketInfo -> "\"" + moduleSocketInfo.getQualifiedName().normalize() + "\", " + moduleSocketInfo.getQualifiedName().normalize())
					.collect(Collectors.joining(", "));
			
			String module_builder_constructor_assignments = Arrays.stream(moduleInfo.getSockets())
				.filter(moduleSocketInfo -> !moduleSocketInfo.isOptional())
				.map(moduleSocketInfo -> this.visit(moduleSocketInfo, generation.forModule(moduleInfo.getQualifiedName()).withMode(GenerationMode.SOCKET_ASSIGNMENT)))
				.collect(Collectors.joining("\n"));
			
			String module_builder_build_args = Stream.concat(Arrays.stream(moduleInfo.getSockets()), Arrays.stream(moduleInfo.getConfigurations()).map(configurationInfo -> configurationInfo.getSocket()))
				.map(moduleSocketInfo -> "this." + moduleSocketInfo.getQualifiedName().normalize())
				.collect(Collectors.joining(", "));
			
			String module_builder_socket_methods = Stream.concat(Arrays.stream(moduleInfo.getSockets()).filter(moduleSocketInfo -> moduleSocketInfo.isOptional()), Arrays.stream(moduleInfo.getConfigurations()).map(configurationInfo -> configurationInfo.getSocket()))
				.map(moduleSocketInfo -> this.visit(moduleSocketInfo, generation.forModule(moduleInfo.getQualifiedName()).withMode(GenerationMode.SOCKET_INJECTOR)))
				.collect(Collectors.joining("\n"));

			String moduleBuilderClass = generation.indent(1) + "public static class Builder extends " + generation.getTypeName(moduleBuilderType) + "<" + className + "> {\n\n";
			if(module_builder_fields != null && !module_builder_fields.equals("")) {
				moduleBuilderClass += module_builder_fields + "\n\n";
			}
			if(module_builder_constructor_parameters != null && !module_builder_constructor_parameters.equals("")) {
				moduleBuilderClass += generation.indent(2) + "public Builder(" + module_builder_constructor_parameters + ") {\n";
				moduleBuilderClass += generation.indent(3) + "super(" + module_builder_constructor_super_args + ");\n\n";
				moduleBuilderClass += module_builder_constructor_assignments + "\n";
				moduleBuilderClass += generation.indent(2) + "}\n\n";
			}
			
			moduleBuilderClass += generation.indent(2) + "protected " + className + " doBuild() {\n";
			moduleBuilderClass += generation.indent(3) + "return new " + className + "(" + module_builder_build_args + ");\n";
			moduleBuilderClass += generation.indent(2) + "}\n";
			
			if(module_builder_socket_methods != null && !module_builder_socket_methods.equals("")) {
				moduleBuilderClass += "\n" + module_builder_socket_methods + "\n";
			}
			
			moduleBuilderClass += generation.indent(1) + "}";
			
			return moduleBuilderClass;
		}
		else if(generation.getMode() == GenerationMode.MODULE_LINKER_CLASS) {
			TypeMirror moduleLinkerType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement(WINTER_CORE_MODULE_LINKER_CLASS).asType());
			TypeMirror mapType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement(Map.class.getCanonicalName()).asType());
			TypeMirror optionalType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement(Optional.class.getCanonicalName()).asType());
			
			String linker_module_args = Stream.concat(Arrays.stream(moduleInfo.getSockets()), Arrays.stream(moduleInfo.getConfigurations()).map(configurationInfo -> configurationInfo.getSocket()))
				.map(moduleSocketInfo -> {
					String result = generation.indent(4);
					if(moduleSocketInfo instanceof ConfigurationSocketBeanInfo) {
						String configurationClassName = "Generated" + generation.getTypeUtils().asElement(moduleSocketInfo.getType()).getSimpleName().toString();
						result += "(" + "(" + generation.getTypeName(optionalType) +"<" + generation.getTypeName(moduleSocketInfo.getSocketType()) + ">)this.sockets.get(\"" + moduleSocketInfo.getQualifiedName().normalize() + "\")).orElse(() -> " + configurationClassName + ".DEFAULT)";
					}
					else {
						if(moduleSocketInfo.isOptional()) {
							result += "(" + generation.getTypeName(optionalType) +"<" + generation.getTypeName(moduleSocketInfo.getSocketType()) + ">)";
						}
						else {
							result += "(" + generation.getTypeName(moduleSocketInfo.getSocketType()) + ")";
						}
						result += "this.sockets.get(\"" + moduleSocketInfo.getQualifiedName().normalize() + "\")";
					}
					
					return result ;
				})
				.collect(Collectors.joining(",\n"));

			String linkerClass = generation.indent(1) + "public static class Linker extends " + generation.getTypeName(moduleLinkerType) + "<" + className + "> {" + "\n\n";
			
			linkerClass += generation.indent(2) + "public Linker(" + generation.getTypeName(mapType) + "<String, Object> sockets) {" + "\n";
			linkerClass += generation.indent(3) + "super(sockets);\n";
			linkerClass += generation.indent(2) + "}\n\n";

			
			linkerClass += generation.indent(2) + "@SuppressWarnings(\"unchecked\")\n";
			linkerClass += generation.indent(2) + "protected " + className + " link() {\n";
			linkerClass += generation.indent(3) + "return new " + className + "(\n";
			linkerClass += linker_module_args + "\n";
			linkerClass += generation.indent(3) + ");\n";
			linkerClass += generation.indent(2) + "}\n";
			
			linkerClass += generation.indent(1) + "}";
			
			return linkerClass;
		}
		else if(generation.getMode() == GenerationMode.COMPONENT_MODULE_NEW) {
			// TODO For some reason this doesn't work 100% (see TestMutiCycle)
			//TypeMirror componentModuleType = generation.getElementUtils().getTypeElement(moduleInfo.getQualifiedName().getClassName()).asType();
			TypeMirror mapType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement(Map.class.getCanonicalName()).asType());

			String import_module_arguments = Stream.concat(Arrays.stream(moduleInfo.getSockets()), Arrays.stream(moduleInfo.getConfigurations()).map(ConfigurationInfo::getSocket))
				.map(beanSocketInfo -> {
					String ret = generation.indent(3) + generation.getTypeName(mapType) + ".entry(\"" + beanSocketInfo.getQualifiedName().normalize() + "\", ";
					ret += this.visit(beanSocketInfo, generation.withMode(GenerationMode.COMPONENT_MODULE_BEAN_REFERENCE).withIndentDepth(4));
					ret += ")";
					return ret;
				})
				.collect(Collectors.joining(",\n"));
			
			String moduleNew = generation.indent(2) + "this." + moduleInfo.getQualifiedName().normalize() + " = this.with(new " + generation.getTypeName(moduleInfo.getQualifiedName().getClassName()) + ".Linker(";
			if(import_module_arguments != null && !import_module_arguments.equals("")) {
				moduleNew += generation.getTypeName(mapType) + ".ofEntries(\n"; 
				moduleNew += import_module_arguments;
				moduleNew += "\n" + generation.indent(2) + ")";
			}
			else {
				moduleNew += generation.getTypeName(mapType) + ".of()";
			}
			moduleNew += "));";
			
			return moduleNew;
		}
		else if(generation.getMode() == GenerationMode.COMPONENT_MODULE_FIELD) {
			// TODO For some reason this doesn't work 100% (see TestMutiCycle)
			/*TypeMirror componentModuleType = generation.getElementUtils().getTypeElement(moduleInfo.getQualifiedName().getClassName()).asType();
			return generation.indent(1) + "private " + generation.getTypeName(componentModuleType) + " " + moduleInfo.getQualifiedName().normalize() + ";";*/
			
			return generation.indent(1) + "private " + generation.getTypeName(moduleInfo.getQualifiedName().getClassName()) + " " + moduleInfo.getQualifiedName().normalize() + ";";
		}
		return null;
	}

	@Override
	public String visit(BeanInfo beanInfo, ModuleClassGeneration generation) {
		if(beanInfo instanceof ModuleBeanInfo) {
			return this.visit((ModuleBeanInfo)beanInfo, generation);
		}
		else if(beanInfo instanceof SocketBeanInfo) {
			return this.visit((SocketBeanInfo)beanInfo, generation);
		}
		else if(beanInfo instanceof NestedConfigurationPropertyInfo) {
			return this.visit((NestedConfigurationPropertyInfo)beanInfo, generation);
		}
		return "";
	}

	@Override
	public String visit(ModuleBeanInfo moduleBeanInfo, ModuleClassGeneration generation) {
		boolean isWrapperBean = moduleBeanInfo instanceof WrapperBeanInfo;
		if(generation.getMode() == GenerationMode.BEAN_FIELD) {
			TypeMirror moduleBeanType = generation.getTypeUtils().getDeclaredType(generation.getElementUtils().getTypeElement(WINTER_CORE_MODULE_BEAN_CLASS), moduleBeanInfo.getType());
			return generation.indent(1) + "private " + generation.getTypeName(moduleBeanType) + " " + moduleBeanInfo.getQualifiedName().normalize() + ";";
		}
		else if(generation.getMode() == GenerationMode.BEAN_ACCESSOR) {
			String beanAccessor = "";
			TypeMirror type = moduleBeanInfo.getProvidedType() != null ? moduleBeanInfo.getProvidedType() : moduleBeanInfo.getType(); 
			beanAccessor += generation.indent(1);
			beanAccessor += moduleBeanInfo.getVisibility().equals(Bean.Visibility.PUBLIC) ? "public " : "private ";
			beanAccessor += generation.getTypeName(type) + " ";
			beanAccessor += moduleBeanInfo.getQualifiedName().normalize() + "() {\n";
			
			beanAccessor += generation.indent(2);
			beanAccessor += "return this." + moduleBeanInfo.getQualifiedName().normalize() + ".get()" + ";\n";
			
			beanAccessor += generation.indent(1) + "}\n";
			
			return beanAccessor;
		}
		else if(generation.getMode() == GenerationMode.BEAN_NEW) {
			String variable = moduleBeanInfo.getQualifiedName().normalize();
			
			TypeMirror beanType = isWrapperBean ? ((WrapperBeanInfo)moduleBeanInfo).getWrapperType() : moduleBeanInfo.getType();
			TypeMirror beanBuilderType;
			if(isWrapperBean) {
				beanBuilderType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement(WINTER_CORE_MODULE_WRAPPERBEANBUILDER_CLASS).asType());
			}
			else {
				beanBuilderType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement(WINTER_CORE_MODULE_MODULEBEANBUILDER_CLASS).asType());
			}
			
			String beanNew = generation.indent(2) + "this." + variable + " = this.with(" + generation.getTypeName(beanBuilderType) + "\n";
			
			if(moduleBeanInfo.getStrategy().equals(Bean.Strategy.SINGLETON)) {
				beanNew += generation.indent(3) + ".singleton(\"" + moduleBeanInfo.getQualifiedName().getSimpleValue() + "\", () -> {\n";
			}
			else if(moduleBeanInfo.getStrategy().equals(Bean.Strategy.PROTOTYPE)) {
				beanNew += generation.indent(3) + ".prototype(\"" + moduleBeanInfo.getQualifiedName().getSimpleValue() + "\", () -> {\n";
			}
			else {
				throw new IllegalArgumentException("Unkown bean strategy: " + moduleBeanInfo.getStrategy());
			}
			
			beanNew += generation.indent(4) + generation.getTypeName(beanType) + " " + variable + " = new " + generation.getTypeName(beanType) + "(";
			if(moduleBeanInfo.getRequiredSockets().length > 0) {
				beanNew += "\n";
				beanNew += Arrays.stream(moduleBeanInfo.getRequiredSockets())
					.sorted(new Comparator<ModuleBeanSocketInfo>() {
						public int compare(ModuleBeanSocketInfo s1, ModuleBeanSocketInfo s2) {
							if(s1.getSocketElement().get() != s2.getSocketElement().get()) {
								throw new IllegalStateException("Comparing required sockets with different socket elements");
							}
							List<String> orderedDependencyNames = s1.getSocketElement().get().getParameters().stream().map(element -> element.getSimpleName().toString()).collect(Collectors.toList());
							return orderedDependencyNames.indexOf(s1.getQualifiedName().getSimpleValue()) - orderedDependencyNames.indexOf(s2.getQualifiedName().getSimpleValue());
						}
					})
					.map(socketInfo -> generation.indent(5) + (socketInfo.isLazy() ? "() -> " : "") + this.visit(socketInfo, generation.withMode(GenerationMode.BEAN_REFERENCE).withIndentDepth(5)))
					.collect(Collectors.joining(", \n"));
				beanNew += "\n" + generation.indent(4) + ");\n";
			}
			else {
				beanNew += ");\n";
			}
			// TODO: optionalSocket.ifPresent(bean::setXxx)
			beanNew += Arrays.stream(moduleBeanInfo.getOptionalSockets())
				.filter(socketInfo -> socketInfo.isResolved())
				.map(socketInfo -> generation.indent(4) + variable + "." + socketInfo.getSocketElement().get().getSimpleName().toString() + "(" + (socketInfo.isLazy() ? "() -> " : "") + this.visit(socketInfo, generation.withMode(GenerationMode.BEAN_REFERENCE).withIndentDepth(4)) + ");")
				.collect(Collectors.joining("\n")) + "\n";

			beanNew += generation.indent(4) + "return " + variable + ";\n";
			beanNew += generation.indent(3) + "})\n";

			if(moduleBeanInfo.getInitElements().length > 0) {
				beanNew += Arrays.stream(moduleBeanInfo.getInitElements())
					.map(element -> generation.indent(3) + ".init(" + generation.getTypeName(beanType) + "::" + element.getSimpleName().toString() + ")")
					.collect(Collectors.joining("\n")) + "\n";
			}
				
			if(moduleBeanInfo.getDestroyElements().length > 0) {
				beanNew += Arrays.stream(moduleBeanInfo.getDestroyElements())
					.map(element -> generation.indent(3) + ".destroy(" + generation.getTypeName(beanType) + "::" + element.getSimpleName().toString() + ")")
					.collect(Collectors.joining("\n")) + "\n";
			}	

			beanNew += generation.indent(2) + ");";
			
			return beanNew;
		}
		else if(generation.getMode() == GenerationMode.BEAN_REFERENCE) {
			if(moduleBeanInfo.getQualifiedName().getModuleQName().equals(generation.getModule())) {
				// We can't use bean accessor for internal beans since provided types are ignored inside a module
				return "this." + moduleBeanInfo.getQualifiedName().normalize() + ".get()";
//				return "this." + moduleBeanInfo.getQualifiedName().normalize() + "()";
			}
			else {
				return "this." + moduleBeanInfo.getQualifiedName().getModuleQName().normalize() + "." + moduleBeanInfo.getQualifiedName().normalize() + "()";
			}
		}
		return "";
	}

	@Override
	public String visit(WrapperBeanInfo moduleWrapperBeanInfo, ModuleClassGeneration generation) {
		return this.visit((ModuleBeanInfo)moduleWrapperBeanInfo, generation);
	}

	@Override
	public String visit(SocketInfo socketInfo, ModuleClassGeneration generation) {
		if(socketInfo instanceof SingleSocketInfo) {
			return this.visit((SingleSocketInfo)socketInfo, generation);
		}
		else if(socketInfo instanceof MultiSocketInfo) {
			return this.visit((MultiSocketInfo)socketInfo, generation);
		}
		return "";
	}
	
	@Override
	public String visit(SingleSocketInfo singleSocketInfo, ModuleClassGeneration generation) {
		if(!singleSocketInfo.isResolved()) {
			return "null";
		}
		if(generation.getMode() == GenerationMode.BEAN_REFERENCE) {
			return this.visit(singleSocketInfo.getBean(), generation);
		}
		return "";
	}

	@Override
	public String visit(MultiSocketInfo multiSocketInfo, ModuleClassGeneration generation) {
		if(!multiSocketInfo.isResolved()) {
			return "";
		}
		if(generation.getMode() == GenerationMode.BEAN_REFERENCE) {
			final TypeMirror unwildDependencyType;
			if(multiSocketInfo.getType().getKind().equals(TypeKind.WILDCARD)) {
				if(((WildcardType)multiSocketInfo.getType()).getExtendsBound() != null) {
					unwildDependencyType = ((WildcardType)multiSocketInfo.getType()).getExtendsBound();
				}
				else if(((WildcardType)multiSocketInfo.getType()).getSuperBound() != null) {
					// TODO test it I don't know precisely what will happen here
					unwildDependencyType = ((WildcardType)multiSocketInfo.getType()).getSuperBound();
				}
				else {
					unwildDependencyType = generation.getElementUtils().getTypeElement(Object.class.getCanonicalName()).asType();
				}
			}
			else {
				unwildDependencyType = multiSocketInfo.getType();
			}
			
			TypeMirror beanAggregatorType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement(WINTER_CORE_MODULE_BEANAGGREGATOR_CLASS).asType());
			
			String beanSocketReference = "new " + generation.getTypeName(beanAggregatorType) + "<" + generation.getTypeName(unwildDependencyType) + ">()\n";
			beanSocketReference += Arrays.stream(multiSocketInfo.getBeans())
				.map(beanInfo -> generation.indent(1) + ".add(" + this.visit(beanInfo, generation) + ")")
				.collect(Collectors.joining("\n")) + "\n";
			
			if(multiSocketInfo.getMultiType().equals(MultiSocketType.ARRAY)) {
				beanSocketReference += generation.indent(0) + ".toArray(" + generation.getTypeName(unwildDependencyType) + "[]::new)";
			}
			else if(multiSocketInfo.getMultiType().equals(MultiSocketType.COLLECTION) || multiSocketInfo.getMultiType().equals(MultiSocketType.LIST)) {
				beanSocketReference += generation.indent(0) + ".toList()";
			}
			else if(multiSocketInfo.getMultiType().equals(MultiSocketType.SET)) {
				beanSocketReference += generation.indent(0) + ".toSet()";
			}
			
			return beanSocketReference;
		}
		return "";
	}
	
	@Override
	public String visit(ModuleBeanSocketInfo beanSocketInfo, ModuleClassGeneration generation) {
		if(beanSocketInfo instanceof ModuleBeanSingleSocketInfo) {
			return this.visit((ModuleBeanSingleSocketInfo)beanSocketInfo, generation);
		}
		else if(beanSocketInfo instanceof ModuleBeanMultiSocketInfo) {
			return this.visit((ModuleBeanMultiSocketInfo)beanSocketInfo, generation);
		}
		return "";
	}

	@Override
	public String visit(ModuleBeanSingleSocketInfo beanSingleSocketInfo, ModuleClassGeneration generation) {
		return this.visit((SingleSocketInfo)beanSingleSocketInfo, generation);
	}

	@Override
	public String visit(ModuleBeanMultiSocketInfo beanMultiSocketInfo, ModuleClassGeneration generation) {
		return this.visit((MultiSocketInfo)beanMultiSocketInfo, generation);
	}

	@Override
	public String visit(SocketBeanInfo socketBeanInfo, ModuleClassGeneration generation) {
		if(generation.getMode() == GenerationMode.SOCKET_PARAMETER) {
			if(socketBeanInfo instanceof ConfigurationSocketBeanInfo) {
				return this.visit((ConfigurationSocketBeanInfo)socketBeanInfo, generation);
			}
			else {
				String socketParameter = "";
				if(socketBeanInfo.getWiredBeans().length > 0) {
					TypeMirror wiredToType = generation.getElementUtils().getTypeElement(WiredTo.class.getCanonicalName()).asType();
					socketParameter += "@" + generation.getTypeName(wiredToType) + "({" + Arrays.stream(socketBeanInfo.getWiredBeans()).map(beanQName -> "\"" + beanQName.getSimpleValue() + "\"").collect(Collectors.joining(", ")) + "}) ";
				}
				
				if(socketBeanInfo.getSelectors().length > 0) {
					// TODO use a recursive method to add imports and reduce the generated line
					socketParameter += Arrays.stream(socketBeanInfo.getSelectors()).map(selector -> selector.toString()).collect(Collectors.joining(", ")) + " ";
				}
				
				if(socketBeanInfo.isOptional()) {
					TypeMirror optionalType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement(Optional.class.getCanonicalName()).asType());
					socketParameter += generation.getTypeName(optionalType) + "<" + generation.getTypeName(socketBeanInfo.getSocketType()) + ">";
				}
				else {
					socketParameter += generation.getTypeName(socketBeanInfo.getSocketType());
				}
				socketParameter += " " + socketBeanInfo.getQualifiedName().normalize();
				
				return socketParameter;
			}
		}
		else if(generation.getMode() == GenerationMode.SOCKET_FIELD) {
			if(socketBeanInfo instanceof ConfigurationSocketBeanInfo) {
				return this.visit((ConfigurationSocketBeanInfo)socketBeanInfo, generation);
			}
			else {
				TypeMirror optionalType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement(Optional.class.getCanonicalName()).asType());
				if(socketBeanInfo.isOptional()) {
					return generation.indent(2) + "private " + generation.getTypeName(optionalType) + "<" + generation.getTypeName(socketBeanInfo.getSocketType()) + "> " + socketBeanInfo.getQualifiedName().normalize() + " = " + generation.getTypeName(optionalType) + ".empty();";
				}
				else {
					return generation.indent(2) + "private " + generation.getTypeName(socketBeanInfo.getSocketType()) + " " + socketBeanInfo.getQualifiedName().normalize() + ";";
				}
			}
		}
		else if(generation.getMode() == GenerationMode.SOCKET_ASSIGNMENT) {
			return generation.indent(3) + "this." + socketBeanInfo.getQualifiedName().normalize() + " = () -> " + socketBeanInfo.getQualifiedName().normalize() + ";";
		}
		else if(generation.getMode() == GenerationMode.SOCKET_INJECTOR) {
			if(socketBeanInfo instanceof ConfigurationSocketBeanInfo) {
				return this.visit((ConfigurationSocketBeanInfo)socketBeanInfo, generation);
			}
			else {
				TypeMirror optionalType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement(Optional.class.getCanonicalName()).asType());
				String plugName = socketBeanInfo.getQualifiedName().normalize();
				
				String result = generation.indent(2) + "public Builder set" + Character.toUpperCase(plugName.charAt(0)) + plugName.substring(1) + "(" + (socketBeanInfo instanceof MultiSocketInfo ? generation.getMultiTypeName(socketBeanInfo.getType(), ((MultiSocketInfo)socketBeanInfo).getMultiType() ) : generation.getTypeName(socketBeanInfo.getType())) + " " + plugName + ") {\n";
				if(socketBeanInfo.isOptional()) {
					result += generation.indent(3) + "this." + plugName + " = " + generation.getTypeName(optionalType) + ".ofNullable(" + plugName + " != null ? () -> " + plugName + " : null);\n";
				}
				else {
					result += generation.indent(3) + "this." + plugName + " = () -> " + plugName + ";\n";
				}
				result += generation.indent(3) + "return this;\n";
				result += generation.indent(2) + "}\n";
				
				return result;
			}
		}
		else if(generation.getMode() == GenerationMode.BEAN_REFERENCE) {
			if(socketBeanInfo instanceof ConfigurationSocketBeanInfo) {
				return this.visit((ConfigurationSocketBeanInfo)socketBeanInfo, generation);
			}
			else {
				if(socketBeanInfo.isOptional()) {
					return socketBeanInfo.getQualifiedName().normalize() + ".orElse(() -> null).get()";
				}
				else {
					return socketBeanInfo.getQualifiedName().normalize() + ".get()";
				}
			}
		}
		else if(generation.getMode() == GenerationMode.COMPONENT_MODULE_BEAN_REFERENCE) {
			TypeMirror optionalType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement(Optional.class.getCanonicalName()).asType());

			if(socketBeanInfo instanceof ConfigurationSocketBeanInfo && ((ConfigurationSocketBeanInfo)socketBeanInfo).getBean() instanceof NestedConfigurationPropertyInfo) {
				return this.visit((ConfigurationSocketBeanInfo)socketBeanInfo, generation);
			}
			
			
			if(!socketBeanInfo.isOptional() || socketBeanInfo.isResolved()) {
				String result = "";
				if(socketBeanInfo instanceof SingleSocketInfo) {
					result += this.visit((SingleSocketBeanInfo)socketBeanInfo, generation);
				}
				else if(socketBeanInfo instanceof MultiSocketInfo) {
					result += this.visit((MultiSocketBeanInfo)socketBeanInfo, generation);
				}
				
				if(socketBeanInfo.isOptional()) {
					return generation.getTypeName(optionalType) + ".of(" + result + ")";
				}
				return result;
			}
			else {
				return generation.getTypeName(optionalType) + ".empty()";
			}
			
			
			/*String result = "";
			if(socketBeanInfo instanceof SingleSocketInfo) {
				result += this.visit((SingleSocketBeanInfo)socketBeanInfo, generation);
			}
			else if(socketBeanInfo instanceof MultiSocketInfo) {
				result += this.visit((MultiSocketBeanInfo)socketBeanInfo, generation);
			}
			
			if(!socketBeanInfo.isOptional()) {
				return result;
			}
			else if(socketBeanInfo.isResolved()) {
				return generation.getTypeName(optionalType) + ".of(" + result + ")";
			}
			else {
				return generation.getTypeName(optionalType) + ".empty()";
			}*/
		}
		return "";
	}

	@Override
	public String visit(SingleSocketBeanInfo singleSocketBeanInfo, ModuleClassGeneration generation) {
		if(generation.getMode() == GenerationMode.COMPONENT_MODULE_BEAN_REFERENCE) {
			return "(" + generation.getTypeName(singleSocketBeanInfo.getSocketType()) + ")" + (singleSocketBeanInfo.isResolved() ? "() -> " + this.visit((SingleSocketInfo)singleSocketBeanInfo, generation.withMode(GenerationMode.BEAN_REFERENCE)) : "null");
		}
		return "";
	}

	@Override
	public String visit(MultiSocketBeanInfo multiSocketBeanInfo, ModuleClassGeneration generation) {
		if(generation.getMode() == GenerationMode.COMPONENT_MODULE_BEAN_REFERENCE) {
			return "(" + generation.getTypeName(multiSocketBeanInfo.getSocketType()) + ")" + (multiSocketBeanInfo.isResolved() ? "() -> " + this.visit((MultiSocketInfo)multiSocketBeanInfo, generation.withMode(GenerationMode.BEAN_REFERENCE)) : "null");
		}
		return null;
	}

	@Override
	public String visit(ConfigurationInfo configurationInfo, ModuleClassGeneration generation) {
		String configurationClassName = "Generated" + generation.getTypeUtils().asElement(configurationInfo.getType()).getSimpleName().toString();
		String configurationBuilderClassName = generation.getTypeUtils().asElement(configurationInfo.getType()).getSimpleName().toString() + "Builder";
		if(generation.getMode() == GenerationMode.CONFIGURATION_CLASS) {
			// private static final ModAConfig DEFAULT = new GeneratedModAConfig();
			String configuration_field_default = generation.indent(2) + "private static final " + generation.getTypeName(configurationInfo.getType()) + " DEFAULT = new " + configurationClassName + "();";
			
			String configuration_field_properties = Arrays.stream(configurationInfo.getProperties())
				.map(configurationPropertyInfo -> this.visit(configurationPropertyInfo, generation.withMode(GenerationMode.CONFIGURATION_PROPERTY_FIELD)))
				.collect(Collectors.joining("\n"));
			
			String configuration_default_constructor = generation.indent(2) + "private " + configurationClassName + "() {}";
			
			String configuration_constructor = "";
			if(configurationInfo.getProperties().length > 0) {
				configuration_constructor += generation.indent(2) + "public " + configurationClassName + "(";
				configuration_constructor += Arrays.stream(configurationInfo.getProperties()).map(configurationPropertyInfo -> this.visit(configurationPropertyInfo, generation.withMode(GenerationMode.CONFIGURATION_PROPERTY_PARAMETER))).collect(Collectors.joining(", "));
				configuration_constructor += ") {\n";
				configuration_constructor += Arrays.stream(configurationInfo.getProperties()).map(configurationPropertyInfo -> this.visit(configurationPropertyInfo, generation.withMode(GenerationMode.CONFIGURATION_PROPERTY_ASSIGNMENT))).collect(Collectors.joining("\n"));
				configuration_constructor += "\n";
				configuration_constructor += generation.indent(2) + "}";
			}
			
			String configuration_property_accessors = Arrays.stream(configurationInfo.getProperties())
				.map(configurationPropertyInfo -> this.visit(configurationPropertyInfo, generation.withMode(GenerationMode.CONFIGURATION_PROPERTY_ACCESSOR)))
				.collect(Collectors.joining("\n"));
			
			
			String configurationClass = generation.indent(1) + "private static final class " + configurationClassName + " implements " + generation.getTypeName(configurationInfo.getType()) + " {\n\n";
			
			configurationClass += configuration_field_default + "\n\n";
			configurationClass += configuration_field_properties + "\n\n";
			configurationClass += configuration_default_constructor + "\n\n";
			if(!configuration_constructor.equals("")) {
				configurationClass += configuration_constructor + "\n\n";
			}
			configurationClass += configuration_property_accessors;
			
			configurationClass += generation.indent(1) + "}";
			
			return configurationClass;
		}
		else if(generation.getMode() == GenerationMode.CONFIGURATION_BUILDER_CLASS) {
			TypeMirror consumerType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement(Consumer.class.getCanonicalName()).asType());
			
			String configuration_builder_field_properties = Arrays.stream(configurationInfo.getProperties())
				.map(configurationPropertyInfo -> this.visit(configurationPropertyInfo, generation.withMode(GenerationMode.CONFIGURATION_BUILDER_PROPERTY_FIELD)))
				.collect(Collectors.joining("\n"));
			
			String configuration_builder_default_constructor = generation.indent(2) + "private " + configurationBuilderClassName + "() {}";
			
			String configuration_builder_build_method = generation.indent(2) + "private " + generation.getTypeName(configurationInfo.getType()) + " build() {\n";
			configuration_builder_build_method += generation.indent(3) + "return new " + configurationClassName + "(";
			configuration_builder_build_method += Arrays.stream(configurationInfo.getProperties())
				.map(configurationPropertyInfo -> "this." + configurationPropertyInfo.getName())
				.collect(Collectors.joining(", "));
			configuration_builder_build_method += ");\n";
			configuration_builder_build_method += generation.indent(2) + "}";
			
			String configuration_builder_static_build_method = generation.indent(2) + "public static " + generation.getTypeName(configurationInfo.getType()) + " build(" + generation.getTypeName(consumerType) + "<" + configurationBuilderClassName + "> configurator) {\n";
			configuration_builder_static_build_method += generation.indent(3) + configurationBuilderClassName + " builder = new " + configurationBuilderClassName + "();\n";
			configuration_builder_static_build_method += generation.indent(3) + "configurator.accept(builder);\n";
			configuration_builder_static_build_method += generation.indent(3) + "return builder.build();\n";
			configuration_builder_static_build_method += generation.indent(2) + "}";
			
			String configuration_property_injectors = Arrays.stream(configurationInfo.getProperties())
				.map(configurationPropertyInfo -> this.visit(configurationPropertyInfo, generation.withMode(GenerationMode.CONFIGURATION_BUILDER_PROPERTY_INJECTOR)))
				.collect(Collectors.joining("\n"));
			
			String configurationBuilderClass = generation.indent(1) + "public static final class " + configurationBuilderClassName + " {\n\n";
			
			configurationBuilderClass += configuration_builder_field_properties + "\n\n";
			configurationBuilderClass += configuration_builder_default_constructor + "\n\n";
			configurationBuilderClass += configuration_builder_build_method + "\n\n";
			configurationBuilderClass += configuration_builder_static_build_method + "\n\n";
			configurationBuilderClass += configuration_property_injectors;
			
			configurationBuilderClass += generation.indent(1) + "}";
			
			return configurationBuilderClass;
		}
		return "";
	}
	
	@Override
	public String visit(ConfigurationPropertyInfo configurationPropertyInfo, ModuleClassGeneration generation) {
		if(generation.getMode() == GenerationMode.CONFIGURATION_PROPERTY_FIELD) {
			String result = generation.indent(2) + "private " + generation.getTypeName(configurationPropertyInfo.getType()) + " " + configurationPropertyInfo.getName();
			if(configurationPropertyInfo.isDefault()) {
				result += " = " + generation.getTypeName(configurationPropertyInfo.getConfiguration().getType()) + ".super." + configurationPropertyInfo.getName() + "()";
			}
			else if(configurationPropertyInfo instanceof NestedConfigurationPropertyInfo) {
				result += " = " + generation.getTypeName(((NestedConfigurationPropertyInfo)configurationPropertyInfo).getBuilderClassName()) + ".build(builder -> {})";
			}
			result += ";";
			return result;
		}
		else if(generation.getMode() == GenerationMode.CONFIGURATION_PROPERTY_PARAMETER) {
			TypeMirror boxedType = configurationPropertyInfo.getType() instanceof PrimitiveType ? generation.getTypeUtils().boxedClass((PrimitiveType)configurationPropertyInfo.getType()).asType() : configurationPropertyInfo.getType();
			return generation.getTypeName(generation.getTypeUtils().getDeclaredType(generation.getElementUtils().getTypeElement(Supplier.class.getCanonicalName()), boxedType)) + " " +configurationPropertyInfo.getName();
		}
		else if(generation.getMode() == GenerationMode.CONFIGURATION_PROPERTY_ASSIGNMENT) {
			TypeMirror optionalType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement(Optional.class.getCanonicalName()).asType());
			return generation.indent(3) + generation.getTypeName(optionalType) + ".ofNullable(" + configurationPropertyInfo.getName() + ").ifPresent(s -> this." + configurationPropertyInfo.getName() + " = s.get());";
		}
		else if(generation.getMode() == GenerationMode.CONFIGURATION_PROPERTY_ACCESSOR) {
			String result = generation.indent(2) + "public " + generation.getTypeName(configurationPropertyInfo.getType()) + " " + configurationPropertyInfo.getName() +"() {\n";
			result += generation.indent(3) + " return this." + configurationPropertyInfo.getName() + ";\n";
			result += generation.indent(2) + "}\n";
			return result;
		}
		else if(generation.getMode() == GenerationMode.CONFIGURATION_BUILDER_PROPERTY_FIELD) {
			TypeMirror boxedType = configurationPropertyInfo.getType() instanceof PrimitiveType ? generation.getTypeUtils().boxedClass((PrimitiveType)configurationPropertyInfo.getType()).asType() : configurationPropertyInfo.getType();
			return generation.indent(2) + "private " + generation.getTypeName(generation.getTypeUtils().getDeclaredType(generation.getElementUtils().getTypeElement(Supplier.class.getCanonicalName()), boxedType)) + " " + configurationPropertyInfo.getName() + ";";
		}
		else if(generation.getMode() == GenerationMode.CONFIGURATION_BUILDER_PROPERTY_INJECTOR) {
			String configurationBuilderClassName = generation.getTypeUtils().asElement(configurationPropertyInfo.getConfiguration().getType()).getSimpleName().toString() + "Builder";
			
			String result = generation.indent(2) + "public " + configurationBuilderClassName + " " + configurationPropertyInfo.getName() +"(" + generation.getTypeName(configurationPropertyInfo.getType()) + " " + configurationPropertyInfo.getName() + ") {\n";
			result += generation.indent(3) + "this." + configurationPropertyInfo.getName() + " = () -> " + configurationPropertyInfo.getName() + ";\n";
			result += generation.indent(3) + "return this;\n";
			result += generation.indent(2) + "}\n";
			if(configurationPropertyInfo instanceof NestedConfigurationPropertyInfo) {
				result += "\n" + this.visit((NestedConfigurationPropertyInfo)configurationPropertyInfo, generation);
			}
			return result;
		}
		return "";
	}
	
	
	@Override
	public String visit(ConfigurationSocketBeanInfo configurationSocketBeanInfo, ModuleClassGeneration generation) {
		if(generation.getMode() == GenerationMode.SOCKET_FIELD) {
			String configurationClassName = "Generated" + generation.getTypeUtils().asElement(configurationSocketBeanInfo.getType()).getSimpleName().toString();
			return generation.indent(2) + "private " + generation.getTypeName(configurationSocketBeanInfo.getSocketType()) + " " + configurationSocketBeanInfo.getQualifiedName().normalize() + " = () -> " + configurationClassName + ".DEFAULT;";
		}
		else if(generation.getMode() == GenerationMode.SOCKET_PARAMETER) {
			String socketParameter = "";
			if(configurationSocketBeanInfo.getWiredBeans().length > 0) {
				TypeMirror wiredToType = generation.getElementUtils().getTypeElement(WiredTo.class.getCanonicalName()).asType();
				socketParameter += "@" + generation.getTypeName(wiredToType) + "({" + Arrays.stream(configurationSocketBeanInfo.getWiredBeans()).map(beanQName -> "\"" + beanQName.getSimpleValue() + "\"").collect(Collectors.joining(", ")) + "}) ";
			}
			socketParameter += generation.getTypeName(configurationSocketBeanInfo.getSocketType());
			socketParameter += " " + configurationSocketBeanInfo.getQualifiedName().normalize();
			
			return socketParameter;
		}
		else if(generation.getMode() == GenerationMode.SOCKET_INJECTOR) {
			String configurationBuilderClassName = generation.getTypeUtils().asElement(configurationSocketBeanInfo.getType()).getSimpleName().toString() + "Builder";
			TypeMirror consumerType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement(Consumer.class.getCanonicalName()).asType());
			TypeMirror optionalType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement(Optional.class.getCanonicalName()).asType());
			TypeMirror npeType = generation.getElementUtils().getTypeElement(NullPointerException.class.getCanonicalName()).asType();

			String plugName = configurationSocketBeanInfo.getQualifiedName().normalize();
			
			String result = generation.indent(2) + "public Builder set" + Character.toUpperCase(plugName.charAt(0)) + plugName.substring(1) + "(" + generation.getTypeName(configurationSocketBeanInfo.getType()) + " " + plugName + ") {\n";
			
			result += generation.indent(3) + "this." + plugName + " = " + generation.getTypeName(optionalType) + ".ofNullable(" + plugName + ")\n";
			result += generation.indent(4) + ".map(config -> (" + generation.getTypeName(configurationSocketBeanInfo.getSocketType()) + ")() -> config)\n";
			result += generation.indent(4) + ".orElseThrow(" + generation.getTypeName(npeType) + "::new);\n";
			result += generation.indent(3) + "return this;\n";
			result += generation.indent(2) + "}\n\n";
			result += generation.indent(2) + "public Builder set" + Character.toUpperCase(plugName.charAt(0)) + plugName.substring(1) + "(" + generation.getTypeName(consumerType) + "<" + configurationBuilderClassName +"> " + plugName + "_configurator) {\n";
			
			result += generation.indent(3) + "this." + plugName + " = " + generation.getTypeName(optionalType) + ".ofNullable(" + plugName + "_configurator)\n";
			result += generation.indent(4) + ".map(configurator -> " + configurationBuilderClassName + ".build(configurator))\n";
			result += generation.indent(4) + ".map(config -> (" + generation.getTypeName(configurationSocketBeanInfo.getSocketType()) + ")() -> config)\n";
			result += generation.indent(4) + ".orElseThrow(" + generation.getTypeName(npeType) + "::new);\n";
			result += generation.indent(3) + "return this;\n";
			result += generation.indent(2) + "}\n";
			
			return result;
		}
		else if(generation.getMode() == GenerationMode.BEAN_REFERENCE) {
			return configurationSocketBeanInfo.getQualifiedName().normalize() + ".get()";
		}
		else if(generation.getMode() == GenerationMode.COMPONENT_MODULE_BEAN_REFERENCE) {
			if(configurationSocketBeanInfo.getBean() instanceof NestedConfigurationPropertyInfo) {
				TypeMirror optionalType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement(Optional.class.getCanonicalName()).asType());
				TypeMirror npeType = generation.getElementUtils().getTypeElement(NullPointerException.class.getCanonicalName()).asType();
				
				NestedConfigurationPropertyInfo bean = (NestedConfigurationPropertyInfo)configurationSocketBeanInfo.getBean();
				
				return generation.getTypeName(optionalType) + ".ofNullable(" + bean.getConfiguration().getQualifiedName().normalize() + ".get()." + bean.getName() + "()).map(config -> " + generation.getTypeName(optionalType) + ".of((" + generation.getTypeName(configurationSocketBeanInfo.getSocketType()) + ")() -> config)).orElseThrow(() -> new " + generation.getTypeName(npeType) + "(\"" + bean.getQualifiedName().getSimpleValue() + "\"))";  
			}
			else {
				return this.visit((SocketBeanInfo)configurationSocketBeanInfo, generation);
			}
		}
		return "";
	}
	
	@Override
	public String visit(NestedConfigurationPropertyInfo nestedConfigurationPropertyInfo, ModuleClassGeneration generation) {
		if(generation.getMode() == GenerationMode.CONFIGURATION_BUILDER_PROPERTY_INJECTOR) {
			String configurationBuilderClassName = generation.getTypeUtils().asElement(nestedConfigurationPropertyInfo.getConfiguration().getType()).getSimpleName().toString() + "Builder";
			
			TypeMirror consumerType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement(Consumer.class.getCanonicalName()).asType());
			
			String result = generation.indent(2) + "public " + configurationBuilderClassName + " " + nestedConfigurationPropertyInfo.getName() +"(" + generation.getTypeName(consumerType) + "<" + nestedConfigurationPropertyInfo.getBuilderClassName() + "> " + nestedConfigurationPropertyInfo.getName() + "_configurator) {\n";
			result += generation.indent(3) + "this." + nestedConfigurationPropertyInfo.getName() + " = () -> " + generation.getTypeName(nestedConfigurationPropertyInfo.getBuilderClassName()) + ".build(" + nestedConfigurationPropertyInfo.getName() + "_configurator);\n";
			result += generation.indent(3) + "return this;\n";
			result += generation.indent(2) + "}\n";
			
			return result;
		}
		else if(generation.getMode() == GenerationMode.BEAN_REFERENCE) {
			return nestedConfigurationPropertyInfo.getConfiguration().getQualifiedName().normalize() + ".get()." + nestedConfigurationPropertyInfo.getName() + "()";
		}
		return "";
	}
}
