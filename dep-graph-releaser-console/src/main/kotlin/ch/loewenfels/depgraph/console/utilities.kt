package ch.loewenfels.depgraph.console

fun expectedArgsAndGiven(command: ConsoleCommand, args: Array<out String>) = """
    |${command.arguments}
    |
    |${getGivenArgs(args)}
    """.trimMargin()

fun getGivenArgs(args: Array<out String>) = "Given: ${args.joinToString(" ")}"
