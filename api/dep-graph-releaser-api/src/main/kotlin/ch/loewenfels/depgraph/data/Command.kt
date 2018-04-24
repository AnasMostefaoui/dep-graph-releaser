package ch.loewenfels.depgraph.data

import kotlin.reflect.KClass

interface Command {
    val state: CommandState

    /**
     * Makes a copy of this command but with a [newState].
     */
    fun asNewState(newState: CommandState): Command

    /**
     * Makes a copy of this command but with [CommandState.Deactivated] as [state]
     *
     * @throws IllegalStateException in case the state was already [CommandState.Deactivated]
     */
    fun asDeactivated(): Command {
        check(state !is CommandState.Deactivated) {
            "Cannot deactivate an already deactivated command: $this"
        }
        return asNewState(CommandState.Deactivated(state))
    }

    /**
     * Makes a copy of this command but with [CommandState.Disabled] as [state]
     *
     * @throws IllegalStateException in case the state was already [CommandState.Disabled]
     */
    fun asDisabled(): Command {
        check(state !== CommandState.Disabled) {
            "Cannot disable an already disabled command: $this"
        }
        return asNewState(CommandState.Disabled)
    }
}

sealed class CommandState {
    data class Waiting(val dependencies: Set<ProjectId>) : CommandState()
    object Ready : CommandState()
    /**
     * Command is queued to be executed.
     */
    object Queueing : CommandState()

    object InProgress : CommandState()
    object Succeeded : CommandState()
    object Failed : CommandState()
    data class Deactivated(val previous: CommandState) : CommandState()
    /**
     * Such a command cannot be reactivated in contrast to [Deactivated].
     */
    object Disabled : CommandState()

    fun checkTransitionAllowed(newState: CommandState): CommandState {
        check(this !== Disabled) { "Cannot transition to any state if current state is Disabled." }
        check(this::class != newState::class) {
            "Cannot transition to the same state as the current." +
                //TODO use $this in stead of $getRepresentation(...) once https://youtrack.jetbrains.com/issue/KT-23970 is fixed
                "\nCurrent: ${getRepresentation(this)}" +
                "\nNew: ${getRepresentation(newState)}"
        }

        when (newState) {
            Ready -> {
                checkNewState(newState, Waiting::class)
                check((this as Waiting).dependencies.isEmpty()) {
                    "Can only change from Waiting to Ready if there are not any dependencies left." +
                        //TODO use $this in stead of $getRepresentation(...) once https://youtrack.jetbrains.com/issue/KT-23970 is fixed
                        "\nState was: ${getRepresentation(this)}"
                }
            }
            Queueing -> checkNewState(newState, Ready::class)
            InProgress -> checkNewState(newState, Queueing::class)
            Succeeded -> checkNewState(newState, InProgress::class)
        }
        return newState
    }

    private fun getRepresentation(state: CommandState): String {
        val representation = this.toString()
        return if (representation == "[object Object]") state::class.simpleName!! else representation
    }

    private fun checkNewState(newState: CommandState, requiredState: KClass<out CommandState>) {
        check(requiredState.isInstance(this)) {

            "Cannot transition to ${newState::class.simpleName} because state is not ${requiredState.simpleName}." +
                //TODO use $this in stead of $getRepresentation(...) once https://youtrack.jetbrains.com/issue/KT-23970 is fixed
                "\nState was: ${getRepresentation(this)}"
        }
    }
}
