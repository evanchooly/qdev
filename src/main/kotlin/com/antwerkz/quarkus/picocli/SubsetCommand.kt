package com.antwerkz.quarkus.picocli

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import org.apache.maven.model.io.DefaultModelReader
import org.zeroturnaround.exec.ProcessExecutor
import picocli.CommandLine
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

@CommandLine.Command(name = "subset")
class SubsetCommand : Runnable {
    companion object {
        val PROPERTIES = listOf(
            "-Ddocker",
            "-Dtest-postgresql",
            "-Dtest-containers",
            "-Dstart-containers"
        )
        val BYTECODE = listOf(
            "-Dquarkus.package.quiltflower.enabled=true",
            "-Dquarkus.debug.generated-classes-dir=target/q/generated",
            "-Dquarkus.debug.generated-sources-dir=target/q/sources",
            "-Dquarkus.debug.transformed-classes-dir=target/q/transformed"
        )
    }

    @Option(names = ["-b"], defaultValue = "false")
    var bytecode: Boolean = false

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

    @Option(names = ["-t", "--test"], description = ["run the tests of the installed modules. Default: false"])
    var runTests = false

    @Option(names = ["--root"])
    var root = System.getProperty("user.home") + "/dev/quarkus"

    @Parameters(paramLabel = "target", description = ["One or more targets to process"])
    var targets = listOf<String>()
    val quarkusRoot = File(root)

    override fun run() {

        val (extensions, integrationTests) = targets.bucket()

        val outputs = mutableListOf<File>()
        if (full) {
            maven(
                quarkusRoot,
                mutableListOf(
                    "-T", "4C", "-DskipTests", "-DskipITs", "-DskipExtensionValidation",
                    "-Dskip.gradle.tests", "clean", "install"
                        + options()
                )
            )
        } else if (extensions.isNotEmpty()) {
            outputs += extensions.map(::build)

        }
        outputs += integrationTests.map(::build)

        outputs
            .filter { it.exists() }
            .filter { it.readLines().none { line -> line.contains("[ERROR]") } }
            .forEach {
                it.delete()
            }
        val errors = outputs
            .filter { it.exists() }
            .filter { it.readLines().any { line -> line.contains("[ERROR]") } }
        errors.forEach {
                println("Errors found in ${it.name}")
            }

        if (errors.isNotEmpty()) {
            println("Rerun failing tests with: ")
            println(" subset " + failingModules(integrationTests, errors))
        }
    }

    private fun failingModules(integrationTests: Set<String>, errors: List<File>): String {
        val moduleNames = errors.map { it.nameWithoutExtension }
        val reader = DefaultModelReader()

        return integrationTests
            .map { File(it) to reader.read(File(it, "pom.xml"), mapOf<String, Any>()) }
            .map { (file, model) -> file to model.artifactId }
            .filter { it.second in moduleNames }
            .joinToString(" ") { it.first.name }
    }

    private fun build(root: String): File {
        val file = File(root)
        if (!file.exists()) throw IllegalArgumentException("${file.absolutePath} does not exist.")
        val core = mutableListOf<String>()
        if (clean) {
            core += "clean"
        }

        core += "install"
        val options: MutableList<String> = (core + options()) as MutableList<String>
        if (native) {
            options += "-Dnative"
        }
        if (!runTests && !isIntegrationTest(File(root))) {
            options += "-DskipTests"
        }
        val output = logFile(file)
        val exitCode = maven(file, options, FileOutputStream(output))
        if (exitCode != 0) {
            System.exit(exitCode)
        }
        return output
    }

    private fun logFile(root: File): File {
        val model = DefaultModelReader().read(File(root, "pom.xml"), mapOf<String, Any>())
        return File(quarkusRoot, "${model.artifactId}.out")
    }

    private fun maven(rootDir: File, options: List<String>, output: OutputStream = System.out): Int {
        if (debug) {
            println("---  Building ${rootDir}")
            println("---  executing:  mvn ${options.joinToString(" ")}")
        }
        val executor = ProcessExecutor()
            .command("mvn", *options.toTypedArray())
            .directory(rootDir)
            .redirectOutput(output)
        if (output != System.out) {
            executor.redirectOutputAlsoTo(System.out)
        }
        return executor.execute().exitValue
    }

    private fun options(): MutableList<String> {
        val options = mutableListOf<String>()
        options += PROPERTIES
        if (bytecode) {
            options += BYTECODE
        }
        resume?.let {
            options += listOf("-rf", if (it.startsWith(":")) it else ":$it")
        }

        return options
    }

    private fun List<String>.bucket(): Pair<Set<String>, Set<String>> {
        val extensions = LinkedHashSet<String>()
        val integrationTests = LinkedHashSet<String>()
        map {
            when {
                File(it).exists() -> File(it).absoluteFile
                File(quarkusRoot, it).exists() -> File(quarkusRoot, it)
                File("$root/integration-tests", it).exists() -> File("$root/integration-tests", it)
                File("$root/extensions", it).exists() -> File("$root/extensions", it)
                else -> throw IllegalArgumentException("$it is neither an integration test nor an extension")
            }
        }.forEach {
            when {
                isIntegrationTest(it) -> integrationTests += it.absolutePath
                else -> extensions += it.absolutePath
            }
        }
        return Pair(extensions, integrationTests)
    }

    private fun isExtension(it: File) = it.relativeToOrNull(File("$root/extensions")) != null

    private fun isIntegrationTest(it: File) = it.startsWith(File("$root/integration-tests"))
}
