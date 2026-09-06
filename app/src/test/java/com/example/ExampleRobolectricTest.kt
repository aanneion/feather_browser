package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Feather", appName)
  }

  @Test
  fun `goHome navigates to blank page`() {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.browser.BrowserViewModel(application)

    viewModel.navigateTo("https://example.com")
    assertEquals("https://example.com", viewModel.activeTabState.value?.url)

    viewModel.goHome()
    assertEquals("", viewModel.activeTabState.value?.url)
  }
}
