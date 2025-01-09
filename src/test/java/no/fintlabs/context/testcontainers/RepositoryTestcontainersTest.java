package no.fintlabs.context.testcontainers;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@DataJpaTest
@ExtendWith(RepositoryTestcontainersExtension.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public @interface RepositoryTestcontainersTest {
    ContainerCleanupType cleanupType();

    long cpuCount() default 0;

    long memorySize() default 0;
}
