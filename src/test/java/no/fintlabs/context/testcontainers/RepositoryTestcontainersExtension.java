package no.fintlabs.context.testcontainers;

import org.junit.jupiter.api.extension.*;
import org.springframework.kafka.support.JavaUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Objects;

@Testcontainers
class RepositoryTestcontainersExtension implements
        Extension,
        BeforeAllCallback,
        AfterAllCallback,
        BeforeEachCallback,
        AfterEachCallback {

    private ContainerCleanupType cleanupType;
    private PostgreSQLContainer<?> postgreSQLContainer;

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        setup(extensionContext);
        if (cleanupType == ContainerCleanupType.CLASS) {
            postgreSQLContainer.start();
        }
    }

    private RepositoryTestcontainersTest getAnnotation(ExtensionContext extensionContext) {
        Class<?> requiredTestClass = extensionContext.getRequiredTestClass();
        RepositoryTestcontainersTest annotation = requiredTestClass.getAnnotation(RepositoryTestcontainersTest.class);
        Objects.requireNonNull(annotation);
        return annotation;
    }

    private void setup(ExtensionContext extensionContext) {
        RepositoryTestcontainersTest annotation = getAnnotation(extensionContext);
        cleanupType = annotation.cleanupType();
        try (PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16")) {
            this.postgreSQLContainer = postgreSQLContainer
                    .waitingFor(Wait.forListeningPort())
                    .withCreateContainerCmdModifier(createContainerCmd -> JavaUtils.INSTANCE.acceptIfNotNull(
                                    createContainerCmd.getHostConfig(),
                                    hostConfig -> {
                                        if (annotation.cpuCount() > 0) {
                                            hostConfig.withCpuCount(annotation.cpuCount());
                                        }
                                        if (annotation.memorySize() > 0) {
                                            hostConfig.withMemory(annotation.memorySize());
                                        }
                                    }
                            )
                    );
        }
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        if (cleanupType == ContainerCleanupType.CLASS) {
            postgreSQLContainer.stop();
        }
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        if (cleanupType == ContainerCleanupType.METHOD) {
            postgreSQLContainer.start();
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        if (cleanupType == ContainerCleanupType.METHOD) {
            postgreSQLContainer.stop();
        }
    }
}
