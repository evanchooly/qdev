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

    @Option(names = ["-n", "--native"], defaultValue = "false")
    var native: Boolean = false

    @Option(names = ["-s", "--skipPanache"], defaultValue = "false")
    var skipPanache: Boolean = false

    @Option(names = ["-r", "--resume"])
    var resume: String? = null

    @Parameters(paramLabel = "test", description = ["One or more tests to run"])
    var tests = listOf<String>()

    val quarkusRoot = File(System.getProperty("user.home"), "dev/quarkus")

    override fun run() {
        if(full) {
            maven(quarkusRoot, options(mutableListOf("-T", "C1", "-DskipTests", "-DskipITs", "-DskipExtensionValidation",
                        "-Dskip.gradle.tests", "clean", "install")))
        } else if (!skipPanache) {
            maven(File(quarkusRoot, "extensions/panache"), options(mutableListOf("install", "-DskipTests")))
        }

        tests.forEach { test ->
            val options = options(mutableListOf("install"))
            if(native) {
                options += "-Pnative"
            }
            val output = File(quarkusRoot, "${test}.out")
            maven(File(quarkusRoot, "integration-tests/$test"), options,
                FileOutputStream(output))
        }

        tests.forEach { test ->
            val output = File(quarkusRoot, "${test}.out")
            if(output.readLines()
                .any { it.contains("[ERROR]") }) {
                println("ERROR found in ${test}")
            } else {
                output.delete()
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
