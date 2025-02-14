package org.testcontainers.junit.jupiter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ModifierSupport;
import org.junit.platform.commons.support.ReflectionSupport;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.lifecycle.TestDescription;
import org.testcontainers.lifecycle.TestLifecycleAware;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestcontainersExtension
    implements BeforeEachCallback, BeforeAllCallback, AfterEachCallback, AfterAllCallback, ExecutionCondition {

    private static final Namespace NAMESPACE = Namespace.create(TestcontainersExtension.class);

    private static final String SHARED_LIFECYCLE_AWARE_CONTAINERS = "sharedLifecycleAwareContainers";

    private static final String LOCAL_LIFECYCLE_AWARE_CONTAINERS = "localLifecycleAwareContainers";
    private static final ConcurrentHashMap<String, StoreAdapterThread> STORE_ADAPTER_THREADS = new ConcurrentHashMap<>();

    private final DockerAvailableDetector dockerDetector = new DockerAvailableDetector();

    @Override
    public void beforeAll(ExtensionContext context) {
        Class<?> testClass = context
            .getTestClass()
            .orElseThrow(() -> {
                return new ExtensionConfigurationException("TestcontainersExtension is only supported for classes.");
            });

        Store store = context.getStore(NAMESPACE);
        List<StoreAdapter> sharedContainersStoreAdapters = findSharedContainers(testClass);

        startContainers(sharedContainersStoreAdapters, store, context);

        List<TestLifecycleAware> lifecycleAwareContainers = sharedContainersStoreAdapters
            .stream()
            .filter(this::isTestLifecycleAware)
            .map(lifecycleAwareAdapter -> (TestLifecycleAware) lifecycleAwareAdapter.container)
            .collect(Collectors.toList());

        store.put(SHARED_LIFECYCLE_AWARE_CONTAINERS, lifecycleAwareContainers);
        signalBeforeTestToContainers(lifecycleAwareContainers, testDescriptionFrom(context));
    }

    private void startContainers(List<StoreAdapter> storeAdapters, Store store, ExtensionContext context) {
        if (storeAdapters.isEmpty()) {
            return;
        }

        if (isParallelExecutionEnabled(context)) {
            Stream<Startable> startables = storeAdapters
                .stream()
                .map(storeAdapter -> {
                    store.getOrComputeIfAbsent(storeAdapter.getKey(), k -> storeAdapterStart(k, storeAdapter));
                    return storeAdapter.container;
                });
            Startables.deepStart(startables).join();
        } else {
            storeAdapters.forEach(adapter -> store.getOrComputeIfAbsent(adapter.getKey(), k -> adapter.start()));
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        signalAfterTestToContainersFor(SHARED_LIFECYCLE_AWARE_CONTAINERS, context);
    }

    @Override
    public void beforeEach(final ExtensionContext context) {
        Store store = context.getStore(NAMESPACE);

        List<StoreAdapter> restartContainers = collectParentTestInstances(context)
            .parallelStream()
            .flatMap(this::findRestartContainers)
            .collect(Collectors.toList());

        List<TestLifecycleAware> lifecycleAwareContainers = findTestLifecycleAwareContainers(
            restartContainers,
            store,
            context
        );

        store.put(LOCAL_LIFECYCLE_AWARE_CONTAINERS, lifecycleAwareContainers);
        signalBeforeTestToContainers(lifecycleAwareContainers, testDescriptionFrom(context));
    }

    private List<TestLifecycleAware> findTestLifecycleAwareContainers(
        List<StoreAdapter> restartContainers,
        Store store,
        ExtensionContext context
    ) {
        startContainers(restartContainers, store, context);

        return restartContainers
            .stream()
            .filter(this::isTestLifecycleAware)
            .map(lifecycleAwareAdapter -> (TestLifecycleAware) lifecycleAwareAdapter.container)
            .collect(Collectors.toList());
    }

    private boolean isParallelExecutionEnabled(ExtensionContext context) {
        return findTestcontainers(context).map(Testcontainers::parallel).orElse(false);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        signalAfterTestToContainersFor(LOCAL_LIFECYCLE_AWARE_CONTAINERS, context);
    }

    private static synchronized StoreAdapter storeAdapterStart(String key, StoreAdapter adapter) {
        boolean isInitialized = STORE_ADAPTER_THREADS.containsKey(key);

        if (!isInitialized) {
            StoreAdapter storeAdapter = adapter.start();
            STORE_ADAPTER_THREADS.put(key, new StoreAdapterThread(storeAdapter));
            return storeAdapter;
        }

        StoreAdapterThread storeAdapter = STORE_ADAPTER_THREADS.get(key);
        storeAdapter.threads.incrementAndGet();
        return storeAdapter.storeAdapter;
    }

    private void signalBeforeTestToContainers(
        List<TestLifecycleAware> lifecycleAwareContainers,
        TestDescription testDescription
    ) {
        lifecycleAwareContainers.forEach(container -> container.beforeTest(testDescription));
    }

    private void signalAfterTestToContainersFor(String storeKey, ExtensionContext context) {
        List<TestLifecycleAware> lifecycleAwareContainers = (List<TestLifecycleAware>) context
            .getStore(NAMESPACE)
            .get(storeKey);
        if (lifecycleAwareContainers != null) {
            TestDescription description = testDescriptionFrom(context);
            Optional<Throwable> throwable = context.getExecutionException();
            lifecycleAwareContainers.forEach(container -> container.afterTest(description, throwable));
        }
    }

    private TestDescription testDescriptionFrom(ExtensionContext context) {
        return new TestcontainersTestDescription(
            context.getUniqueId(),
            FilesystemFriendlyNameGenerator.filesystemFriendlyNameOf(context)
        );
    }

    private boolean isTestLifecycleAware(StoreAdapter adapter) {
        return adapter.container instanceof TestLifecycleAware;
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return findTestcontainers(context)
            .map(this::evaluate)
            .orElseThrow(() -> new ExtensionConfigurationException("@Testcontainers not found"));
    }

    private Optional<Testcontainers> findTestcontainers(ExtensionContext context) {
        Optional<ExtensionContext> current = Optional.of(context);
        while (current.isPresent()) {
            Optional<Testcontainers> testcontainers = AnnotationSupport.findAnnotation(
                current.get().getRequiredTestClass(),
                Testcontainers.class
            );
            if (testcontainers.isPresent()) {
                return testcontainers;
            }
            current = current.get().getParent();
        }
        return Optional.empty();
    }

    private ConditionEvaluationResult evaluate(Testcontainers testcontainers) {
        if (testcontainers.disabledWithoutDocker()) {
            if (isDockerAvailable()) {
                return ConditionEvaluationResult.enabled("Docker is available");
            }
            return ConditionEvaluationResult.disabled("disabledWithoutDocker is true and Docker is not available");
        }
        return ConditionEvaluationResult.enabled("disabledWithoutDocker is false");
    }

    boolean isDockerAvailable() {
        return this.dockerDetector.isDockerAvailable();
    }

    private Set<Object> collectParentTestInstances(final ExtensionContext context) {
        List<Object> allInstances = new ArrayList<>(context.getRequiredTestInstances().getAllInstances());
        Collections.reverse(allInstances);
        return new LinkedHashSet<>(allInstances);
    }

    private List<StoreAdapter> findSharedContainers(Class<?> testClass) {
        return ReflectionSupport
            .findFields(testClass, isSharedContainer(), HierarchyTraversalMode.TOP_DOWN)
            .stream()
            .map(f -> getContainerInstance(null, f))
            .collect(Collectors.toList());
    }

    private Predicate<Field> isSharedContainer() {
        return isContainer().and(ModifierSupport::isStatic);
    }

    private Stream<StoreAdapter> findRestartContainers(Object testInstance) {
        return ReflectionSupport
            .findFields(testInstance.getClass(), isRestartContainer(), HierarchyTraversalMode.TOP_DOWN)
            .stream()
            .map(f -> getContainerInstance(testInstance, f));
    }

    private Predicate<Field> isRestartContainer() {
        return isContainer().and(ModifierSupport::isNotStatic);
    }

    private static Predicate<Field> isContainer() {
        return field -> {
            boolean isAnnotatedWithContainer = AnnotationSupport.isAnnotated(field, Container.class);
            if (isAnnotatedWithContainer) {
                boolean isStartable = Startable.class.isAssignableFrom(field.getType());

                if (!isStartable) {
                    throw new ExtensionConfigurationException(
                        String.format("FieldName: %s does not implement Startable", field.getName())
                    );
                }
                return true;
            }
            return false;
        };
    }

    private static StoreAdapter getContainerInstance(final Object testInstance, final Field field) {
        try {
            field.setAccessible(true);
            Startable containerInstance = (Startable) field.get(testInstance);
            if (containerInstance == null) {
                throw new ExtensionConfigurationException("Container " + field.getName() + " needs to be initialized");
            }
            return new StoreAdapter(field.getDeclaringClass(), field.getName(), containerInstance);
        } catch (IllegalAccessException e) {
            throw new ExtensionConfigurationException("Can not access container defined in field " + field.getName());
        }
    }

    /**
     * An adapter for {@link Startable} that implement {@link CloseableResource}
     * thereby letting the JUnit automatically stop containers once the current
     * {@link ExtensionContext} is closed.
     */
    private static class StoreAdapter implements CloseableResource {

        @Getter
        private final String key;

        private final Startable container;

        private StoreAdapter(Class<?> declaringClass, String fieldName, Startable container) {
            this.key = declaringClass.getName() + "." + fieldName;
            this.container = container;
        }

        private StoreAdapter start() {
            container.start();
            return this;
        }

        @Override
        public void close() {
            int total = STORE_ADAPTER_THREADS.getOrDefault(key, StoreAdapterThread.NULL).threads.decrementAndGet();
            if (total < 1) {
                container.stop();
                STORE_ADAPTER_THREADS.remove(key);
            }
        }

    }

    private static class StoreAdapterThread {

        public static final StoreAdapterThread NULL = new StoreAdapterThread(null);
        public final StoreAdapter storeAdapter;
        public final AtomicInteger threads = new AtomicInteger(1);

        private StoreAdapterThread(StoreAdapter storeAdapter) {
            this.storeAdapter = storeAdapter;
        }

    }

}
