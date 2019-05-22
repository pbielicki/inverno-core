package io.winterframework.core.compiler;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;

import io.winterframework.core.annotation.Bean;
import io.winterframework.core.annotation.Scope;
import io.winterframework.core.compiler.ModuleClassGeneration.GenerationMode;
import io.winterframework.core.compiler.spi.BeanInfo;
import io.winterframework.core.compiler.spi.ModuleBeanInfo;
import io.winterframework.core.compiler.spi.ModuleBeanMultiSocketInfo;
import io.winterframework.core.compiler.spi.ModuleBeanSingleSocketInfo;
import io.winterframework.core.compiler.spi.ModuleBeanSocketInfo;
import io.winterframework.core.compiler.spi.ModuleInfo;
import io.winterframework.core.compiler.spi.ModuleInfoVisitor;
import io.winterframework.core.compiler.spi.MultiSocketBeanInfo;
import io.winterframework.core.compiler.spi.MultiSocketInfo;
import io.winterframework.core.compiler.spi.MultiSocketType;
import io.winterframework.core.compiler.spi.SingleSocketBeanInfo;
import io.winterframework.core.compiler.spi.SingleSocketInfo;
import io.winterframework.core.compiler.spi.SocketBeanInfo;
import io.winterframework.core.compiler.spi.SocketInfo;
import io.winterframework.core.compiler.spi.WrapperBeanInfo;

class ModuleClassGenerator implements ModuleInfoVisitor<String, ModuleClassGeneration> {

	@Override
	public String visit(ModuleInfo moduleInfo, ModuleClassGeneration generation) {
		String className = moduleInfo.getQualifiedName().getClassName();
		String packageName = className.lastIndexOf(".") != -1 ? className.substring(0, className.lastIndexOf(".")) : "";
		className = className.substring(packageName.length() + 1);
		
		if(generation.getMode() == GenerationMode.MODULE_CLASS) {
			TypeMirror generatedType = generation.getElementUtils().getTypeElement(generation.getElementUtils().getModuleElement("java.compiler"), "javax.annotation.processing.Generated").asType();
			TypeMirror moduleType = generation.getElementUtils().getTypeElement("io.winterframework.core.Module").asType();

			generation.addImport(className, moduleInfo.getQualifiedName().getClassName());
			generation.addImport("Builder", moduleInfo.getQualifiedName().getClassName() + ".Builder");
			
			// Fields
			String module_field_beans = Arrays.stream(moduleInfo.getBeans())
				.map(moduleBeanInfo -> this.visit(moduleBeanInfo, generation.forModule(moduleInfo.getQualifiedName()).withMode(GenerationMode.BEAN_FIELD)))
				.collect(Collectors.joining("\n"));
			String module_field_modules = Arrays.stream(moduleInfo.getModules())
				.map(importedModuleInfo -> this.visit(importedModuleInfo, generation.forModule(moduleInfo.getQualifiedName()).withMode(GenerationMode.IMPORT_MODULE_FIELD)))
				.collect(Collectors.joining("\n"));
			
			String module_constructor_parameters = Arrays.stream(moduleInfo.getSockets())
				.map(socketInfo -> this.visit(socketInfo , generation.forModule(moduleInfo.getQualifiedName()).withMode(GenerationMode.SOCKET_PARAMETER)))
				.collect(Collectors.joining(", "));
			
			String module_constructor_modules = Arrays.stream(moduleInfo.getModules())
				.map(importedModuleInfo -> this.visit(importedModuleInfo, generation.forModule(moduleInfo.getQualifiedName()).withMode(GenerationMode.IMPORT_MODULE_NEW)))
				.collect(Collectors.joining("\n"));
			
			String module_constructor_beans = Arrays.stream(moduleInfo.getBeans())
				.map(moduleBeanInfo -> this.visit(moduleBeanInfo, generation.forModule(moduleInfo.getQualifiedName()).withMode(GenerationMode.BEAN_NEW)))
				.collect(Collectors.joining("\n"));
			
			String module_method_beans = Arrays.stream(moduleInfo.getBeans())
				.map(moduleBeanInfo -> this.visit(moduleBeanInfo, generation.forModule(moduleInfo.getQualifiedName()).withMode(GenerationMode.BEAN_ACCESSOR)))
				.collect(Collectors.joining("\n"));
			
			String module_creator = this.visit(moduleInfo, generation.forModule(moduleInfo.getQualifiedName()).withMode(GenerationMode.MODULE_CREATOR_CLASS));
			String module_builder = this.visit(moduleInfo, generation.forModule(moduleInfo.getQualifiedName()).withMode(GenerationMode.MODULE_BUILDER_CLASS));

			String moduleClass = "";

			if(!packageName.equals("")) {
				moduleClass += "package " + packageName + ";" + "\n\n";
			}
			
			generation.removeImport(className);
			generation.removeImport("Builder");
			generation.removeImport("ModuleCreator");
			generation.removeImport("ModuleBuilder");
			generation.removeImport("BeanBuilder");
			generation.removeImport("Bean");
			
			generation.getTypeName(generatedType);
			generation.getTypeName(moduleType);
			
			moduleClass += generation.getImports().stream().sorted().map(i -> "import " + i + ";").collect(Collectors.joining("\n")) + "\n\n";
			
			// TODO add version
			moduleClass += "@" + generation.getTypeName(generatedType) + "(\"" + ModuleAnnotationProcessor.class.getCanonicalName() + "\")\n";
			moduleClass += "public class " + className + " extends " + generation.getTypeName(moduleType) + " {" + "\n\n";

			if(module_field_modules != null && !module_field_modules.equals("")) {
				moduleClass += module_field_modules + "\n\n";
			}
			if(module_field_beans != null && !module_field_beans.equals("")) {
				moduleClass += module_field_beans + "\n\n";
			}
			
			moduleClass += generation.indent(1) + "public " + className + "(" + module_constructor_parameters + ") {\n";
			moduleClass += generation.indent(2) + "super(\"" + moduleInfo.getQualifiedName().getValue() + "\");\n\n";
			
			if(module_constructor_modules != null && !module_constructor_modules.equals("")) {
				moduleClass += module_constructor_modules + "\n\n";
			}
			if(module_constructor_beans != null && !module_constructor_beans.equals("")) {
				moduleClass += module_constructor_beans + "\n\n";
			}
			
			moduleClass += generation.indent(1) + "}\n";
			
			if(module_method_beans != null && !module_method_beans.equals("")) {
				moduleClass += module_method_beans;
			}
			
			moduleClass += module_creator + "\n\n";
			moduleClass += module_builder + "\n\n";
			
			moduleClass += "}\n";
			
			return moduleClass;
		}
		else if(generation.getMode() == GenerationMode.MODULE_CREATOR_CLASS) {
			TypeMirror moduleCreatorType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement("io.winterframework.core.Module.ModuleCreator").asType());
			TypeMirror mapType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement("java.util.Map").asType());
			TypeMirror optionalType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement("java.util.Optional").asType());
			
			String creator_with_parameters = Arrays.stream(moduleInfo.getSockets())
				.filter(moduleSocketInfo -> !moduleSocketInfo.isOptional())
				.map(moduleSocketInfo -> generation.getTypeName(moduleSocketInfo.getType()) + " " + moduleSocketInfo.getQualifiedName().normalize())
				.collect(Collectors.joining(", "));
			
			String creator_with_args = Arrays.stream(moduleInfo.getSockets())
				.filter(moduleSocketInfo -> !moduleSocketInfo.isOptional())
				.map(moduleSocketInfo -> moduleSocketInfo.getQualifiedName().normalize())
				.collect(Collectors.joining(", "));
			
			String creator_create_args = Arrays.stream(moduleInfo.getSockets())
				.map(moduleSocketInfo -> {
					String result = "";
					if(moduleSocketInfo.isOptional()) {
						result += "(" + generation.getTypeName(optionalType) +"<" + generation.getTypeName(moduleSocketInfo.getSocketType()) + ">)";
					}
					else {
						result += "(" + generation.getTypeName(moduleSocketInfo.getSocketType()) + ")";
					}
					result += "args.get(\"" + moduleSocketInfo.getQualifiedName().normalize() + "\")";
					return result ;
				})
				.collect(Collectors.joining(", "));

			String creatorClass = generation.indent(1) + "public static class Creator extends " + generation.getTypeName(moduleCreatorType) + "<" + className + "> {" + "\n\n";
			
			creatorClass += generation.indent(2) + "public static Builder with(" + creator_with_parameters + ") {" + "\n";
			creatorClass += generation.indent(3) + "return new Builder(" + creator_with_args + ");\n";
			creatorClass += generation.indent(2) + "}\n\n";

			creatorClass += generation.indent(2) + "protected " + className + " create(" + generation.getTypeName(mapType) + "<String, Object> args) {\n";
			creatorClass += generation.indent(3) + "return new " + className + "(" + creator_create_args + ");\n";
			creatorClass += generation.indent(2) + "}\n";
			
			creatorClass += generation.indent(1) + "}\n";
			
			return creatorClass;
		}
		else if(generation.getMode() == GenerationMode.MODULE_BUILDER_CLASS) {
			TypeMirror moduleBuilderType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement("io.winterframework.core.Module.ModuleBuilder").asType());
			TypeMirror optionalType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement("java.util.Optional").asType());
			
			String module_builder_fields = Arrays.stream(moduleInfo.getSockets())
				.map(moduleSocketInfo -> this.visit(moduleSocketInfo, generation.forModule(moduleInfo.getQualifiedName()).withMode(GenerationMode.SOCKET_FIELD)))
				.collect(Collectors.joining("\n"));
			
			String module_builder_constructor_parameters = Arrays.stream(moduleInfo.getSockets())
				.filter(moduleSocketInfo -> !moduleSocketInfo.isOptional())
				.map(moduleSocketInfo -> generation.getTypeName(moduleSocketInfo.getType()) + " " + moduleSocketInfo.getQualifiedName().normalize())
				.collect(Collectors.joining(", "));
			
			String module_builder_constructor = Arrays.stream(moduleInfo.getSockets())
				.filter(moduleSocketInfo -> !moduleSocketInfo.isOptional())
				.map(moduleSocketInfo -> this.visit(moduleSocketInfo, generation.forModule(moduleInfo.getQualifiedName()).withMode(GenerationMode.SOCKET_ASSIGNMENT)))
				.collect(Collectors.joining("\n"));

			String module_builder_build_args = Arrays.stream(moduleInfo.getSockets())
				.map(moduleSocketInfo -> {
					// TODO required socket have to be provided in the builder constructor as a result there shouldn't be a need for this for required socket at least
					String result = "this." + moduleSocketInfo.getQualifiedName().normalize() + " != null ? () -> this." + moduleSocketInfo.getQualifiedName().normalize() + " : null";
					if(moduleSocketInfo.isOptional()) {
						result = generation.getTypeName(optionalType) + ".ofNullable(" + result + ")";
					}
					return result;
				})
				.collect(Collectors.joining(", "));
			
			String module_builder_socket_methods = Arrays.stream(moduleInfo.getSockets())
				.filter(moduleSocketInfo -> moduleSocketInfo.isOptional())
				.map(moduleSocketInfo -> this.visit(moduleSocketInfo, generation.forModule(moduleInfo.getQualifiedName()).withMode(GenerationMode.SOCKET_INJECTOR)))
				.collect(Collectors.joining("\n"));

			String moduleBuilderClass = generation.indent(1) + "public static class Builder extends " + generation.getTypeName(moduleBuilderType) + "<" + className + "> {\n\n";
			if(module_builder_fields != null && !module_builder_fields.equals("")) {
				moduleBuilderClass += module_builder_fields + "\n\n";
			}
			if(module_builder_constructor_parameters != null && !module_builder_constructor_parameters.equals("")) {
				moduleBuilderClass += generation.indent(2) + "protected Builder(" + module_builder_constructor_parameters + ") {\n";
				moduleBuilderClass += module_builder_constructor + "\n";
				moduleBuilderClass += generation.indent(2) + "}\n\n";
			}
			
			moduleBuilderClass += generation.indent(2) + "protected " + className + " doBuild() {\n";
			moduleBuilderClass += generation.indent(3) + "return new " + className + "(" + module_builder_build_args + ");\n";
			moduleBuilderClass += generation.indent(2) + "}\n";
			
			if(module_builder_socket_methods != null && !module_builder_socket_methods.equals("")) {
				moduleBuilderClass += module_builder_socket_methods + "\n";
			}
			
			moduleBuilderClass += generation.indent(1) + "}";
			
			return moduleBuilderClass;
		}
		else if(generation.getMode() == GenerationMode.IMPORT_MODULE_NEW) {
			TypeMirror importedModuleType = generation.getElementUtils().getTypeElement(moduleInfo.getQualifiedName().getClassName()).asType();
			TypeMirror mapType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement("java.util.Map").asType());

			String import_module_arguments = Arrays.stream(moduleInfo.getSockets())
				.map(beanSocketInfo -> {
					// TODO we must determine whether BEAN_REFERENCE or IMPORT_BEAN_REFERENCE, the only way would be to keep the module being built in the generation... but it sucks
					String ret = generation.getTypeName(mapType) + ".entry(\"" + beanSocketInfo.getQualifiedName().normalize() + "\", ";
					ret += this.visit(beanSocketInfo, generation.withMode(GenerationMode.IMPORT_BEAN_REFERENCE));
					ret += ")";
					return ret;
				})
				.collect(Collectors.joining(", "));
			
			String moduleNew = generation.indent(2) + "this." + moduleInfo.getQualifiedName().normalize() + " = this.with(new " + generation.getTypeName(importedModuleType) + ".Creator()";
			if(import_module_arguments != null && !import_module_arguments.equals("")) {
				moduleNew += ", " + generation.getTypeName(mapType) + ".ofEntries(" + import_module_arguments + ")";
			}
			else {
				moduleNew += ", " + generation.getTypeName(mapType) + ".of()";
			}
			moduleNew += ");\n";
			
			return moduleNew;
		}
		else if(generation.getMode() == GenerationMode.IMPORT_MODULE_FIELD) {
			TypeMirror importedModuleType = generation.getElementUtils().getTypeElement(moduleInfo.getQualifiedName().getClassName()).asType();
			return generation.indent(1) + "private " + generation.getTypeName(importedModuleType) + " " + moduleInfo.getQualifiedName().normalize() + ";"; 
		}
		return null;
	}

	@Override
	public String visit(BeanInfo beanInfo, ModuleClassGeneration generation) {
		if(ModuleBeanInfo.class.isAssignableFrom(beanInfo.getClass())) {
			return this.visit((ModuleBeanInfo)beanInfo, generation);
		}
		else if(SocketBeanInfo.class.isAssignableFrom(beanInfo.getClass())) {
			return this.visit((SocketBeanInfo)beanInfo, generation);
		}
		return "";
	}

	@Override
	public String visit(ModuleBeanInfo moduleBeanInfo, ModuleClassGeneration generation) {
		boolean isWrapperBean = WrapperBeanInfo.class.isAssignableFrom(moduleBeanInfo.getClass());
		if(generation.getMode() == GenerationMode.BEAN_FIELD) {
			TypeMirror moduleBeanType = generation.getTypeUtils().getDeclaredType(generation.getElementUtils().getTypeElement("io.winterframework.core.Module.Bean"), moduleBeanInfo.getType());
			return generation.indent(1) + "private " + generation.getTypeName(moduleBeanType) + " " + moduleBeanInfo.getQualifiedName().normalize() + ";";
		}
		else if(generation.getMode() == GenerationMode.BEAN_ACCESSOR) {
			String beanAccessor = "";
			beanAccessor += generation.indent(1);
			beanAccessor += moduleBeanInfo.getVisibility().equals(Bean.Visibility.PUBLIC) ? "public " : "private ";
			beanAccessor += generation.getTypeName(moduleBeanInfo.getType()) + " ";
			beanAccessor += moduleBeanInfo.getQualifiedName().normalize() + "() {\n";
			
			beanAccessor += generation.indent(2);
			beanAccessor += "return this." + moduleBeanInfo.getQualifiedName().normalize() + ".get()" + (isWrapperBean ? ".get()" :"") + ";\n";
			
			beanAccessor += generation.indent(1) + "}\n";
			
			return beanAccessor;
		}
		else if(generation.getMode() == GenerationMode.BEAN_NEW) {
			String variable = moduleBeanInfo.getQualifiedName().normalize();
			
			TypeMirror beanType = isWrapperBean ? ((WrapperBeanInfo)moduleBeanInfo).getWrapperType() : moduleBeanInfo.getType();
			TypeMirror beanBuilderType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement("io.winterframework.core.Module.BeanBuilder").asType());
			
			String beanNew = generation.indent(2) + "this." + variable + " = this.with(" + generation.getTypeName(beanBuilderType) + "\n";
			
			if(moduleBeanInfo.getScope().equals(Scope.Type.SINGLETON)) {
				beanNew += generation.indent(3) + ".singleton(\"" + moduleBeanInfo.getQualifiedName().getSimpleValue() + "\", () -> {\n";
			}
			else if(moduleBeanInfo.getScope().equals(Scope.Type.PROTOTYPE)) {
				beanNew += generation.indent(3) + ".prototype(\"" + moduleBeanInfo.getQualifiedName().getSimpleValue() + "\", () -> {\n";
			}
			else {
				throw new IllegalArgumentException("Unkown bean scope: " + moduleBeanInfo.getScope());
			}
			
			beanNew += generation.indent(4) + generation.getTypeName(beanType) + " " + variable + " = new " + generation.getTypeName(beanType) + "(";
			beanNew += Arrays.stream(moduleBeanInfo.getRequiredSockets())
				.sorted(new Comparator<ModuleBeanSocketInfo>() {
					public int compare(ModuleBeanSocketInfo s1, ModuleBeanSocketInfo s2) {
						if(s1.getSocketElement() != s2.getSocketElement()) {
							throw new IllegalStateException("Comparing required sockets with different socket elements");
						}
						List<String> orderedDependencyNames = s1.getSocketElement().getParameters().stream().map(element -> element.getSimpleName().toString()).collect(Collectors.toList());
						return orderedDependencyNames.indexOf(s1.getQualifiedName().getSimpleValue()) - orderedDependencyNames.indexOf(s2.getQualifiedName().getSimpleValue());
					}
				})
				.map(socketInfo -> this.visit(socketInfo, generation.withMode(GenerationMode.BEAN_REFERENCE)))
				.collect(Collectors.joining(", "));
			beanNew += ");\n";
			
			// TODO: optionalSocket.ifPresent(bean::setXxx)
			beanNew += Arrays.stream(moduleBeanInfo.getOptionalSockets())
				.filter(socketInfo -> socketInfo.isResolved())
				.map(socketInfo -> generation.indent(4) + variable + "." + socketInfo.getSocketElement().getSimpleName().toString() + "(" + this.visit(socketInfo, generation.withMode(GenerationMode.BEAN_REFERENCE)) + ");")
				.collect(Collectors.joining("\n")) + "\n";

			beanNew += generation.indent(4) + "return " + variable + ";\n";
			beanNew += generation.indent(3) + "})\n";

			if(moduleBeanInfo.getInitElements().length > 0) {
				beanNew += Arrays.stream(moduleBeanInfo.getInitElements())
					.map(element -> generation.indent(3) + ".init(" + generation.getTypeName(beanType) + "::" + element.getSimpleName().toString() + ")")
					.collect(Collectors.joining("\n")) + "\n";
			}
				
			if(moduleBeanInfo.getInitElements().length > 0) {
				beanNew += Arrays.stream(moduleBeanInfo.getDestroyElements())
					.map(element -> generation.indent(3) + ".destroy(" + generation.getTypeName(beanType) + "::" + element.getSimpleName().toString() + ")")
					.collect(Collectors.joining("\n")) + "\n";
			}	

			beanNew += generation.indent(3) + ".build());";
			
			return beanNew;
		}
		else if(generation.getMode() == GenerationMode.BEAN_REFERENCE) {
			if(moduleBeanInfo.getQualifiedName().getModuleQName().equals(generation.getModule())) {
				return "this." + moduleBeanInfo.getQualifiedName().normalize() + "()";
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
		if(SingleSocketInfo.class.isAssignableFrom(socketInfo.getClass())) {
			return this.visit((SingleSocketInfo)socketInfo, generation);
		}
		else if(MultiSocketInfo.class.isAssignableFrom(socketInfo.getClass())) {
			return this.visit((MultiSocketInfo)socketInfo, generation);
		}
		return "";
	}
	
	@Override
	public String visit(SingleSocketInfo singleSocketInfo, ModuleClassGeneration generation) {
		if(!singleSocketInfo.isResolved()) {
			return "";
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
			
			DeclaredType dependencyCollectionType = generation.getTypeUtils().getDeclaredType(generation.getElementUtils().getTypeElement(Collection.class.getCanonicalName()), generation.getTypeUtils().getWildcardType(unwildDependencyType, null));
			DeclaredType dependencyListType = generation.getTypeUtils().getDeclaredType(generation.getElementUtils().getTypeElement(List.class.getCanonicalName()), generation.getTypeUtils().getWildcardType(unwildDependencyType, null));
			DeclaredType dependencySetType = generation.getTypeUtils().getDeclaredType(generation.getElementUtils().getTypeElement(Set.class.getCanonicalName()), generation.getTypeUtils().getWildcardType(unwildDependencyType, null));
			ArrayType dependencyArrayType =  generation.getTypeUtils().getArrayType(unwildDependencyType);
			
			TypeMirror streamType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement("java.util.stream.Stream").asType());
			TypeMirror listType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement("java.util.List").asType());
			TypeMirror setType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement("java.util.Set").asType());
			TypeMirror optionalType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement("java.util.Optional").asType());
			TypeMirror arraysType = generation.getElementUtils().getTypeElement("java.util.Arrays").asType();
			TypeMirror objectsType = generation.getElementUtils().getTypeElement("java.util.Objects").asType();
			TypeMirror collectorsType = generation.getElementUtils().getTypeElement("java.util.stream.Collectors").asType();
//			TypeMirror functionType = this.processingEnvironment.getTypeUtils().erasure(this.processingEnvironment.getElementUtils().getTypeElement("java.util.function.Function").asType());
			
			String beanSocketReference = "";
			
			beanSocketReference = generation.getTypeName(streamType) + ".of(";
			beanSocketReference += Arrays.stream(multiSocketInfo.getBeans())
				.map(beanInfo -> {
					if(generation.getTypeUtils().isAssignable(beanInfo.getType(), unwildDependencyType)) {
						if(SocketBeanInfo.class.isAssignableFrom(beanInfo.getClass()) && ((SocketBeanInfo)beanInfo).isOptional()) {
							return generation.getTypeName(optionalType) + ".ofNullable(" + this.visit(beanInfo, generation) + ").stream()";
						}
						return generation.getTypeName(streamType) + ".of(" + this.visit(beanInfo, generation) + ")";
					}
					else if(generation.getTypeUtils().isAssignable(beanInfo.getType(), dependencyListType)) {
						if(SocketBeanInfo.class.isAssignableFrom(beanInfo.getClass()) && ((SocketBeanInfo)beanInfo).isOptional()) {
							return generation.getTypeName(optionalType) + ".ofNullable(" + this.visit(beanInfo, generation) + ").orElse(" + generation.getTypeName(listType) + ".of()).stream()";
						}
						return this.visit(beanInfo, generation) + ".stream()";
					}
					else if(generation.getTypeUtils().isAssignable(beanInfo.getType(), dependencySetType)) {
						if(SocketBeanInfo.class.isAssignableFrom(beanInfo.getClass()) && ((SocketBeanInfo)beanInfo).isOptional()) {
							return generation.getTypeName(optionalType) + ".ofNullable(" + this.visit(beanInfo, generation) + ").orElse(" + generation.getTypeName(setType) + ".of()).stream()";
						}
						return this.visit(beanInfo, generation) + ".stream()";
					}
					else if(generation.getTypeUtils().isAssignable(beanInfo.getType(), dependencyCollectionType)) {
						if(SocketBeanInfo.class.isAssignableFrom(beanInfo.getClass()) && ((SocketBeanInfo)beanInfo).isOptional()) {
							return generation.getTypeName(optionalType) + ".ofNullable(" + this.visit(beanInfo, generation) + ").orElse(" + generation.getTypeName(listType) + ".of()).stream()";
						}
						return this.visit(beanInfo, generation) + ".stream()";
					}
					else if(generation.getTypeUtils().isAssignable(beanInfo.getType(), dependencyArrayType)) {
						if(SocketBeanInfo.class.isAssignableFrom(beanInfo.getClass()) && ((SocketBeanInfo)beanInfo).isOptional()) {
							return generation.getTypeName(arraysType) + ".stream(" + generation.getTypeName(optionalType) + ".ofNullable(" + this.visit(beanInfo, generation) + ").orElse(new " + generation.getTypeName(multiSocketInfo.getType()) + "[0]))";
						}
						return generation.getTypeName(arraysType) + ".stream(" + this.visit(beanInfo, generation) + ")";
					}
					return "";
				})
				.collect(Collectors.joining(", "));
			beanSocketReference += ").flatMap(t -> t).filter(" + generation.getTypeName(objectsType) + "::nonNull)";
			//beanSocketReference += ").flatMap(" + generation.getTypeName(functionType) + ".identity())"; // Apparently this fails compilation with OpenJDK compiler but not with ECJ

			if(multiSocketInfo.getMultiType().equals(MultiSocketType.ARRAY)) {
				beanSocketReference += ".toArray(" + generation.getTypeName(unwildDependencyType) + "[]::new)";
			}
			else if(multiSocketInfo.getMultiType().equals(MultiSocketType.COLLECTION) || multiSocketInfo.getMultiType().equals(MultiSocketType.LIST)) {
				beanSocketReference += ".collect(" + generation.getTypeName(collectorsType) + ".toList())";
			}
			else if(multiSocketInfo.getMultiType().equals(MultiSocketType.SET)) {
				beanSocketReference += ".collect(" + generation.getTypeName(collectorsType) + ".toSet())";
			}
			return beanSocketReference;
		}
		return "";
	}
	
	@Override
	public String visit(ModuleBeanSocketInfo beanSocketInfo, ModuleClassGeneration generation) {
		if(ModuleBeanSingleSocketInfo.class.isAssignableFrom(beanSocketInfo.getClass())) {
			return this.visit((ModuleBeanSingleSocketInfo)beanSocketInfo, generation);
		}
		else if(ModuleBeanMultiSocketInfo.class.isAssignableFrom(beanSocketInfo.getClass())) {
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
			String socketParameter = "";
			if(socketBeanInfo.getWiredBeans().length > 0) {
				TypeMirror wiredToType = generation.getElementUtils().getTypeElement("io.winterframework.core.annotation.WiredTo").asType(); 
				socketParameter += "@" + generation.getTypeName(wiredToType) + "({" + Arrays.stream(socketBeanInfo.getWiredBeans()).map(beanQName -> "\"" + beanQName.getSimpleValue() + "\"").collect(Collectors.joining(", ")) + "}) ";
			}
			
			if(socketBeanInfo.isOptional()) {
				TypeMirror optionalType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement("java.util.Optional").asType());
				socketParameter += generation.getTypeName(optionalType) + "<" + generation.getTypeName(socketBeanInfo.getSocketType()) + ">";
			}
			else {
				socketParameter += generation.getTypeName(socketBeanInfo.getSocketType());
			}
			socketParameter += " " + socketBeanInfo.getQualifiedName().normalize();
			
			return socketParameter;
		}
		else if(generation.getMode() == GenerationMode.SOCKET_FIELD) {
			// TODO we should declare the SocketType instead of the type for required socket and Optional<SocketType> for optional sockets
			// and initialize optional sockets to Optional.empty();
			return generation.indent(2) + "private " + generation.getTypeName(socketBeanInfo.getType()) + " " + socketBeanInfo.getQualifiedName().normalize() + ";";
		}
		else if(generation.getMode() == GenerationMode.SOCKET_ASSIGNMENT) {
			// TODO assuming SocketType is used in field declaration we should directly assign Suppliers: "() -> " + socketBeanInfo.getQualifiedName().normalize()
			// and throw IllegalArgumentException in case a socket is null
			return generation.indent(3) + "this." + socketBeanInfo.getQualifiedName().normalize() + " = " + socketBeanInfo.getQualifiedName().normalize() + ";";
		}
		else if(generation.getMode() == GenerationMode.SOCKET_INJECTOR) {
			// TODO assuming SocketType is used in field declaration we should assign an Optional.ofNullable(...)
			String plugName = socketBeanInfo.getQualifiedName().normalize();
			
			String result = generation.indent(2) + "public Builder set" + Character.toUpperCase(plugName.charAt(0)) + plugName.substring(1) + "(" + generation.getTypeName(socketBeanInfo.getType()) + " " + plugName + ") {\n";
			result += generation.indent(3) + "this." + plugName + " = " + plugName + ";\n";
			result += generation.indent(3) + "return this;\n";
			result += generation.indent(2) + "}\n";
			
			return result;
		}
		else if(generation.getMode() == GenerationMode.BEAN_REFERENCE) {
			if(socketBeanInfo.isOptional()) {
				return socketBeanInfo.getQualifiedName().normalize() + ".orElse(() -> null).get()";
			}
			else {
				return socketBeanInfo.getQualifiedName().normalize() + ".get()";
			}
		}
		else if(generation.getMode() == GenerationMode.IMPORT_BEAN_REFERENCE) {
			TypeMirror optionalType = generation.getTypeUtils().erasure(generation.getElementUtils().getTypeElement("java.util.Optional").asType());
			
			String result = "";
			if(SingleSocketInfo.class.isAssignableFrom(socketBeanInfo.getClass())) {
				result += this.visit((SingleSocketBeanInfo)socketBeanInfo, generation);
			}
			else if(MultiSocketInfo.class.isAssignableFrom(socketBeanInfo.getClass())) {
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
			}
		}
		return "";
	}

	@Override
	public String visit(SingleSocketBeanInfo singleSocketBeanInfo, ModuleClassGeneration generation) {
		if(generation.getMode() == GenerationMode.IMPORT_BEAN_REFERENCE) {
			return "(" + generation.getTypeName(singleSocketBeanInfo.getSocketType()) + ")() -> " + this.visit((SingleSocketInfo)singleSocketBeanInfo, generation.withMode(GenerationMode.BEAN_REFERENCE));
		}
		return "";
	}

	@Override
	public String visit(MultiSocketBeanInfo multiSocketBeanInfo, ModuleClassGeneration generation) {
		if(generation.getMode() == GenerationMode.IMPORT_BEAN_REFERENCE) {
			return "(" + generation.getTypeName(multiSocketBeanInfo.getSocketType()) + ")() -> " + this.visit((MultiSocketInfo)multiSocketBeanInfo, generation.withMode(GenerationMode.BEAN_REFERENCE));
		}
		return null;
	}

}
