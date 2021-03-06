package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.ConfigKey
import ch.loewenfels.depgraph.data.*
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsCommand
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMultiMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.loewenfels.depgraph.hasNextOnTheSameLevel
import ch.loewenfels.depgraph.toPeekingIterator
import kotlinx.html.*
import kotlinx.html.dom.append
import org.w3c.dom.*
import kotlin.browser.document
import kotlin.dom.addClass
import kotlin.dom.removeClass

class Gui(private val releasePlan: ReleasePlan, private val menu: Menu) {
    private val toggler = Toggler(releasePlan, menu)

    fun load() {
        val rootProjectId = releasePlan.rootProjectId
        val htmlTitle = (rootProjectId as? MavenProjectId)?.artifactId ?: rootProjectId.identifier
        document.title = "Release $htmlTitle"
        setUpMessages(releasePlan.warnings, "warnings", { showWarning(it) })
        setUpMessages(releasePlan.infos, "infos", { showInfo(it) })
        setUpConfig(releasePlan)
        setUpProjects()
        toggler.registerToggleEvents()

        //TODO we should check if releasePlant.state is inProgress. In such a case it might be that command states
        // have changed already and we need to update the state let's say the browser crashes during release and we
        // have already triggered a job and know it is queued in this case we should check if it is no longer queued
        // but already started etc.
        //TODO also for state failed, might be that it failed because maxWaitingTime was over
    }

    private fun setUpMessages(messages: List<String>, id: String, action: (String) -> Unit) {
        if (messages.isNotEmpty()) {
            val minimized = elementById("${id}Minimized")
            minimized.style.display = "block"
            minimized.addEventListener("click", {
                minimized.style.display = "none"
                messages.forEach(action)
            })
        }
        val messagesDiv = elementById("messages")
        elementById(HIDE_MESSAGES_HTML_ID).addClickEventListener {
            document.querySelectorAll("#messages > div")
                .asList()
                .forEach { messagesDiv.removeChild(it) }
        }
    }

    private fun setUpConfig(releasePlan: ReleasePlan) {
        //TODO add description for each property
        elementById("config").append {
            div {
                fieldWithLabel(RELEASE_ID_HTML_ID, "ReleaseId", releasePlan.releaseId)

                val config = releasePlan.config
                listOf(
                    ConfigKey.COMMIT_PREFIX,
                    ConfigKey.UPDATE_DEPENDENCY_JOB,
                    ConfigKey.REMOTE_REGEX,
                    ConfigKey.REMOTE_JOB,
                    ConfigKey.REGEX_PARAMS
                ).forEach { key ->
                    fieldWithLabel("config-${key.asString()}", key.asString(), config[key] ?: "")
                }
                val key = ConfigKey.JOB_MAPPING
                textFieldWithLabel("config-${key.asString()}", key.asString(), config[key]?.replace("|", "\n") ?: "")
            }
        }
    }

    private fun setUpProjects() {
        val set = hashSetOf<ProjectId>()
        val pipeline = elementById(PIPELINE_HTML_ID)
        pipeline.asDynamic().state = releasePlan.state
        pipeline.append {
            val itr = releasePlan.iterator().toPeekingIterator()
            var level: Int
            while (itr.hasNext()) {
                val project = itr.next()
                level = project.level

                div("level l$level") {
                    if (!project.isSubmodule) {
                        project(project)
                    }
                    set.add(project.id)
                    while (itr.hasNextOnTheSameLevel(level)) {
                        val nextProject = itr.next()
                        if (!nextProject.isSubmodule) {
                            project(nextProject)
                        }
                        set.add(nextProject.id)
                    }
                }
            }
        }
        val involvedProjects = set.size
        showStatus("Projects involved: $involvedProjects")
        if (involvedProjects != releasePlan.getNumberOfProjects()) {
            showError("Not all dependent projects are involved in the process, please report a bug. The following where left out\n" +
                (releasePlan.getProjectIds() - set).joinToString("\n") { it.identifier }
            )
        }
    }

    private fun DIV.project(project: Project) {
        div {
            val hasCommands = project.commands.isNotEmpty()
            classes = setOf(
                "project",
                if (project.isSubmodule) "submodule" else "",
                if (!hasCommands) "withoutCommands" else "",
                if (releasePlan.hasSubmodules(project.id)) "withSubmodules" else ""
            )

            val identifier = project.id.identifier
            this.id = identifier
            div("title") {
                if (hasCommands) {
                    toggle(
                        "$identifier$DEACTIVATE_ALL_SUFFIX",
                        "deactivate all commands",
                        project.commands.any { it.state !is CommandState.Deactivated },
                        false
                    )
                }
                span {
                    projectId(project.id)
                }
            }
            if (!project.isSubmodule) {
                div("fields") {
                    fieldReadOnlyWithLabel(
                        "$identifier:currentVersion",
                        "Current Version",
                        project.currentVersion
                    )
                    fieldWithLabel("$identifier:releaseVersion", "Release Version", project.releaseVersion)
                }
            }
            commands(project)

            if (project.isSubmodule) {
                // means we are within a multi-module and might want to show submodules of this submodule
                submodules(project.id)
            }
        }
    }

    private fun CommonAttributeGroupFacade.projectId(id: ProjectId) {
        if (id is MavenProjectId) {
            title = id.identifier
            +id.artifactId
        } else {
            +id.identifier
        }
    }

    private fun INPUT.projectId(id: ProjectId) {
        if (id is MavenProjectId) {
            title = id.identifier
            value = id.artifactId
        } else {
            value = id.identifier
        }
    }

    private fun DIV.commands(project: Project) {
        project.commands.forEachIndexed { index, command ->
            div {
                val commandId = getCommandId(project, index)
                id = commandId
                classes = setOf("command", stateToCssClass(command.state))
                div("commandTitle") {
                    id = "$commandId$TITLE_SUFFIX"
                    +command::class.simpleName!!
                }
                div("fields") {
                    fieldsForCommand(commandId, project.id, command)
                }
                val div = getUnderlyingHtmlElement().asDynamic()
                div.state = command.state
                if (command is JenkinsCommand) {
                    div.buildUrl = command.buildUrl
                }
            }
        }
    }

    private fun DIV.fieldWithLabel(id: String, label: String, text: String) {
        fieldWithLabel(id, label, text, {})
    }

    private fun DIV.fieldReadOnlyWithLabel(id: String, label: String, text: String, inputAct: INPUT.() -> Unit = {}) {
        fieldWithLabel(id, label, text, { disabled = true; inputAct() })
    }

    private fun DIV.fieldWithLabel(id: String, label: String, text: String, inputAct: INPUT.() -> Unit) {
        div {
            label("fields") {
                htmlFor = id
                +label
            }
            textInput {
                this.id = id
                value = text
                inputAct()
                val input = getUnderlyingHtmlElement() as HTMLInputElement
                input.addEventListener("keyup", { menu.activateSaveButton() })
                disableUnDisableForReleaseStartAndEnd(input, input)
            }
        }
    }

    private fun DIV.textFieldWithLabel(id: String, label: String, text: String) {
        div {
            label("fields") {
                htmlFor = id
                +label
            }
            textArea {
                this.id = id
                +text
                val htmlTextAreaElement = getUnderlyingHtmlElement() as HTMLTextAreaElement
                htmlTextAreaElement.addEventListener("keyup", { menu.activateSaveButton() })
                //for what disableUnDisableForReleaseStartAndEnd needs, title and disabled, it is ok to make the unsafe cast
                //TODO change in case https://github.com/Kotlin/kotlinx.html/issues/87 is implemented
                val input = htmlTextAreaElement.unsafeCast<HTMLInputElement>()
                disableUnDisableForReleaseStartAndEnd(input, htmlTextAreaElement)
            }
        }
    }

    private fun DIV.fieldsForCommand(idPrefix: String, projectId: ProjectId, command: Command) {
        val cssClass = when (command) {
            is ReleaseCommand -> "release"
            else -> ""
        }

        toggle(
            "$idPrefix$DEACTIVATE_SUFFIX",
            "Click to deactivate command",
            command.state !is CommandState.Deactivated,
            command.state === CommandState.Disabled,
            cssClass
        )
        a(classes = "state") {
            id = "$idPrefix$STATE_SUFFIX"
            i("material-icons") {
                span()
                id = "$idPrefix:status.icon"
            }
            if (command is JenkinsCommand) {
                href = command.buildUrl ?: ""
            }
            title = stateToTitle(command.state)
        }

        when (command) {
            is JenkinsMavenReleasePlugin ->
                appendJenkinsMavenReleasePluginField(idPrefix, command)
            is JenkinsMultiMavenReleasePlugin ->
                appendJenkinsMultiMavenReleasePluginFields(idPrefix, projectId, command)
            is JenkinsUpdateDependency ->
                appendJenkinsUpdateDependencyField(idPrefix, command)
            else ->
                showError("Unknown command found, cannot display its fields.\n$command")
        }
    }

    private fun DIV.appendJenkinsMavenReleasePluginField(idPrefix: String, command: JenkinsMavenReleasePlugin) {
        fieldNextDevVersion(idPrefix, command, command.nextDevVersion)
    }

    private fun DIV.fieldNextDevVersion(
        idPrefix: String,
        command: Command,
        nextDevVersion: String
    ) {
        fieldWithLabel("$idPrefix${Gui.NEXT_DEV_VERSION_SUFFIX}", "Next Dev Version", nextDevVersion) {
            if (command.state === CommandState.Disabled) {
                disabled = true
            }
        }
    }

    private fun DIV.appendJenkinsMultiMavenReleasePluginFields(
        idPrefix: String,
        projectId: ProjectId,
        command: JenkinsMultiMavenReleasePlugin
    ) {
        fieldNextDevVersion(idPrefix, command, command.nextDevVersion)
        submodules(projectId)
    }

    private fun DIV.submodules(projectId: ProjectId) {
        val submodules = releasePlan.getSubmodules(projectId)
        if (submodules.isEmpty()) return

        div("submodules") {
            submodules.forEach {
                project(releasePlan.getProject(it))
            }
        }
    }

    private fun DIV.appendJenkinsUpdateDependencyField(idPrefix: String, command: JenkinsUpdateDependency) {
        fieldReadOnlyWithLabel(
            "$idPrefix:groupId",
            "Dependency",
            command.projectId.identifier,
            { projectId(command.projectId) })
    }

    private fun DIV.toggle(
        id: String,
        title: String,
        checked: Boolean,
        disabled: Boolean,
        checkboxCssClass: String = ""
    ) {
        label("toggle") {
            checkBoxInput(classes = checkboxCssClass) {
                this.id = id
                this.checked = checked && !disabled
                this.disabled = disabled
            }
            span("slider") {
                this.id = "$id$SLIDER_SUFFIX"
                this.title = title
                if (disabled) {
                    this.title = STATE_DISABLED
                }
            }
        }
    }

    companion object {
        private const val PIPELINE_HTML_ID = "pipeline"
        const val RELEASE_ID_HTML_ID = "releaseId"
        const val HIDE_MESSAGES_HTML_ID = "hideMessages"

        const val DEACTIVATE_SUFFIX = ":deactivate"
        const val DEACTIVATE_ALL_SUFFIX = ":deactivateAll"
        const val SLIDER_SUFFIX = ":slider"
        const val DISABLED_RELEASE_IN_PROGRESS = "disabled due to release which is in progress."
        const val DISABLED_RELEASE_SUCCESS = "Release successful, use a new pipeline for a new release."
        const val NEXT_DEV_VERSION_SUFFIX = ":nextDevVersion"
        const val STATE_SUFFIX = ":state"
        const val TITLE_SUFFIX = ":title"

        private const val STATE_WAITING = "Wait for dependent projects to complete."
        const val STATE_READY = "Ready to be queued for execution."
        const val STATE_READY_TO_RE_TRIGGER = "Ready to be re-scheduled"
        const val STATE_QUEUEING = "Currently queueing the job."
        const val STATE_IN_PROGRESS = "Job is running."
        const val STATE_SUCCEEDED = "Job completed successfully."
        const val STATE_FAILED = "Job completed successfully."
        private const val STATE_DEACTIVATED = "Currently deactivated, click to activate"
        const val STATE_DISABLED = "Command disabled, cannot be reactivated."

        fun getCommandId(project: Project, index: Int) = getCommandId(project.id, index)
        fun getCommandId(projectId: ProjectId, index: Int) = "${projectId.identifier}:$index"
        fun getCommand(project: Project, index: Int) = getCommand(project.id, index)
        fun getCommand(projectId: ProjectId, index: Int): HTMLElement = elementById(getCommandId(projectId, index))

        fun getCommandState(projectId: ProjectId, index: Int): CommandState {
            return getCommand(projectId, index).asDynamic().state as CommandState
        }

        fun disableUnDisableForReleaseStartAndEnd(input: HTMLInputElement, titleElement: HTMLElement) {
            Menu.registerForReleaseStartEvent {
                input.asDynamic().oldDisabled = input.disabled
                input.disabled = true
                titleElement.setTitleSaveOld(DISABLED_RELEASE_IN_PROGRESS)
            }
            Menu.registerForReleaseEndEvent { success ->
                if (success) {
                    titleElement.title = DISABLED_RELEASE_SUCCESS
                } else {
                    input.disabled = input.asDynamic().oldDisabled as Boolean
                    titleElement.title = titleElement.getOldTitle()
                }
            }
        }

        fun changeStateOfCommandAndAddBuildUrl(
            project: Project,
            index: Int,
            newState: CommandState,
            title: String,
            buildUrl: String
        ) {
            changeStateOfCommand(project, index, newState, title)
            val commandId = getCommandId(project, index)
            elementById<HTMLAnchorElement>("$commandId$STATE_SUFFIX").href = buildUrl
            elementById(commandId).asDynamic().buildUrl = buildUrl
        }


        fun changeStateOfCommand(project: Project, index: Int, newState: CommandState, title: String) {
            val commandId = getCommandId(project, index)
            val command = elementById(commandId)
            val dynCommand = command.asDynamic()
            val previousState = dynCommand.state as CommandState
            try {
                dynCommand.state = previousState.checkTransitionAllowed(newState)
            } catch (e: IllegalStateException) {
                val commandTitle = elementById(commandId + TITLE_SUFFIX)
                throw IllegalStateException(
                    "Cannot change the state of the command ${commandTitle.innerText} (${index + 1}. command) " +
                        "of the project ${project.id.identifier}",
                    e
                )
            }
            command.removeClass(stateToCssClass(previousState))
            command.addClass(stateToCssClass(newState))
            elementById("$commandId$STATE_SUFFIX").title = title
        }

        private fun stateToCssClass(state: CommandState) = when (state) {
            is CommandState.Waiting -> "waiting"
            CommandState.Ready -> "ready"
            CommandState.ReadyToRetrigger -> "readyToRetrigger"
            CommandState.Queueing -> "queueing"
            CommandState.InProgress -> "inProgress"
            CommandState.Succeeded -> "succeeded"
            is CommandState.Failed -> "failed"
            is CommandState.Deactivated -> "deactivated"
            CommandState.Disabled -> "disabled"
        }


        fun getReleaseState() = elementById(Gui.PIPELINE_HTML_ID).asDynamic().state as ReleaseState

        fun changeReleaseState(newState: ReleaseState) {
            val pipeline = elementById(Gui.PIPELINE_HTML_ID).asDynamic()
            pipeline.state = getReleaseState().checkTransitionAllowed(newState)
        }

        private fun stateToTitle(state: CommandState) = when (state) {
            is CommandState.Waiting -> STATE_WAITING
            CommandState.Ready -> STATE_READY
            CommandState.ReadyToRetrigger -> STATE_READY_TO_RE_TRIGGER
            CommandState.Queueing -> STATE_QUEUEING
            CommandState.InProgress -> STATE_IN_PROGRESS
            CommandState.Succeeded -> STATE_SUCCEEDED
            CommandState.Failed -> STATE_FAILED
            is CommandState.Deactivated -> STATE_DEACTIVATED
            CommandState.Disabled -> STATE_DISABLED
        }
    }
}
