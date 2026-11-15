package com.entreco.eqms.feature.scaffold

/** Fixed DutchDoxx product identity. Deliberately independent of per-project branding. */
object ProductIdentity {
    const val NAME = "DutchDoxx"
    const val ATTRIBUTION = "Powered by DutchDoxx"
    const val AUTHOR = "Entreco"
    const val LICENCE_NOTICE = "Licence: not yet specified. The project owner must set it before release."
    const val DESCRIPTION = "An eQMS quality workspace. Drafts are not approvals."

    private const val MAX_PROJECT_TITLE = 60

    /** A project name may qualify the title but never replace the product name. */
    fun windowTitle(projectName: String?): String {
        val project = projectName.orEmpty().filter { !it.isISOControl() }.trim().take(MAX_PROJECT_TITLE)
        return if (project.isEmpty()) NAME else "$project \u2014 $NAME"
    }
}
