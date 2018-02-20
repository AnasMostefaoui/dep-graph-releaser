package ch.loewenfels.depgraph.serialization

import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.ProjectId
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson

/**
 * Responsible to serialize [CommandState].
 */
object CommandStateAdapter {

    @ToJson
    fun toJson(state: CommandState): CommandStateJson = when (state) {
        is CommandState.Waiting -> CommandStateJson(State.Waiting, state.dependencies)
        CommandState.Ready -> CommandStateJson(State.Ready)
        CommandState.InProgress -> CommandStateJson(State.InProgress)
        CommandState.Succeeded -> CommandStateJson(State.Succeeded)
        is CommandState.Failed -> CommandStateJson(State.Failed, state.message)
        is CommandState.Deactivated -> CommandStateJson(State.Deactivated, toJson(state.previous))
    }

    @FromJson
    fun fromJson(json: CommandStateJson): CommandState = when (json.state) {
        State.Waiting -> CommandState.Waiting(json.dependencies ?: throwIllegal("dependencies", "Waiting"))
        State.Ready -> CommandState.Ready
        State.InProgress -> CommandState.InProgress
        State.Succeeded -> CommandState.Succeeded
        State.Failed -> CommandState.Failed(json.message ?: throwIllegal("message", "Failed"))
        State.Deactivated -> CommandState.Deactivated(fromJson(json.previous ?: throwIllegal("previous", "Deactivated")))
    }

    private fun throwIllegal(fieldName: String, stateName: String): Nothing = throw IllegalArgumentException("$fieldName must be defined for state $stateName")
}

data class CommandStateJson(
    val state: State,
    val message: String?,
    val dependencies: Set<ProjectId>?,
    val previous: CommandStateJson?
) {
    constructor(state: State) : this(state, null, null, null)
    constructor(state: State, message: String) : this(state, message, null, null)
    constructor(state: State, dependencies: Set<ProjectId>) : this(state, null, dependencies, null)
    constructor(state: State, previous: CommandStateJson) : this(state, null, null, previous)
}

enum class State {
    Waiting,
    Ready,
    InProgress,
    Succeeded,
    Failed,
    Deactivated,
}
