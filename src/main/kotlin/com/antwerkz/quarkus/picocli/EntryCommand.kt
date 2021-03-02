package com.antwerkz.quarkus.picocli

import io.quarkus.picocli.runtime.annotations.TopCommand
import picocli.CommandLine

@TopCommand
@CommandLine.Command(mixinStandardHelpOptions = true,
    subcommands = [ByteCodeCommand::class, QInstallCommand::class, SubsetCommand::class])
class EntryCommand 
