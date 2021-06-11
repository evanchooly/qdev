package com.antwerkz.quarkus.picocli

import org.zeroturnaround.exec.ProcessExecutor
import picocli.CommandLine
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

@CommandLine.Command(name = "subset")
class SubsetCommand : Runnable {
    companion object {
        val PROPERTIES = listOf("-Dtest-postgresql", "-Ddocker")
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

    @Parameters(paramLabel = "test", description = ["One or more tests to run"])
    var tests = listOf<String>()
    val quarkusRoot = File(System.getProperty("user.home"), "dev/quarkus")
    override fun run() {
        if (full) {
            maven(
                quarkusRoot, options(
                    mutableListOf(
                        "-T", "4C", "-DskipTests", "-DskipITs", "-DskipExtensionValidation",
                        "-Dskip.gradle.tests", "clean", "install"
                    )
                )
            )
        } else {
            val qinstall = QInstallCommand(
                tests.filter {
                    File(quarkusRoot, it).exists()
                })
            qinstall.debug = debug
            qinstall.clean = clean
            qinstall.test = test
            qinstall.run()
        }

        tests.forEach { test ->
            val testRoot = File(quarkusRoot, "integration-tests/$test")
            if (testRoot.exists()) {
                val core = mutableListOf<String>()
                if (clean) {
                    core += "clean"
                }

                core += "verify"
                val options = options(core)
                if (native) {
                    options += "-Dnative"
                }
                val output = File(quarkusRoot, "${test}.out")
                maven(testRoot, options, FileOutputStream(output))
            }
        }

        tests.forEach { test ->
            val output = File(quarkusRoot, "${test}.out")
            if(output.exists()) {
                if (output.readLines()
                        .any { it.contains("[ERROR]") }
                ) {
                    println("ERROR found in ${test}")
                } else {
                    output.delete()
                }
            }
        }
    }

    private fun options(core: MutableList<String>): MutableList<String> {
        val options = core
        options += PROPERTIES
        resume?.let {
            options += listOf("-rf", if (it.startsWith(":")) it else ":$it")
        }

        return options
    }

    private fun maven(rootDir: File, options: List<String>, output: OutputStream = System.out): Int {
        if (debug) println("---  Building ${rootDir}")
        val executor = ProcessExecutor()
            .command("mvn", *options.toTypedArray())
            .directory(rootDir)
            .redirectOutput(output)
        if (output != System.out) {
            executor.redirectOutputAlsoTo(System.out);
        }

        return executor.execute().exitValue
    }
}
