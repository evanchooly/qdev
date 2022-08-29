package com.antwerkz.quarkus.picocli

import org.zeroturnaround.exec.ProcessExecutor
import picocli.CommandLine
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import kotlin.system.exitProcess

@CommandLine.Command(name = "qinstall")
class QInstallCommand() : Runnable {
    @Option(names = ["-c", "--clean"], defaultValue = "false")
    var clean: Boolean = false

    @Option(names = ["-d"], defaultValue = "false")
    var debug: Boolean = false

    @Option(names = ["-r", "--root"])
    var root = System.getProperty("user.home") + "/dev/quarkus"

    @Option(names = ["-t", "--test"], description = ["If true, run the tests of the installed modules. Default: false"])
    var test = false

    @Parameters(paramLabel = "module", description = ["One or more modules to build"])
    var modules = listOf<String>()

    constructor(modules: List<String>): this() {
        this.modules = modules;
    }

    override fun run() {
        for (module in modules) {
            install(module.normalize())
        }
    }

    private fun String.normalize(): File {
        val file = if (startsWith("./") || startsWith("/")) File(this) else File(root, this)
        return if(file.exists()) file else {
            println("${file.absolutePath} does not exist.")
            exitProcess(-1)
        }
    }

    private fun install(rootDir: File): Boolean {
        if (debug) println("---  Building ${rootDir}")
        val exitValue = ProcessExecutor()
            .command("mvn", *options(rootDir))
            .redirectOutput(System.out)
            .redirectError(System.err)
            .execute()
            .exitValue

        if (exitValue != 0) {
            System.exit(exitValue)
        }

        return exitValue == 0
    }
    private fun options(rootDir: File): Array<String> {
        val buildOptions = mutableListOf("-f", rootDir.absolutePath)
        buildOptions += if (clean) listOf("clean") else listOf()
        buildOptions += listOf("source:jar", "install")
        buildOptions += if (test) listOf() else listOf("-DskipTests")

        return buildOptions.toTypedArray()
    }
}
