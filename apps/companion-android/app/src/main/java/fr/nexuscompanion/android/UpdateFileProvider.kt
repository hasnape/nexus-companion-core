package fr.nexuscompanion.android

import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider

/** Only the dedicated APK cache is exposed, and granted recipients can never write it. */
class UpdateFileProvider : FileProvider(R.xml.update_paths) {
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Update files are read-only")
        return super.openFile(uri, mode) ?: throw java.io.FileNotFoundException("Update snapshot unavailable")
    }
}
