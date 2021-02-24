package com.antwerkz.quarkus.picocli

import org.zeroturnaround.exec.ProcessExecutor
import picocli.CommandLine
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File

@CommandLine.Command(name = "qinstall")
class QInstallCommand() : Runnable {
    companion object {
    }

    @Option(names = ["-c", "--clean"], defaultValue = "false")
    var clean: Boolean = false

    @Option(names = ["-d"], defaultValue = "false")
    var debug: Boolean = false

    @Option(names = ["-r", "--root"])
    var root = System.getProperty("user.home") + "/dev/quarkus"

    @Parameters(paramLabel = "module", description = ["One or more modules to build"])
    var modules = listOf<String>()

    constructor(modules: List<String>): this() {
        this.modules = modules;
    }

    override fun run() {
        for (module in modules) {
            (if (module.startsWith("./")) File(module) else File(root, module)).apply {
                if(exists()) {
                    install(this)
                } else {
                    println("${absolutePath} does not exist.")
                    System.exit(-1)
                }
            }
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
        val buildOptions = if (clean) arrayOf("clean") else arrayOf()
        return arrayOf("-f", rootDir.absolutePath, *buildOptions, "install", "-DskipTests")
    }
}
