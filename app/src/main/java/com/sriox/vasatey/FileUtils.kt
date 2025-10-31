package com.sriox.vasatey

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object FileUtils {
    fun extractAsset(context: Context, assetFileName: String): String {
        val assetManager = context.assets
        val outFile = File(context.filesDir, assetFileName)

        assetManager.open(assetFileName).use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
        return outFile.absolutePath
    }
}
