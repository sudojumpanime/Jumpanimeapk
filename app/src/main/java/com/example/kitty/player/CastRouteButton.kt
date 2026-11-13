package com.example.kitty.player

import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.example.R

/**
 * System Cast picker (MediaRouteButton). The Cast SDK hides the button when
 * no receivers / Play Services are available.
 */
@Composable
fun CastRouteButton(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            val themed = ContextThemeWrapper(ctx, R.style.Theme_JumpAnime_Cast)
            MediaRouteButton(themed).also { button ->
                button.contentDescription = "Cast"
                runCatching {
                    CastButtonFactory.setUpMediaRouteButton(themed.applicationContext, button)
                }
            }
        },
        modifier = modifier.size(40.dp),
    )
}
