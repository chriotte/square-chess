package com.dataespresso.squarechess

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Square
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

enum class MoveSound { MOVE, CAPTURE, CHECK }

/** Classifies the last move of [position]; null when there is no move. */
fun lastMoveSound(position: ChessPosition): MoveSound? {
    val last = position.moves.lastOrNull() ?: return null
    if (position.board.isKingAttacked) return MoveSound.CHECK
    val before = ChessPosition(position.initialFen, position.moves.dropLast(1)).board
    val from = Square.valueOf(last.substring(0, 2).uppercase())
    val to = Square.valueOf(last.substring(2, 4).uppercase())
    val mover = before.getPiece(from)
    val enPassant = mover.pieceType == PieceType.PAWN && from.file != to.file && before.getPiece(to) == Piece.NONE
    return if (before.getPiece(to) != Piece.NONE || enPassant) MoveSound.CAPTURE else MoveSound.MOVE
}

/**
 * Short synthesised sounds, so the app needs no audio assets. Each sound is a
 * pre-rendered static AudioTrack; failures are logged and never affect play.
 */
class MoveSounds {
    private val rate = 22050
    private val tracks = runCatching {
        mapOf(
            MoveSound.MOVE to track(knock(620.0, 0.55)),
            MoveSound.CAPTURE to track(knock(480.0, 0.8) + ShortArray(rate * 35 / 1000) + knock(560.0, 0.6)),
            MoveSound.CHECK to track(tone(880.0, 0.07, 0.35) + tone(1175.0, 0.09, 0.35))
        )
    }.onFailure { Log.w("SquareChess", "Move sounds unavailable", it) }.getOrDefault(emptyMap())

    fun play(sound: MoveSound) {
        val track = tracks[sound] ?: return
        runCatching { track.stop(); track.reloadStaticData(); track.play() }
    }
    fun release() = tracks.values.forEach { runCatching { it.release() } }

    // A wooden "knock": a decaying low tone plus a very short noise attack.
    private fun knock(frequency: Double, gain: Double): ShortArray {
        val random = Random(7)
        return ShortArray(rate * 70 / 1000) { i ->
            val t = i.toDouble() / rate
            val body = sin(2 * PI * frequency * t) * exp(-t / 0.012)
            val attack = (random.nextDouble() * 2 - 1) * exp(-t / 0.002)
            ((body * 0.8 + attack * 0.4) * gain * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
    }
    private fun tone(frequency: Double, seconds: Double, gain: Double): ShortArray {
        val n = (rate * seconds).toInt()
        return ShortArray(n) { i ->
            val t = i.toDouble() / rate
            val envelope = minOf(1.0, i / (rate * 0.005)) * minOf(1.0, (n - i) / (rate * 0.02))
            (sin(2 * PI * frequency * t) * envelope * gain * Short.MAX_VALUE).toInt().toShort()
        }
    }
    private fun track(pcm: ShortArray) = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .setAudioFormat(AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(rate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setBufferSizeInBytes(pcm.size * 2)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build().apply { write(pcm, 0, pcm.size) }
}
