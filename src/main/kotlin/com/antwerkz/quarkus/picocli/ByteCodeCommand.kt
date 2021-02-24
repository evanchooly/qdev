package com.antwerkz.quarkus.picocli

import org.zeroturnaround.exec.ProcessExecutor
import picocli.CommandLine
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import java.io.FileOutputStream

@CommandLine.Command(name = "bytecode")
class ByteCodeCommand() : Runnable {
    companion object {
        val TARGET = File("target/bytecode")
    }

    @Option(names = ["-d"], defaultValue = "false")
    private var debug: Boolean = false

    @Option(names = ["-v"], defaultValue = "true")
    private var verbose: Boolean = true

    @Option(names = ["-m", "--modules"], split=",", description = ["List of modules to build in quarkus before building the local project"])
    private var modules = listOf<String>()

    @Parameters(paramLabel = "entity", description = ["One or more entities to catalog"])
    private var entities = listOf<String>()

    init {
        TARGET.mkdirs()
    }

    override fun run() {
        val qInstallCommand = QInstallCommand(modules)
        qInstallCommand.debug = true
        qInstallCommand.run()
        for (entity in entities) {
            for (type in listOf("main", "test")) {
                for (source in Source.values()) {
                    val directory = File(source.dir(type))
                    if (directory.isDirectory) {
                        directory.walk()
                            .firstOrNull { f -> f.name == "${entity}.${source.ext()}" }
                            ?.let {
                                val packageName = readPackage(it)
                                for (base in listOf(entity, "${entity}\$Companion", "${entity}Dao", "${entity}Repository")) {
                                    dump(packageName, base)
                                }
                            }
                    }
                }
            }
        }
    }

    private fun readPackage(it: File) = it.readLines()
        .first { line -> line.startsWith("package") }
        .substringAfter(" ")
        .substringBefore(";")

    private fun dump(packageName: String, baseName: String) {
        val className = "$packageName.$baseName"

        println("Scanning for ${baseName}")
        if (!output(File("target/classes"), className, File(TARGET, "${baseName}.txt"))) {
            output(File("target/test-classes"), className, File(TARGET, "${baseName}.txt"))
        }
        output(File("target/quarkus-app/quarkus/transformed-bytecode.jar"), className, File(TARGET, "${baseName}-quarkus.txt"))
    }

    private fun javap(rootDir: File, className: String, outputFile: File): Boolean {
        if (debug) println("   \\---  Looking in ${rootDir}")
        val exitValue = ProcessExecutor()
            .command("javap", *options(), "-classpath", rootDir.path, className)
            .redirectOutput(FileOutputStream(outputFile))
            .execute()
            .exitValue

        if (exitValue != 0) {
            outputFile.delete()
        }

        return exitValue == 0
    }

    private fun options(): Array<String> {
        val options = arrayOf("-c", "-s", "-p")
        return if (verbose) options + "-v" else options
    }

    private fun output(rootDir: File, className: String, outputFile: File): Boolean {
        return javap(rootDir, className, outputFile)
    }
}
