package com.spotify.sdk.demo

import android.os.Handler
import android.widget.SeekBar

class TrackProgressBar(private val seekBar: SeekBar, private val seekStopListener: (Long) -> Unit) {
    private val handler: Handler

    private val seekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}

        override fun onStartTrackingTouch(seekBar: SeekBar) {}

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            seekStopListener.invoke(seekBar.progress.toLong())
        }
    }

    init {
        seekBar.setOnSeekBarChangeListener(seekBarChangeListener)
        handler = Handler()
    }

    private val seekUpdateRunnable = object : Runnable {
        override fun run() {
            val progress = seekBar.progress
            seekBar.progress = progress + LOOP_DURATION
            handler.postDelayed(this, LOOP_DURATION.toLong())
        }
    }

    fun setDuration(duration: Long) {
        seekBar.max = duration.toInt()
    }

    fun update(progress: Long) {
        seekBar.progress = progress.toInt()
    }

    fun pause() {
        handler.removeCallbacks(seekUpdateRunnable)
    }

    fun unpause() {
        handler.removeCallbacks(seekUpdateRunnable)
        handler.postDelayed(seekUpdateRunnable, LOOP_DURATION.toLong())
    }

    companion object {
        private const val LOOP_DURATION = 500
    }
}