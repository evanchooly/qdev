package com.antwerkz.quarkus.picocli

import org.zeroturnaround.exec.ProcessExecutor
import picocli.CommandLine
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.system.exitProcess

@CommandLine.Command(name = "subset")
class SubsetCommand : Runnable {
    companion object {
        val PROPERTIES = listOf(
            "-Dtest-postgresql", "-Ddocker",
            "-Dquarkus.debug.generated-classes-dir=target/q/generated",
            "-Dquarkus.debug.transformed-classes-dir=target/q/transformed",
            "-Dquarkus.debug.generated-sources-dir=target/q/sources"
        )
    }

    @Option(names = ["-d"], defaultValue = "false")
    var debug: Boolean = false

    @Option(names = ["-f", "--full"], defaultValue = "false")
    var full: Boolean = false

    @Option(names = ["-c", "--clean"], defaultValue = "false")
    var clean: Boolean = false

    @Option(names = ["-n", "--native"], defaultValue = "false")
    var native: Boolean = false

    @Option(names = ["-r", "--resume"])
    var resume: String? = null

    @Option(names = ["-t", "--test"], description = ["If true, run the tests of the installed modules. Default: false"])
    var test = false

    @Option(names = ["--root"])
    var root = System.getProperty("user.home") + "/dev/quarkus"

    @Parameters(paramLabel = "test", description = ["One or more tests to run"])
    var tests = listOf<String>()
    val quarkusRoot = File(System.getProperty("user.home"), "dev/quarkus")
    override fun run() {
        val (extensions, integrationTests) = tests.bucket()

        if (full) {
            maven(
                quarkusRoot, options(
                    mutableListOf(
                        "-T", "4C", "-DskipTests", "-DskipITs", "-DskipExtensionValidation",
                        "-Dskip.gradle.tests", "clean", "install"
                    )
                )
            )
        } else if (extensions.isNotEmpty()) {
            val qinstall = QInstallCommand(extensions)
            qinstall.debug = debug
            qinstall.clean = clean
            qinstall.test = test
            qinstall.run()
        }

        val outputs = mutableListOf<File>()
        integrationTests
            .forEach { test ->
                val file = File(test)
                if(!file.exists()) throw IllegalArgumentException("${file.absolutePath} does not exist.")
                val core = mutableListOf<String>()
                if (clean) {
                    core += "clean"
                }

                core += "verify"
                val options = options(core)
                if (native) {
                    options += "-Dnative"
                }
                val output = File(quarkusRoot, "${file.name}.out")
                outputs += output
                maven(file, options, FileOutputStream(output))
            }

        outputs
            .filter { it.exists() }
            .filter { it.readLines().any { line -> line.contains("[ERROR]") } }
            .forEach {
                println("Errors found in ${it.name}")
            }
        outputs
            .filter { it.exists() }
            .filter { it.readLines().none { line -> line.contains("[ERROR]") } }
            .forEach {
                it.delete()
            }
    }

    private fun maven(rootDir: File, options: List<String>, output: OutputStream = System.out): Int {
        if (debug) println("---  Building ${rootDir}")
        val executor = ProcessExecutor()
            .command("mvn", *options.toTypedArray())
            .directory(rootDir)
            .redirectOutput(output)
        if (output != System.out) {
            executor.redirectOutputAlsoTo(System.out)
        }
        return executor.execute().exitValue
    }

    private fun options(core: MutableList<String>): MutableList<String> {
        val options = core
        options += PROPERTIES
        resume?.let {
            options += listOf("-rf", if (it.startsWith(":")) it else ":$it")
        }

        return options
    }

    private fun List<String>.bucket(): Pair<List<String>, List<String>> {
        val extensions = mutableListOf<String>()
        val integrationTests = mutableListOf<String>()

        forEach {
            when {
                it.startsWith("integration-tests") -> integrationTests += "$root/$it"
                File("$root/integration-tests/$it").exists() -> integrationTests += "$root/integration-tests/$it"
                it.startsWith("extensions") -> extensions += "$root/$it"
                File("$root/extensions/$it").exists() -> extensions += "$root/extensions/$it"
                else -> throw IllegalArgumentException("$it is neither an integration test nor an extension")
            }
        }
        return Pair(extensions, integrationTests)
    }
}
