package com.clockweather.app.presentation.widget.common

import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class WidgetSetIconTest {

    private val context: Context = mockk()
    private val views: RemoteViews = mockk(relaxed = true)
    private val viewId = 42
    private val drawableResId = 99

    @Test
    fun `renders bitmap and calls setImageViewBitmap when renderIcon succeeds`() {
        val bitmap: Bitmap = mockk()

        setWidgetIcon(views, viewId, context, drawableResId) { _, _ -> bitmap }

        verify { views.setImageViewBitmap(viewId, bitmap) }
        verify(exactly = 0) { views.setImageViewResource(any(), any()) }
    }

    @Test
    fun `does not call setImageViewResource when renderIcon returns null`() {
        // On MIUI Android 10 the launcher's RemoteViews inflater cannot parse
        // vector drawables with aapt:attr gradients. Passing the resource ID as a
        // fallback re-triggers the same crash. An empty icon is safer than a dead widget.
        setWidgetIcon(views, viewId, context, drawableResId) { _, _ -> null }

        verify(exactly = 0) { views.setImageViewResource(any(), any()) }
        verify(exactly = 0) { views.setImageViewBitmap(any(), any()) }
    }
}
