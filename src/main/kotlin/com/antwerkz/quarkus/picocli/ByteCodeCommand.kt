package com.antwerkz.quarkus.picocli

import org.zeroturnaround.exec.ProcessExecutor
import picocli.CommandLine
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import java.io.FileFilter
import java.io.FileOutputStream

@CommandLine.Command(name = "bytecode")
class ByteCodeCommand : Runnable {
    companion object {
        val TARGET = File("target/bytecode")
    }

    @Option(names = ["-d"], defaultValue = "false")
    var debug: Boolean = false

    @Option(names = ["-l", "--local"], defaultValue = "true")
    var local: Boolean = true

    @Option(names = ["-v"], defaultValue = "true")
    var verbose: Boolean = true

    @Option(names = ["-m", "--modules"], split=",", description = ["List of modules to build in quarkus before building the local project"])
    var modules = listOf<String>()

    @Parameters(paramLabel = "entity", description = ["One or more entities to catalog"])
    var entities = listOf<String>()

    init {
        TARGET.mkdirs()
    }

    override fun run() {
        val qInstallCommand = QInstallCommand(modules)
        qInstallCommand.debug = true
        qInstallCommand.run()

        if (local) {
            val localCommand = QInstallCommand(listOf("./"))
            localCommand.debug = true
            localCommand.run()
        }
        for (entity in entities) {
            for (type in listOf("main", "test")) {
                for (source in Source.values()) {
                    val directory = File(source.dir(type))
                    if (directory.isDirectory) {
                        directory.walk()
                            .filter { f ->
//                                println("f.nameWithoutExtension = ${f.nameWithoutExtension}")
//                                println("f.nameWithoutExtension.startsWith(entity) = ${f.nameWithoutExtension.startsWith(entity)}")
                                f.nameWithoutExtension.startsWith(entity)
                            }
                            .forEach {
                                dump(it)
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

    private fun dump(file: File) {
        val packageName = readPackage(file)
        val baseName = "$packageName.${file.nameWithoutExtension}"

        for (suffix in listOf("", "\$Companion", "Dao", "Repository")) {
            val className = baseName + suffix
            println("Scanning $className")
            val baseFileName = file.nameWithoutExtension + suffix
            if (!output(File("target/classes"), className, File(TARGET, "$baseFileName.txt"))) {
                output(File("target/test-classes"), className, File(TARGET, "$baseFileName.txt"))
            }
            File("target/quarkus-app/")
                .listFiles { pathname -> pathname.name.endsWith(".jar") }
                ?.forEach {
                    output(it, className, File(TARGET, "$baseFileName-quarkus.txt"))
                }
        }
    }

    private fun javap(rootDir: File, className: String, outputFile: File): Boolean {
        if (debug) println("   \\---  Looking in $rootDir")
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
        val options = arrayOf("-c", "-s", "-p", "-l")
        return if (verbose) options + "-v" else options
    }

    private fun output(rootDir: File, className: String, outputFile: File): Boolean {
        return javap(rootDir, className, outputFile)
    }
}
