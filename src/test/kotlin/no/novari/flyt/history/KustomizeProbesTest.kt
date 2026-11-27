package no.novari.flyt.history

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path

/** Startup waits for liveness, not for a health aggregate that a stopped Kafka consumer could pull down. */
class KustomizeProbesTest {
    @Test
    fun `base and every generated overlay use liveness for startup `() {
        val base = readYaml(Path.of("kustomize/base/flais.yaml"))
        val probes = (base.getValue("spec") as Map<*, *>)["probes"] as Map<*, *>
        assertThat((probes["startup"] as Map<*, *>)["path"])
            .isEqualTo((probes["liveness"] as Map<*, *>)["path"])
            .isEqualTo("/actuator/health/liveness")
        val overlays =
            Files.walk(Path.of("kustomize/overlays")).use { paths ->
                paths.filter { it.fileName.toString() == "kustomization.yaml" }.toList()
            }
        assertThat(overlays).isNotEmpty()
        overlays.forEach { overlay ->
            val patches = readYaml(overlay).getValue("patches") as List<*>
            val operations =
                patches.flatMap {
                    val patch = (it as Map<*, *>)["patch"] as? String
                    if (patch == null) emptyList() else Yaml().load<List<Map<String, Any>>>(patch)
                }
            val startup = operations.single { it["path"] == "/spec/probes/startup/path" }["value"]
            val liveness = operations.single { it["path"] == "/spec/probes/liveness/path" }["value"]
            assertThat(startup).describedAs(overlay.toString()).isEqualTo(liveness)
            assertThat(startup as String).endsWith("/actuator/health/liveness")
        }
    }

    private fun readYaml(path: Path): Map<String, Any> = Files.newBufferedReader(path).use { Yaml().load(it) }
}
