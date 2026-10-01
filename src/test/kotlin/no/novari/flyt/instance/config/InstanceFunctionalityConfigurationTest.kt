package no.novari.flyt.instance.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class InstanceFunctionalityConfigurationTest {
    private val contextRunner =
        ApplicationContextRunner().withUserConfiguration(
            InstanceFunctionalityConfiguration::class.java,
            ConditionalConfiguration::class.java,
            ConditionalBeanConfiguration::class.java,
        )

    @Test
    fun `instance functionality is disabled when property is missing`() {
        contextRunner.run { context ->
            assertThat(context).hasSingleBean(InstanceFunctionalityProperties::class.java)
            assertThat(context.getBean(InstanceFunctionalityProperties::class.java).enabled).isFalse()
            assertThat(context).doesNotHaveBean(ClassScopedTestBean::class.java)
            assertThat(context).doesNotHaveBean(MethodScopedTestBean::class.java)
        }
    }

    @Test
    fun `instance functionality is disabled when property is false`() {
        contextRunner.withPropertyValues("novari.flyt.instance-functionality.enabled=false").run { context ->
            assertThat(context.getBean(InstanceFunctionalityProperties::class.java).enabled).isFalse()
            assertThat(context).doesNotHaveBean(ClassScopedTestBean::class.java)
            assertThat(context).doesNotHaveBean(MethodScopedTestBean::class.java)
        }
    }

    @Test
    fun `instance functionality is enabled when property is true`() {
        contextRunner.withPropertyValues("novari.flyt.instance-functionality.enabled=true").run { context ->
            assertThat(context.getBean(InstanceFunctionalityProperties::class.java).enabled).isTrue()
            assertThat(context).hasSingleBean(ClassScopedTestBean::class.java)
            assertThat(context).hasSingleBean(MethodScopedTestBean::class.java)
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnInstanceFunctionality
    class ConditionalConfiguration {
        @Bean
        fun classScopedTestBean() = ClassScopedTestBean()
    }

    @Configuration(proxyBeanMethods = false)
    class ConditionalBeanConfiguration {
        @Bean
        @ConditionalOnInstanceFunctionality
        fun methodScopedTestBean() = MethodScopedTestBean()
    }

    class ClassScopedTestBean

    class MethodScopedTestBean
}
