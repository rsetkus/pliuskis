package lt.setkus.pliuskis.core.command

interface CommandExecutable<in I> {
    suspend fun execute(i: I)
}
