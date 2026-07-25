package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ShortcutUtils {
    fun pinAnimeWithPoster(
        context: Context,
        scope: CoroutineScope,
        animeId: String,
        title: String,
        posterUrl: String?
    ) {
        scope.launch {
            var iconCompat: IconCompat? = null
            if (!posterUrl.isNullOrEmpty()) {
                try {
                    val loader = context.imageLoader
                    val request = ImageRequest.Builder(context)
                        .data(posterUrl)
                        .allowHardware(false) // Required for getting a drawing bitmap
                        .build()
                    val result = loader.execute(request)
                    if (result is SuccessResult) {
                        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                        if (bitmap != null) {
                            val size = minOf(bitmap.width, bitmap.height)
                            val x = (bitmap.width - size) / 2
                            val y = (bitmap.height - size) / 2
                            val squareBitmap = Bitmap.createBitmap(bitmap, x, y, size, size)
                            iconCompat = IconCompat.createWithBitmap(squareBitmap)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ShortcutUtils", "Failed to load poster for shortcut", e)
                }
            }

            if (iconCompat == null) {
                iconCompat = IconCompat.createWithResource(context, com.example.R.mipmap.ic_launcher)
            }

            withContext(Dispatchers.Main) {
                if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                    val intent = Intent(context, MainActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        putExtra("PINNED_CLICK", true)
                        putExtra("ANIME_ID", animeId)
                    }

                    val shortcutInfo = ShortcutInfoCompat.Builder(context, animeId)
                        .setShortLabel(title)
                        .setLongLabel(title)
                        .setIcon(iconCompat!!)
                        .setIntent(intent)
                        .build()

                    val success = ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
                    if (success) {
                        Toast.makeText(context, "Pinning '$title' to Home Screen...", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to request shortcut pin", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Pinning shortcuts is not supported by your launcher.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
