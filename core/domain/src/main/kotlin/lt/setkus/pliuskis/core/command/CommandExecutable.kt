package lt.setkus.pliuskis.core.command

interface CommandExecutable<in I> {
    fun execute(i: I)
}
