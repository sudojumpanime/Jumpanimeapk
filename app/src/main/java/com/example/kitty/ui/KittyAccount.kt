package com.example.kitty.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kitty.KittyCatalog
import com.example.kitty.theme.KittyAccent
import com.example.kitty.theme.KittyMuted
import com.example.kitty.theme.KittyOnAccent
import com.example.kitty.theme.KittyOutlineSoft
import kotlinx.coroutines.launch

/** Account tab: AniList profile, sign in/out, and links to sources, downloads and app settings. */
@Composable
fun KittyAccountScreen(
    catalog: KittyCatalog,
    onOpenLibrary: (String) -> Unit,
    onOpenSources: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val profile by catalog.account.profile.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { KittyPageHeader(title = "Account", subtitle = "AniList, sources and downloads") }
        item {
            val p = profile
            if (p != null) {
                KittyProfileCard(profile = p, onOpenList = onOpenLibrary)
            } else {
                KittyEmptyState(
                    title = "Not signed in",
                    body = "Sign in with AniList to sync your lists and write comments.",
                    actionLabel = "Sign in with AniList",
                    onAction = { catalog.account.startSignIn(context) },
                )
            }
        }
        if (profile != null) {
            item {
                Column {
                    Button(
                        onClick = { scope.launch { runCatching { catalog.account.refreshProfile(catalog.client) } } },
                        colors = ButtonDefaults.buttonColors(containerColor = KittyAccent, contentColor = KittyOnAccent),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Refresh profile", fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { catalog.account.clear() },
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, KittyOutlineSoft),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = KittyMuted),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Sign out", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        item {
            KittySectionHeading(title = "Stream sources", subtitle = "Anikoto · Gotaku · Ani.pm, plus extensions", padded = false)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onOpenSources,
                colors = ButtonDefaults.buttonColors(containerColor = KittyAccent, contentColor = KittyOnAccent),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Sources & extensions", fontWeight = FontWeight.Bold) }
        }
        item {
            KittySectionHeading(title = "On this device", padded = false)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onOpenDownloads,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, KittyOutlineSoft),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Downloads", fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenAppSettings,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, KittyOutlineSoft),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("App settings", fontWeight = FontWeight.SemiBold) }
        }
    }
}
