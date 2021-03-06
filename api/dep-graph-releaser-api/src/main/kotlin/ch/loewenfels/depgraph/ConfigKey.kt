package ch.loewenfels.depgraph

enum class ConfigKey(private val key: kotlin.String) {
    COMMIT_PREFIX("commitPrefix"),
    UPDATE_DEPENDENCY_JOB("updateDependencyJob"),
    REMOTE_REGEX("remoteRegex"),
    REMOTE_JOB("remoteJob"),
    REGEX_PARAMS("regexParams"),
    JOB_MAPPING("jobMapping"),
    ;

    fun asString(): String = key

    companion object {
        fun fromString(key: String): ConfigKey {
            return values().first { it.asString() == key  }
        }
    }
}
