package com.entreco.eqms.feature.scaffold

import com.entreco.eqms.feature.ui.BrandTokens

data class ProjectDirectory(val name: String, val path: String)

data class DirectoryListing(
    val path: String,
    val parent: String?,
    val children: List<ProjectDirectory>,
)

interface ProjectCapabilities {
    suspend fun listDirectory(path: String?): Result<DirectoryListing>
    suspend fun createFolder(parent: String, name: String): Result<String>
    suspend fun openProject(path: String, create: Boolean): Result<String>

    /** Brand tokens of the project at [path]; `null` when the capability has none. */
    suspend fun brandTokens(path: String): Result<BrandTokens?> = Result.success(null)

    /** Stored branding of the project at [path], including how to draw a generated logo. */
    suspend fun projectBranding(path: String): Result<BrandingPreview?> =
        brandTokens(path).map { tokens -> tokens?.let { BrandingPreview(it, null) } }

    /** Records [draft] as the branding of the project at [path] (journaled by core). */
    suspend fun saveBranding(path: String, draft: BrandingDraft): Result<BrandingPreview> =
        Result.failure(UnsupportedOperationException("Saving branding is not available."))

    /** Asks core what it would record for [draft]; never writes. */
    suspend fun previewBranding(draft: BrandingDraft): Result<BrandingPreview> =
        Result.failure(UnsupportedOperationException("Branding preview is not available."))

    /** Creates the project at [path] and records [draft] as its branding. */
    suspend fun createProject(path: String, draft: BrandingDraft): Result<String> = openProject(path, create = true)

    /** Lets the user choose a PNG logo; `null` when cancelled or unsupported. */
    suspend fun chooseLogoFile(): String? = null
}
