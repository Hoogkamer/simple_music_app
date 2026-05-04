package com.michael.simplemusic.scanner

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

data class AudioFile(
    val uri: Uri,
    val displayName: String,
    val relativePath: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null
)

class FolderScanner(private val context: Context) {

    fun scanFolder(folderUri: Uri): List<AudioFile> {
        val rootDocument = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
        val files = mutableListOf<AudioFile>()
        scanRecursive(rootDocument, "", files)
        return files.sortedBy { it.relativePath.lowercase() }
    }

    private fun scanRecursive(
        directory: DocumentFile,
        currentPath: String,
        results: MutableList<AudioFile>
    ) {
        val list = directory.listFiles()
        val retriever = android.media.MediaMetadataRetriever()
        
        list.forEach { file ->
            val name = file.name ?: return@forEach

            if (file.isDirectory) {
                val subPath = if (currentPath.isEmpty()) name else "$currentPath/$name"
                scanRecursive(file, subPath, results)
            } else if (file.isFile && name.endsWith(".mp3", ignoreCase = true)) {
                val relativePath = if (currentPath.isEmpty()) name else "$currentPath/$name"
                
                var title: String? = null
                var artist: String? = null
                var album: String? = null
                
                try {
                    context.contentResolver.openFileDescriptor(file.uri, "r")?.use { pfd ->
                        retriever.setDataSource(pfd.fileDescriptor)
                        title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                        artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    }
                } catch (e: Exception) {
                    // Ignore errors, use filename
                }

                results.add(
                    AudioFile(
                        uri = file.uri,
                        displayName = name,
                        relativePath = relativePath,
                        title = title,
                        artist = artist,
                        album = album
                    )
                )
            }
        }
        try { retriever.release() } catch (e: Exception) {}
    }
}
