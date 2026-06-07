package org.devil.hytranslator.service

import org.junit.Assert.assertSame
import org.junit.Test

class DownloadStartPolicyTest {
    @Test
    fun choose_beforeAndroid14_usesForegroundServiceFallback() {
        assertSame(
            DownloadTransport.ForegroundService,
            DownloadStartPolicy.choose(apiLevel = 33),
        )
    }

    @Test
    fun choose_onAndroid14AndLater_usesUserInitiatedDataTransferJob() {
        assertSame(
            DownloadTransport.UserInitiatedDataTransferJob,
            DownloadStartPolicy.choose(apiLevel = 34),
        )
        assertSame(
            DownloadTransport.UserInitiatedDataTransferJob,
            DownloadStartPolicy.choose(apiLevel = 37),
        )
    }
}
