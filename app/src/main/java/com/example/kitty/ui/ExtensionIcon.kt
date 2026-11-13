package com.example.kitty.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.kitty.theme.KittyFaint
import com.example.kitty.theme.KittyOutlineSoft
import com.example.kitty.theme.KittySurface

/** Site brand icon for store / installed stream sources. */
@Composable
fun ExtensionIcon(
    iconUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(KittySurface)
            .border(1.dp, KittyOutlineSoft, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (!iconUrl.isNullOrBlank()) {
            AsyncImage(
                model = iconUrl,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(size)
                    .clip(shape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Outlined.Extension,
                contentDescription = contentDescription,
                tint = KittyFaint,
                modifier = Modifier.size(size * 0.48f),
            )
        }
    }
}
