package com.antwerkz.quarkus.picocli

import java.io.File
import java.io.FileOutputStream
import org.zeroturnaround.exec.ProcessExecutor
import picocli.CommandLine
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

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
        .firstOrNull { line -> line.startsWith("package") }
        ?.substringAfter(" ")
        ?.substringBefore(";")
        ?.plus(".")
        ?: ""

    private fun dump(file: File) {
        if (debug) println("dumping $file")
        val packageName = readPackage(file)
        val baseName = "${packageName}${file.nameWithoutExtension}"

        for (suffix in listOf("", "\$Companion", "Dao", "Repository")) {
            val className = baseName + suffix
            val baseFileName = file.nameWithoutExtension + suffix
            listOf(File("target/classes"), File("target/test-classes"))
                .flatMap {
                    it.walkTopDown()
                        .filter { found ->
                            found.nameWithoutExtension == baseName
                                || found.nameWithoutExtension.startsWith("$baseName\$")
                        }
                        .toList()
                }
                .forEach {
                    val name = "${packageName}${it.nameWithoutExtension}"
                    var root = File(it.path.replace("${name}.class", ""))
                    output(root, name, File(TARGET, "${it.nameWithoutExtension}.txt"))
                }

            File("target/quarkus-app/app")
                .listFiles()
                .flatMap {
                    it.walkTopDown()
                        .filter { found ->
                            found.extension == "jar"
                        }
                        .toList()
                }
                .forEach {
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
