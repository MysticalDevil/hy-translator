package org.devil.hytranslator.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.devil.hytranslator.domain.model.AiAsset
import org.devil.hytranslator.domain.model.AiAssetState
import org.devil.hytranslator.domain.model.DownloadProgress

interface AiAssetRepository {
    fun state(asset: AiAsset): StateFlow<AiAssetState>

    fun refresh(asset: AiAsset)

    fun download(asset: AiAsset): Flow<DownloadProgress>

    fun isReady(asset: AiAsset): Boolean

    fun localPath(asset: AiAsset): String
}
