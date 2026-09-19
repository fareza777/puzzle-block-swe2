package com.fareza.blokku.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.fareza.blokku.R
import com.fareza.blokku.data.Save
import kotlin.math.min
import kotlin.random.Random

/**
 * SFX via SoundPool (low latency, per-play rate for pitch variety),
 * music via two looping MediaPlayers crossfaded per context (menu vs game).
 * All sounds are synthesized in-repo — fully original, license-free.
 */
object Audio {

    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()
    private var musicMenu: MediaPlayer? = null
    private var musicGame: MediaPlayer? = null
    private var init = false
    private var musicWasPlaying = false
    private var musicMode = 0 // 0=menu, 1=game
    private var menuVol = 0f
    private var gameVol = 0f
    private var appPaused = false
    private const val MUSIC_VOL = 0.34f

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
        "hint" to R.raw.sfx_hint,
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
            musicMenu = MediaPlayer.create(context, R.raw.music_loop)
            musicMenu?.isLooping = true
            musicMenu?.setVolume(0f, 0f)
            musicGame = MediaPlayer.create(context, R.raw.music_game)
            musicGame?.isLooping = true
            musicGame?.setVolume(0f, 0f)
        } catch (e: Exception) { musicMenu = null; musicGame = null }
        sync()
    }

    /** rate scales playback speed/pitch — small random offsets keep repeats fresh. */
    fun play(name: String, rate: Float = 1f) {
        if (!Save.soundOn) return
        val id = ids[name] ?: return
        try { pool?.play(id, 1f, 1f, 1, 0, rate) } catch (e: Exception) {}
    }

    /** Convenience: subtle random detune for frequently-triggered sfx. */
    fun playVaried(name: String) = play(name, 0.96f + Random.nextFloat() * 0.10f)

    fun sync() {
        if (Save.musicOn) startMusic() else stopMusic()
    }

    /** 0 = menu music, 1 = in-game music. Crossfades over ~0.5s in update(). */
    fun setMusicMode(mode: Int) {
        musicMode = mode
        if (Save.musicOn) startMusic()
    }

    private fun startMusic() {
        try {
            if (musicMode == 0) {
                if (musicMenu?.isPlaying == false) musicMenu?.start()
            } else {
                if (musicGame?.isPlaying == false) musicGame?.start()
            }
        } catch (e: Exception) {}
    }

    fun stopMusic() {
        try { if (musicMenu?.isPlaying == true) musicMenu?.pause() } catch (e: Exception) {}
        try { if (musicGame?.isPlaying == true) musicGame?.pause() } catch (e: Exception) {}
    }

    /** Called each frame from GameView — smooth volume crossfade between tracks. */
    fun update(dt: Float) {
        val targetMenu = if (Save.musicOn && !appPaused && musicMode == 0) MUSIC_VOL else 0f
        val targetGame = if (Save.musicOn && !appPaused && musicMode == 1) MUSIC_VOL else 0f
        menuVol += (targetMenu - menuVol) * min(1f, dt * 4f)
        gameVol += (targetGame - gameVol) * min(1f, dt * 4f)
        try {
            musicMenu?.setVolume(menuVol, menuVol)
            musicGame?.setVolume(gameVol, gameVol)
            if (Save.musicOn && !appPaused) {
                if (menuVol > 0.01f && musicMenu?.isPlaying == false) musicMenu?.start()
                if (gameVol > 0.01f && musicGame?.isPlaying == false) musicGame?.start()
                if (menuVol < 0.005f && musicMenu?.isPlaying == true) musicMenu?.pause()
                if (gameVol < 0.005f && musicGame?.isPlaying == true) musicGame?.pause()
            }
        } catch (e: Exception) {}
    }

    fun onAppPause() {
        appPaused = true
        musicWasPlaying = (musicMenu?.isPlaying == true) || (musicGame?.isPlaying == true)
        stopMusic()
        try { pool?.autoPause() } catch (e: Exception) {}
    }

    fun onAppResume() {
        appPaused = false
        try { pool?.autoResume() } catch (e: Exception) {}
        if (musicWasPlaying && Save.musicOn) startMusic()
    }
}
