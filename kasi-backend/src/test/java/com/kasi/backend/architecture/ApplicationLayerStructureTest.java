package com.kasi.backend.architecture;

import com.kasi.backend.KasiBackendApplication;
import jakarta.validation.Valid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@DisplayName("应用分层结构")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApplicationLayerStructureTest {

    private static final String BASE_PACKAGE = "com.kasi.backend";
    private final Map<String, Class<?>> applicationTypes = scanApplicationTypes();

    @Test
    @DisplayName("Service接口与impl目录下的ServiceImpl自动配对")
    void servicesFollowInterfaceImplementationConvention() {
        List<Class<?>> serviceTypes = applicationTypes.values().stream()
                .filter(type -> type.getPackageName().endsWith(".service"))
                .filter(type -> type.getSimpleName().endsWith("Service"))
                .toList();
        List<Class<?>> implementationTypes = applicationTypes.values().stream()
                .filter(type -> type.getPackageName().endsWith(".service.impl"))
                .filter(type -> type.getSimpleName().endsWith("ServiceImpl"))
                .toList();

        assertThat(serviceTypes).isNotEmpty();
        assertThat(implementationTypes).isNotEmpty();
        assertSoftly(softly -> {
            for (Class<?> serviceType : serviceTypes) {
                softly.assertThat(serviceType.isInterface())
                        .as("%s 应为 Service 接口", serviceType.getName())
                        .isTrue();
                String implementationName = serviceType.getPackageName() + ".impl."
                        + serviceType.getSimpleName() + "Impl";
                Class<?> implementationType = applicationTypes.get(implementationName);
                softly.assertThat(implementationType)
                        .as("%s 应存在", implementationName)
                        .isNotNull();
                if (implementationType != null) {
                    softly.assertThat(serviceType.isAssignableFrom(implementationType))
                            .as("%s 应实现 %s", implementationType.getName(), serviceType.getName())
                            .isTrue();
                }
            }
            for (Class<?> implementationType : implementationTypes) {
                String serviceName = implementationType.getPackageName().replaceFirst("\\.impl$", "")
                        + "." + implementationType.getSimpleName().replaceFirst("Impl$", "");
                Class<?> serviceType = applicationTypes.get(serviceName);
                softly.assertThat(serviceType)
                        .as("%s 应对应 %s", implementationType.getName(), serviceName)
                        .isNotNull();
                if (serviceType != null) {
                    softly.assertThat(serviceType.isAssignableFrom(implementationType))
                            .as("%s 应实现 %s", implementationType.getName(), serviceName)
                            .isTrue();
                }
            }
        });
    }

    @Test
    @DisplayName("DTO与VO的包路径和类名后缀保持一致")
    void transportModelsFollowPackageAndSuffixConvention() {
        List<Class<?>> topLevelTypes = applicationTypes.values().stream()
                .filter(type -> type.getEnclosingClass() == null)
                .toList();
        List<Class<?>> dtoTypes = topLevelTypes.stream()
                .filter(type -> type.getSimpleName().endsWith("DTO"))
                .toList();
        List<Class<?>> voTypes = topLevelTypes.stream()
                .filter(type -> type.getSimpleName().endsWith("VO"))
                .toList();

        assertThat(dtoTypes).isNotEmpty();
        assertThat(voTypes).isNotEmpty();
        assertSoftly(softly -> {
            dtoTypes.forEach(type -> softly.assertThat(type.getPackageName())
                    .as("%s 应位于 dto 包", type.getName())
                    .endsWith(".dto"));
            voTypes.forEach(type -> softly.assertThat(type.getPackageName())
                    .as("%s 应位于 vo 包", type.getName())
                    .endsWith(".vo"));
        });
    }

    @Test
    @DisplayName("Controller不直接依赖Mapper且请求体使用已校验DTO")
    void controllersRespectMachineCheckableBoundaries() {
        List<Class<?>> controllerTypes = applicationTypes.values().stream()
                .filter(type -> type.getPackageName().endsWith(".controller"))
                .filter(type -> type.getSimpleName().endsWith("Controller"))
                .toList();

        assertThat(controllerTypes).isNotEmpty();
        assertSoftly(softly -> controllerTypes.forEach(controllerType -> {
            directDependencies(controllerType).forEach(dependency -> softly.assertThat(
                            dependency.getPackageName().endsWith(".mapper"))
                    .as("%s 不应直接依赖 %s", controllerType.getName(), dependency.getName())
                    .isFalse());

            for (Method method : controllerType.getDeclaredMethods()) {
                for (Parameter parameter : method.getParameters()) {
                    if (!parameter.isAnnotationPresent(RequestBody.class)) {
                        continue;
                    }
                    Class<?> requestType = parameter.getType();
                    softly.assertThat(requestType.getPackageName().endsWith(".dto")
                                    && requestType.getSimpleName().endsWith("DTO"))
                            .as("%s#%s 的请求体应使用 dto 包中的 DTO", controllerType.getName(), method.getName())
                            .isTrue();
                    softly.assertThat(hasValidation(controllerType, method, parameter))
                            .as("%s#%s 的请求体应触发 Validation", controllerType.getName(), method.getName())
                            .isTrue();
                }
            }
        }));
    }

    private static Stream<Class<?>> directDependencies(Class<?> controllerType) {
        Stream<Class<?>> fieldTypes = Arrays.stream(controllerType.getDeclaredFields())
                .map(java.lang.reflect.Field::getType);
        Stream<Class<?>> constructorTypes = Arrays.stream(controllerType.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()));
        return Stream.concat(fieldTypes, constructorTypes).distinct();
    }

    private static boolean hasValidation(Class<?> controllerType, Method method, Parameter parameter) {
        return parameter.isAnnotationPresent(Valid.class)
                || parameter.isAnnotationPresent(Validated.class)
                || method.isAnnotationPresent(Validated.class)
                || controllerType.isAnnotationPresent(Validated.class);
    }

    private static Map<String, Class<?>> scanApplicationTypes() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                        return beanDefinition.getMetadata().isIndependent();
                    }
                };
        TypeFilter includeAll = (metadataReader, metadataReaderFactory) -> true;
        scanner.addIncludeFilter(includeAll);

        ClassLoader classLoader = Objects.requireNonNullElse(
                Thread.currentThread().getContextClassLoader(), ApplicationLayerStructureTest.class.getClassLoader());
        Map<String, Class<?>> result = new LinkedHashMap<>();
        scanner.findCandidateComponents(BASE_PACKAGE).stream()
                .map(definition -> definition.getBeanClassName())
                .filter(Objects::nonNull)
                .sorted(Comparator.naturalOrder())
                .map(className -> loadClass(className, classLoader))
                .filter(ApplicationLayerStructureTest::isProductionType)
                .forEach(type -> result.put(type.getName(), type));
        return result;
    }

    private static boolean isProductionType(Class<?> type) {
        if (type.getProtectionDomain().getCodeSource() == null
                || KasiBackendApplication.class.getProtectionDomain().getCodeSource() == null) {
            return false;
        }
        return type.getProtectionDomain().getCodeSource().getLocation().equals(
                KasiBackendApplication.class.getProtectionDomain().getCodeSource().getLocation());
    }

    private static Class<?> loadClass(String className, ClassLoader classLoader) {
        try {
            return ClassUtils.forName(className, classLoader);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("无法加载应用类型: " + className, exception);
        }
    }
}
