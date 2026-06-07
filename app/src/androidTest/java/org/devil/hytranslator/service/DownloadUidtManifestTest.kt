package org.devil.hytranslator.service

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadUidtManifestTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val packageManager = context.packageManager

    @Test
    fun manifest_declaresUserInitiatedJobsPermissionAndJobServices() {
        val packageInfo = packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )

        assertTrue(
            packageInfo.requestedPermissions.orEmpty()
                .contains(Manifest.permission.RUN_USER_INITIATED_JOBS),
        )
        assertJobService<ModelDownloadUidtJobService>()
        assertJobService<AiAssetDownloadUidtJobService>()
    }

    private inline fun <reified T> assertJobService() {
        val serviceInfo = packageManager.getServiceInfo(
            ComponentName(context, T::class.java),
            PackageManager.ComponentInfoFlags.of(0),
        )

        assertEquals(BIND_JOB_SERVICE_PERMISSION, serviceInfo.permission)
        assertEquals(false, serviceInfo.exported)
    }

    private companion object {
        const val BIND_JOB_SERVICE_PERMISSION = "android.permission.BIND_JOB_SERVICE"
    }
}
