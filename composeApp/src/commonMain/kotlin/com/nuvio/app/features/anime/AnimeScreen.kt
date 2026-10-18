package com.nuvio.app.features.anime

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import nuvio.composeapp.generated.resources.action_save
import nuvio.composeapp.generated.resources.anime_mal_cleared
import nuvio.composeapp.generated.resources.anime_mal_client_id_placeholder
import nuvio.composeapp.generated.resources.anime_mal_invalid
import nuvio.composeapp.generated.resources.anime_mal_network_error
import nuvio.composeapp.generated.resources.anime_mal_open_api_config
import nuvio.composeapp.generated.resources.anime_mal_saved
import nuvio.composeapp.generated.resources.anime_mal_settings_body
import nuvio.composeapp.generated.resources.anime_mal_settings_title
import nuvio.composeapp.generated.resources.anime_row_airing
import nuvio.composeapp.generated.resources.anime_settings
import nuvio.composeapp.generated.resources.anime_source_mal
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioSurfaceCard
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.addons.AddAddonResult
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.HomeCatalogRowSection
import com.nuvio.app.features.home.components.HomeEmptyStateCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_install
import nuvio.composeapp.generated.resources.action_retry
import nuvio.composeapp.generated.resources.anime_kitsu_addon_body
import nuvio.composeapp.generated.resources.anime_kitsu_addon_installed
import nuvio.composeapp.generated.resources.anime_kitsu_addon_installing
import nuvio.composeapp.generated.resources.anime_kitsu_addon_title
import nuvio.composeapp.generated.resources.anime_load_failed
import nuvio.composeapp.generated.resources.anime_row_movies
import nuvio.composeapp.generated.resources.anime_row_popular
import nuvio.composeapp.generated.resources.anime_row_season
import nuvio.composeapp.generated.resources.anime_row_top_rated
import nuvio.composeapp.generated.resources.anime_row_upcoming
import nuvio.composeapp.generated.resources.anime_source_kitsu
import nuvio.composeapp.generated.resources.anime_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun AnimeScreen(
    modifier: Modifier = Modifier,
    scrollToTopRequests: Flow<Unit> = emptyFlow(),
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
) {
    val uiState by remember {
        AnimeCatalogRepository.ensureLoaded()
        AnimeCatalogRepository.uiState
    }.collectAsStateWithLifecycle()
    val addonsState by AddonRepository.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(addonsState.addons) {
        AnimeCatalogRepository.refreshKitsuAddonState()
    }

    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopRequests) {
        scrollToTopRequests.collect { listState.animateScrollToItem(0) }
    }
    val coroutineScope = rememberCoroutineScope()
    val sourceName = stringResource(
        if (uiState.source == AnimeSource.MY_ANIME_LIST) Res.string.anime_source_mal else Res.string.anime_source_kitsu,
    )
    var showMalSettings by rememberSaveable { mutableStateOf(false) }

    NuvioScreen(
        modifier = modifier,
        horizontalPadding = 0.dp,
        listState = listState,
    ) {
        stickyHeader {
            NuvioScreenHeader(
                title = stringResource(Res.string.anime_title),
                modifier = Modifier.padding(horizontal = 16.dp),
                actions = {
                    IconButton(onClick = { showMalSettings = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = stringResource(Res.string.anime_settings),
                        )
                    }
                },
            )
        }

        if (!uiState.kitsuAddonInstalled) {
            item(key = "kitsu-addon") {
                KitsuAddonCard(
                    installing = uiState.isInstallingKitsuAddon,
                    onInstall = {
                        coroutineScope.launch {
                            when (val result = AnimeCatalogRepository.installKitsuAddon()) {
                                is AddAddonResult.Success ->
                                    NuvioToastController.show(getString(Res.string.anime_kitsu_addon_installed))
                                is AddAddonResult.Error -> NuvioToastController.show(result.message)
                            }
                        }
                    },
                )
            }
        }

        when {
            uiState.rows.isNotEmpty() -> items(uiState.rows, key = { it.kind.name }) { row ->
                val title = stringResource(row.kind.titleResource())
                HomeCatalogRowSection(
                    section = HomeCatalogSection(
                        key = "anime-${row.kind.name}",
                        title = title,
                        subtitle = sourceName,
                        addonName = sourceName,
                        target = CatalogTarget.Addon(
                            manifestUrl = AnimeCatalogRepository.KITSU_ADDON_MANIFEST_URL,
                            contentType = "anime",
                            catalogId = "nexo-anime-${row.kind.name.lowercase()}",
                        ),
                        items = row.items,
                    ),
                    onPosterClick = onPosterClick,
                    onPosterLongClick = onPosterLongClick,
                )
            }

            uiState.isLoading -> item(key = "loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    NuvioLoadingIndicator()
                }
            }

            uiState.hasError -> item(key = "error") {
                HomeEmptyStateCard(
                    title = stringResource(Res.string.anime_title),
                    message = stringResource(Res.string.anime_load_failed),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    actionLabel = stringResource(Res.string.action_retry),
                    onActionClick = AnimeCatalogRepository::reload,
                )
            }
        }
    }

    if (showMalSettings) {
        MalSettingsSheet(
            initialClientId = uiState.malClientId,
            onDismiss = { showMalSettings = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MalSettingsSheet(
    initialClientId: String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    var clientId by rememberSaveable { mutableStateOf(initialClientId) }
    var saving by remember { mutableStateOf(false) }

    NuvioModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(Res.string.anime_mal_settings_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(Res.string.anime_mal_settings_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { uriHandler.openUri(AnimeCatalogRepository.MAL_API_CONFIG_URL) }) {
                Text(stringResource(Res.string.anime_mal_open_api_config))
            }
            NuvioInputField(
                value = clientId,
                onValueChange = { clientId = it },
                placeholder = stringResource(Res.string.anime_mal_client_id_placeholder),
            )
            Spacer(modifier = Modifier.height(16.dp))
            NuvioPrimaryButton(
                text = stringResource(Res.string.action_save),
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                onClick = {
                    saving = true
                    coroutineScope.launch {
                        val message = when (AnimeCatalogRepository.updateMalClientId(clientId)) {
                            MalClientIdResult.Saved -> Res.string.anime_mal_saved
                            MalClientIdResult.Cleared -> Res.string.anime_mal_cleared
                            MalClientIdResult.Invalid -> Res.string.anime_mal_invalid
                            MalClientIdResult.NetworkError -> Res.string.anime_mal_network_error
                        }
                        saving = false
                        NuvioToastController.show(getString(message))
                        if (message == Res.string.anime_mal_saved || message == Res.string.anime_mal_cleared) {
                            onDismiss()
                        }
                    }
                },
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun KitsuAddonCard(
    installing: Boolean,
    onInstall: () -> Unit,
) {
    NuvioSurfaceCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(Res.string.anime_kitsu_addon_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.anime_kitsu_addon_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        NuvioPrimaryButton(
            text = stringResource(
                if (installing) Res.string.anime_kitsu_addon_installing else Res.string.action_install,
            ),
            enabled = !installing,
            onClick = onInstall,
        )
    }
}

private fun AnimeRowKind.titleResource(): StringResource = when (this) {
    AnimeRowKind.SEASON -> Res.string.anime_row_season
    AnimeRowKind.AIRING -> Res.string.anime_row_airing
    AnimeRowKind.POPULAR -> Res.string.anime_row_popular
    AnimeRowKind.TOP_RATED -> Res.string.anime_row_top_rated
    AnimeRowKind.MOVIES -> Res.string.anime_row_movies
    AnimeRowKind.UPCOMING -> Res.string.anime_row_upcoming
}
