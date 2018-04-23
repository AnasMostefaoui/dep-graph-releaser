package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.data.Command
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsCommand
import ch.loewenfels.depgraph.data.maven.jenkins.M2ReleaseCommand
import ch.loewenfels.depgraph.data.serialization.CommandStateJson

object ChangeApplier {

    fun createReleasePlanJsonWithChanges(json: String): Pair<Boolean, String> {
        val releasePlanJson = JSON.parse<ReleasePlanJson>(json)
        val changed = applyChanges(releasePlanJson)
        val newJson = JSON.stringify(releasePlanJson)
        return changed to newJson
    }

    private fun applyChanges(releasePlanJson: ReleasePlanJson): Boolean {
        var changed = false
        releasePlanJson.projects.forEach { project ->
            val mavenProjectId = deserializeProjectId(project.id)
            changed = changed or replaceReleaseVersionIfChanged(project, mavenProjectId)

            project.commands.forEachIndexed { index, command ->
                changed = changed or
                    replaceStateIfChanged(command, mavenProjectId, index) or
                    replaceFieldsIfChanged(command, mavenProjectId, index)
            }
        }
        releasePlanJson.config.forEach { arr ->
            if (arr.size != 2) return@forEach

            val input = getTextField("config-${arr[0]}")
            if (arr[1] != input.value) {
                arr[1] = input.value
                changed = true
            }
        }
        return changed
    }


    private fun replaceReleaseVersionIfChanged(project: ProjectJson, mavenProjectId: ProjectId): Boolean {
        val input = getTextFieldOrNull("${mavenProjectId.identifier}:releaseVersion")
        if (input != null && project.releaseVersion != input.value) {
            project.releaseVersion = input.value
            return true
        }
        return false
    }

    private fun replaceStateIfChanged(command: GenericType<Command>, mavenProjectId: ProjectId, index: Int): Boolean {
        val commandStateJson = command.p.state.unsafeCast<CommandStateJson>()
        val state = commandStateJson.state.unsafeCast<String>()
        if (state != CommandStateJson.State.Deactivated.name && state != CommandStateJson.State.Disabled.name) {
            val checkbox = getCheckbox("${mavenProjectId.identifier}:$index${Gui.DISABLE_SUFFIX}")
            if (!checkbox.checked) {
                val previous = JSON.parse<CommandStateJson>(JSON.stringify(commandStateJson))
                commandStateJson.state = CommandStateJson.State.Deactivated.name.unsafeCast<CommandStateJson.State>()
                commandStateJson.previous = previous
                return true
            }
        }
        return false
    }

    private fun replaceFieldsIfChanged(command: GenericType<Command>, mavenProjectId: ProjectId, index: Int): Boolean {
        return when (command.t) {
            JENKINS_MAVEN_RELEASE_PLUGIN, JENKINS_MULTI_MAVEN_RELEASE_PLUGIN -> {
                replaceNextVersionIfChanged(command.p, mavenProjectId, index) or
                replaceBuildUrlIfChanged(command.p, mavenProjectId, index)
            }
            JENKINS_UPDATE_DEPENDENCY -> replaceBuildUrlIfChanged(command.p, mavenProjectId, index)
            else -> throw UnsupportedOperationException("${command.t} is not supported.")
        }
    }

    private fun replaceNextVersionIfChanged(command: Command, mavenProjectId: ProjectId, index: Int): Boolean {
        val m2Command = command.unsafeCast<M2ReleaseCommand>()
        val input = getTextField(Gui.getCommandId(mavenProjectId, index) + Gui.NEXT_DEV_VERSION_SUFFIX)
        if (m2Command.nextDevVersion != input.value) {
            m2Command.asDynamic().nextDevVersion = input.value
            return true
        }
        return false
    }

    private fun replaceBuildUrlIfChanged(command: Command, mavenProjectId: ProjectId, index: Int): Boolean {
        val jenkinsCommand = command.unsafeCast<JenkinsCommand>()
        val guiCommand = Gui.getCommand(mavenProjectId, index)

        val newBuildUrl = guiCommand.asDynamic().buildUrl as? String
        if (newBuildUrl != null && jenkinsCommand.buildUrl != newBuildUrl) {
            jenkinsCommand.asDynamic().buildUrl = newBuildUrl
            return true
        }
        return false
    }

}
