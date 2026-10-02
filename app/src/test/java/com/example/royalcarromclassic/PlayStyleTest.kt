package com.example.royalcarromclassic

import android.app.Application
import com.example.royalcarromclassic.data.PlayStyle
import com.example.royalcarromclassic.ui.CarromViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayStyleTest {
    private class StyleAudio : MockAudioEngine() {
        var received: PlayStyle? = null
        override fun setStyle(style: PlayStyle) {
            received = style
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `play style is saved and restored into the sound engine`() {
        val repository = MockGameRepository()
        val first = StyleAudio()
        val vm = CarromViewModel(Application(), repository, first, MockHapticEngine(), null)
        assertEquals(PlayStyle.NORMAL, vm.gameState.value.playStyle)
        assertEquals(PlayStyle.NORMAL, first.received)

        vm.setPlayStyle(PlayStyle.MEME)
        assertEquals(PlayStyle.MEME, vm.gameState.value.playStyle)
        assertEquals(PlayStyle.MEME, first.received)

        val second = StyleAudio()
        val reopened = CarromViewModel(Application(), repository, second, MockHapticEngine(), null)
        assertEquals(PlayStyle.MEME, reopened.gameState.value.playStyle)
        assertEquals(PlayStyle.MEME, second.received)
    }
}
