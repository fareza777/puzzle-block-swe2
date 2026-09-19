package com.fareza.blokku.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.fareza.blokku.R
import com.fareza.blokku.data.Save

/**
 * SFX via SoundPool (low latency), music via looping MediaPlayer.
 * All sounds are synthesized in-repo — fully original, license-free.
 */
object Audio {

    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()
    private var music: MediaPlayer? = null
    private var init = false
    private var musicWasPlaying = false

    private val sfxMap = mapOf(
        "click" to R.raw.sfx_click,
        "pickup" to R.raw.sfx_pickup,
        "place" to R.raw.sfx_place,
        "invalid" to R.raw.sfx_invalid,
        "clear1" to R.raw.sfx_clear1,
        "clear2" to R.raw.sfx_clear2,
        "clear3" to R.raw.sfx_clear3,
        "combo" to R.raw.sfx_combo,
        "coin" to R.raw.sfx_coin,
        "reward" to R.raw.sfx_reward,
        "meter" to R.raw.sfx_meter,
        "undo" to R.raw.sfx_undo,
        "rotate" to R.raw.sfx_rotate,
        "bomb" to R.raw.sfx_bomb,
        "shuffle" to R.raw.sfx_shuffle,
        "spawn" to R.raw.sfx_spawn,
        "gameover" to R.raw.sfx_gameover,
        "win" to R.raw.sfx_win,
    )

    fun init(context: Context) {
        if (init) return
        init = true
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attrs).build()
        for ((k, res) in sfxMap) {
            ids[k] = pool!!.load(context, res, 1)
        }
        try {
            music = MediaPlayer.create(context, R.raw.music_loop)
            music?.isLooping = true
            music?.setVolume(0.35f, 0.35f)
        } catch (e: Exception) { music = null }
        sync()
    }

    fun play(name: String) {
        if (!Save.soundOn) return
        val id = ids[name] ?: return
        try { pool?.play(id, 1f, 1f, 1, 0, 1f) } catch (e: Exception) {}
    }

    fun sync() {
        if (Save.musicOn) startMusic() else stopMusic()
    }

    fun startMusic() {
        try { if (music?.isPlaying == false && Save.musicOn) music?.start() } catch (e: Exception) {}
    }

    fun stopMusic() {
        try { if (music?.isPlaying == true) music?.pause() } catch (e: Exception) {}
    }

    fun onAppPause() {
        musicWasPlaying = music?.isPlaying == true
        stopMusic()
    }

    fun onAppResume() {
        if (musicWasPlaying && Save.musicOn) startMusic()
    }
}
