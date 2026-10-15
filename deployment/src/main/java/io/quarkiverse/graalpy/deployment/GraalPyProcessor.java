package io.quarkiverse.graalpy.deployment;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.PrimitiveType;
import org.jboss.jandex.Type;

import io.quarkiverse.graalpy.runtime.GraalPy;
import io.quarkiverse.graalpy.runtime.GraalPyFunction;
import io.quarkiverse.graalpy.runtime.GraalPyModuleBinding;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmoAdaptor;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.FieldCreator;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.runtime.LaunchMode;

public class GraalPyProcessor {

    private static final String FEATURE = "graalpy";
    private static final DotName GRAALPY_MODULE_BINDING = DotName.createSimple(GraalPyModuleBinding.class.getName());
    private static final DotName GRAALPY_FUNCTION = DotName.createSimple(GraalPyFunction.class.getName());

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem graalPyBean() {
        return AdditionalBeanBuildItem.unremovableOf(GraalPy.class);
    }

    @BuildStep
    void packagePythonSources(OutputTargetBuildItem outputTarget,
            LaunchModeBuildItem launchMode,
            GraalPyBuildTimeConfig config,
            BuildProducer<GeneratedResourceBuildItem> generatedResources,
            BuildProducer<NativeImageResourceBuildItem> nativeResources,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watched) throws IOException {
        Path projectRoot = outputTarget.getOutputDirectory().getParent();
        if (projectRoot == null) {
            return;
        }

        packageDirectory(projectRoot.resolve(config.sourceDir()), GraalPy.RESOURCE_ROOT, GraalPy.MANIFEST_RESOURCE,
                generatedResources, nativeResources, watched);

        if (launchMode.getLaunchMode() == LaunchMode.TEST && config.includeTestSources()) {
            packageDirectory(projectRoot.resolve(config.testSourceDir()), GraalPy.TEST_RESOURCE_ROOT,
                    GraalPy.TEST_MANIFEST_RESOURCE, generatedResources, nativeResources, watched);
        } else {
            watchDirectoryAdditions(projectRoot.resolve(config.testSourceDir()), watched);
        }
    }

    private void packageDirectory(Path sourceRoot, String resourceRoot, String manifestResource,
            BuildProducer<GeneratedResourceBuildItem> generatedResources,
            BuildProducer<NativeImageResourceBuildItem> nativeResources,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watched) throws IOException {
        if (!Files.isDirectory(sourceRoot)) {
            return;
        }
        List<String> manifest = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            for (Path file : (Iterable<Path>) walk::iterator) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                String relative = sourceRoot.relativize(file).toString().replace('\\', '/');
                String resourcePath = resourceRoot + "/" + relative;
                byte[] content = Files.readAllBytes(file);
                generatedResources.produce(new GeneratedResourceBuildItem(resourcePath, content));
                nativeResources.produce(new NativeImageResourceBuildItem(resourcePath));
                watched.produce(new HotDeploymentWatchedFileBuildItem(file.toString()));
                manifest.add(relative);
            }
        }
        if (manifest.isEmpty()) {
            return;
        }
        byte[] manifestBytes = String.join("\n", manifest).getBytes(StandardCharsets.UTF_8);
        generatedResources.produce(new GeneratedResourceBuildItem(manifestResource, manifestBytes));
        nativeResources.produce(new NativeImageResourceBuildItem(manifestResource));
        watchDirectoryAdditions(sourceRoot, watched);
    }

    private void watchDirectoryAdditions(Path dir, BuildProducer<HotDeploymentWatchedFileBuildItem> watched) {
        String prefix = dir.toAbsolutePath().toString();
        watched.produce(HotDeploymentWatchedFileBuildItem.builder()
                .setLocationPredicate(path -> path != null && path.startsWith(prefix) && path.endsWith(".py"))
                .setRestartNeeded(true)
                .build());
    }

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    void validateBindings(CombinedIndexBuildItem combinedIndex,
            OutputTargetBuildItem outputTarget,
            LaunchModeBuildItem launchMode,
            GraalPyBuildTimeConfig config) {
        if (!config.validateBindings()) {
            return;
        }
        IndexView index = combinedIndex.getIndex();
        Collection<AnnotationInstance> annotations = index.getAnnotations(GRAALPY_MODULE_BINDING);
        if (annotations.isEmpty()) {
            return;
        }
        Path projectRoot = outputTarget.getOutputDirectory().getParent();
        if (projectRoot == null) {
            return;
        }
        List<Path> sysPaths = new ArrayList<>();
        Path mainDir = projectRoot.resolve(config.sourceDir());
        if (Files.isDirectory(mainDir)) {
            sysPaths.add(mainDir.toAbsolutePath());
        }
        if (launchMode.getLaunchMode() == LaunchMode.TEST && config.includeTestSources()) {
            Path testDir = projectRoot.resolve(config.testSourceDir());
            if (Files.isDirectory(testDir)) {
                sysPaths.add(testDir.toAbsolutePath());
            }
        }

        List<String> errors = new ArrayList<>();
        try (Context ctx = Context.newBuilder("python")
                .allowIO(IOAccess.ALL)
                .allowExperimentalOptions(true)
                .build()) {
            for (Path p : sysPaths) {
                String literal = p.toString().replace("\\", "\\\\").replace("'", "\\'");
                ctx.eval("python", "import sys; sys.path.insert(0, '" + literal + "')");
            }
            for (AnnotationInstance annotation : annotations) {
                if (annotation.target().kind() != AnnotationTarget.Kind.CLASS) {
                    continue;
                }
                ClassInfo iface = annotation.target().asClass();
                String moduleName = annotation.value().asString();
                Value module;
                try {
                    module = ctx.eval("python",
                            "__import__('" + moduleName.replace("'", "\\'") + "', None, None, ['*'])");
                } catch (PolyglotException e) {
                    errors.add("@GraalPyModuleBinding(\"" + moduleName + "\") on " + iface.name()
                            + ": module not importable: " + firstLine(e.getMessage()));
                    continue;
                }
                for (MethodInfo method : iface.methods()) {
                    if (method.isSynthetic()) {
                        continue;
                    }
                    if ((method.flags() & Modifier.ABSTRACT) == 0 || (method.flags() & Modifier.STATIC) != 0) {
                        continue;
                    }
                    String fnName = resolveFunctionName(method);
                    Value member = module.getMember(fnName);
                    if (member == null || !member.canExecute()) {
                        errors.add("@GraalPyModuleBinding(\"" + moduleName + "\") on " + iface.name()
                                + ": missing callable '" + fnName + "' (for method " + method.name() + ")");
                    }
                }
            }
        }
        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder("GraalPy binding validation failed (")
                    .append(errors.size()).append(" issue(s)):\n");
            for (String e : errors) {
                sb.append("  - ").append(e).append('\n');
            }
            throw new IllegalStateException(sb.toString());
        }
    }

    private static String firstLine(String msg) {
        if (msg == null) {
            return "(no message)";
        }
        int nl = msg.indexOf('\n');
        return nl < 0 ? msg : msg.substring(0, nl);
    }

    @BuildStep
    void generateBindings(CombinedIndexBuildItem combinedIndex,
            BuildProducer<GeneratedBeanBuildItem> generatedBeans,
            BuildProducer<UnremovableBeanBuildItem> unremovable) {
        IndexView index = combinedIndex.getIndex();
        Collection<AnnotationInstance> annotations = index.getAnnotations(GRAALPY_MODULE_BINDING);
        if (annotations.isEmpty()) {
            return;
        }
        ClassOutput classOutput = new GeneratedBeanGizmoAdaptor(generatedBeans);
        for (AnnotationInstance annotation : annotations) {
            if (annotation.target().kind() != AnnotationTarget.Kind.CLASS) {
                continue;
            }
            ClassInfo iface = annotation.target().asClass();
            if ((iface.flags() & Modifier.INTERFACE) == 0) {
                throw new IllegalStateException(
                        "@GraalPyModuleBinding may only be placed on interfaces: " + iface.name());
            }
            String moduleName = annotation.value().asString();
            generateImplementation(iface, moduleName, classOutput);
            unremovable.produce(UnremovableBeanBuildItem.beanTypes(iface.name()));
        }
    }

    private void generateImplementation(ClassInfo iface, String moduleName, ClassOutput classOutput) {
        String ifaceName = iface.name().toString();
        String generatedName = ifaceName + "_GraalPyImpl";
        try (ClassCreator cc = ClassCreator.builder()
                .classOutput(classOutput)
                .className(generatedName)
                .interfaces(ifaceName)
                .build()) {
            cc.addAnnotation(ApplicationScoped.class);
            FieldCreator graalPyField = cc.getFieldCreator("graalPy", GraalPy.class)
                    .setModifiers(Modifier.PUBLIC);
            graalPyField.addAnnotation(Inject.class);
            FieldDescriptor graalPyFd = graalPyField.getFieldDescriptor();

            for (MethodInfo method : iface.methods()) {
                if (method.isSynthetic()) {
                    continue;
                }
                if ((method.flags() & Modifier.ABSTRACT) == 0 || (method.flags() & Modifier.STATIC) != 0) {
                    continue;
                }
                generateMethod(cc, method, moduleName, graalPyFd);
            }
        }
    }

    private void generateMethod(ClassCreator cc, MethodInfo method, String moduleName, FieldDescriptor graalPyFd) {
        String functionName = resolveFunctionName(method);
        List<Type> parameters = method.parameterTypes();
        Object[] paramDescriptors = new Object[parameters.size()];
        for (int i = 0; i < parameters.size(); i++) {
            paramDescriptors[i] = parameters.get(i).name().toString();
        }
        Type returnType = method.returnType();
        try (MethodCreator mc = cc.getMethodCreator(method.name(), returnType.name().toString(), paramDescriptors)) {
            ResultHandle self = mc.getThis();
            ResultHandle graalPy = mc.readInstanceField(graalPyFd, self);
            ResultHandle moduleHandle = mc.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(GraalPy.class, "module", GraalPy.Module.class, String.class),
                    graalPy, mc.load(moduleName));
            ResultHandle fnHandle = mc.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(GraalPy.Module.class, "function", GraalPy.Function.class, String.class),
                    moduleHandle, mc.load(functionName));

            ResultHandle argsArray = mc.newArray(Object.class, parameters.size());
            for (int i = 0; i < parameters.size(); i++) {
                ResultHandle param = mc.getMethodParam(i);
                Type paramType = parameters.get(i);
                ResultHandle boxed = boxIfPrimitive(mc, param, paramType);
                mc.writeArrayValue(argsArray, i, boxed);
            }
            ResultHandle result = mc.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(GraalPy.Function.class, "invoke", GraalPy.Result.class, Object[].class),
                    fnHandle, argsArray);

            if (returnType.kind() == Type.Kind.VOID) {
                mc.returnValue(null);
                return;
            }
            ResultHandle returnClass = loadClassForAs(mc, returnType);
            ResultHandle converted = mc.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(GraalPy.Result.class, "as", Object.class, Class.class),
                    result, returnClass);
            if (returnType.kind() == Type.Kind.PRIMITIVE) {
                mc.returnValue(unbox(mc, converted, returnType));
            } else {
                mc.returnValue(mc.checkCast(converted, returnType.name().toString()));
            }
        }
    }

    private ResultHandle loadClassForAs(MethodCreator mc, Type returnType) {
        if (returnType.kind() == Type.Kind.PRIMITIVE) {
            return mc.loadClass(boxedClass(returnType.asPrimitiveType().primitive()));
        }
        return mc.loadClass(returnType.name().toString());
    }

    private String resolveFunctionName(MethodInfo method) {
        AnnotationInstance override = method.annotation(GRAALPY_FUNCTION);
        if (override != null && override.value() != null) {
            String name = override.value().asString();
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
        return method.name();
    }

    private ResultHandle boxIfPrimitive(MethodCreator mc, ResultHandle value, Type type) {
        if (type.kind() != Type.Kind.PRIMITIVE) {
            return value;
        }
        Class<?> boxed = boxedClass(type.asPrimitiveType().primitive());
        Class<?> primitive = primitiveClass(type.asPrimitiveType().primitive());
        return mc.invokeStaticMethod(MethodDescriptor.ofMethod(boxed, "valueOf", boxed, primitive), value);
    }

    private ResultHandle unbox(MethodCreator mc, ResultHandle value, Type type) {
        Class<?> boxed = boxedClass(type.asPrimitiveType().primitive());
        Class<?> primitive = primitiveClass(type.asPrimitiveType().primitive());
        ResultHandle cast = mc.checkCast(value, boxed);
        String unboxMethod = primitive.getName() + "Value";
        return mc.invokeVirtualMethod(MethodDescriptor.ofMethod(boxed, unboxMethod, primitive), cast);
    }

    private Class<?> boxedClass(PrimitiveType.Primitive primitive) {
        switch (primitive) {
            case BOOLEAN:
                return Boolean.class;
            case BYTE:
                return Byte.class;
            case CHAR:
                return Character.class;
            case SHORT:
                return Short.class;
            case INT:
                return Integer.class;
            case LONG:
                return Long.class;
            case FLOAT:
                return Float.class;
            case DOUBLE:
                return Double.class;
            default:
                throw new IllegalArgumentException("Unsupported primitive: " + primitive);
        }
    }

    private Class<?> primitiveClass(PrimitiveType.Primitive primitive) {
        switch (primitive) {
            case BOOLEAN:
                return boolean.class;
            case BYTE:
                return byte.class;
            case CHAR:
                return char.class;
            case SHORT:
                return short.class;
            case INT:
                return int.class;
            case LONG:
                return long.class;
            case FLOAT:
                return float.class;
            case DOUBLE:
                return double.class;
            default:
                throw new IllegalArgumentException("Unsupported primitive: " + primitive);
        }
    }
}
