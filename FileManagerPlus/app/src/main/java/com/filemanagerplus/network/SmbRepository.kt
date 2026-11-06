package com.filemanagerplus.network

/**
 * SMB integration boundary.
 *
 * The SMBJ dependency is included, but connection UI, credential storage,
 * share browsing and stream adapters are not implemented in this starter.
 * Credentials must never be stored in plaintext or logged.
 */
data class SmbConnection(
    val host: String,
    val share: String,
    val username: String,
    val domain: String? = null
)

interface SmbRepository {
    suspend fun testConnection(connection: SmbConnection): Result<Unit>
}
