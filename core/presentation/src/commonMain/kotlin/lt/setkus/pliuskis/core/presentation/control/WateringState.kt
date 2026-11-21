package lt.setkus.pliuskis.core.presentation.control

/** Progress of the water command, for the water button's feedback. */
enum class WateringState {
    /** Ready to send. */
    IDLE,

    /** The command is on its way to the device; another tap is ignored. */
    SENDING,

    /** The broker took the command (its PUBACK arrived). Turns back to [IDLE] after a short confirmation. */
    STARTED,

    /** The command could not be sent. The user can try again. */
    FAILED,
}
