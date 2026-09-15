package com.helium4.localscreenshare

import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Wire format for the receiver -> sender control back-channel.
 *
 * The receiver (controller) writes commands; the sender reads them and replays
 * them through [ControlAccessibilityService]. Coordinates are normalized to the
 * sender's screen (0f..1f) so no resolution needs to be transmitted — the sender
 * multiplies by its real pixel dimensions.
 *
 * Layout (big-endian, via Data*Stream):
 *   TAP / LONG_PRESS : byte op, float nx, float ny
 *   SWIPE            : byte op, float nx1, ny1, nx2, ny2, int durationMs
 *   GLOBAL           : byte op, byte action
 *   TEXT             : byte op, int len, len bytes UTF-8
 */
object ControlProtocol {
    const val OP_TAP: Byte = 1
    const val OP_LONG_PRESS: Byte = 2
    const val OP_SWIPE: Byte = 3
    const val OP_GLOBAL: Byte = 4
    const val OP_TEXT: Byte = 5

    // Global action ids
    const val GLOBAL_BACK: Byte = 0
    const val GLOBAL_HOME: Byte = 1
    const val GLOBAL_RECENTS: Byte = 2

    /** A single decoded control command. */
    sealed class Command {
        data class Tap(val nx: Float, val ny: Float, val longPress: Boolean) : Command()
        data class Swipe(
            val nx1: Float, val ny1: Float,
            val nx2: Float, val ny2: Float,
            val durationMs: Int
        ) : Command()
        data class Global(val action: Byte) : Command()
        data class Text(val text: String) : Command()
    }

    // ---- Writers (receiver side) ----

    fun writeTap(out: DataOutputStream, nx: Float, ny: Float, longPress: Boolean) {
        out.writeByte((if (longPress) OP_LONG_PRESS else OP_TAP).toInt())
        out.writeFloat(nx)
        out.writeFloat(ny)
        out.flush()
    }

    fun writeSwipe(
        out: DataOutputStream,
        nx1: Float, ny1: Float, nx2: Float, ny2: Float, durationMs: Int
    ) {
        out.writeByte(OP_SWIPE.toInt())
        out.writeFloat(nx1)
        out.writeFloat(ny1)
        out.writeFloat(nx2)
        out.writeFloat(ny2)
        out.writeInt(durationMs)
        out.flush()
    }

    fun writeGlobal(out: DataOutputStream, action: Byte) {
        out.writeByte(OP_GLOBAL.toInt())
        out.writeByte(action.toInt())
        out.flush()
    }

    fun writeText(out: DataOutputStream, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        out.writeByte(OP_TEXT.toInt())
        out.writeInt(bytes.size)
        out.write(bytes)
        out.flush()
    }

    // ---- Reader (sender side) ----

    /** Blocks reading one command. Throws on stream end/IO error (caller stops the loop). */
    fun readCommand(input: DataInputStream): Command {
        return when (val op = input.readByte()) {
            OP_TAP, OP_LONG_PRESS -> {
                val nx = input.readFloat()
                val ny = input.readFloat()
                Command.Tap(nx, ny, longPress = op == OP_LONG_PRESS)
            }
            OP_SWIPE -> {
                val nx1 = input.readFloat()
                val ny1 = input.readFloat()
                val nx2 = input.readFloat()
                val ny2 = input.readFloat()
                val dur = input.readInt()
                Command.Swipe(nx1, ny1, nx2, ny2, dur)
            }
            OP_GLOBAL -> Command.Global(input.readByte())
            OP_TEXT -> {
                val len = input.readInt()
                require(len in 0..8192) { "Invalid text length: $len" }
                val bytes = ByteArray(len)
                input.readFully(bytes)
                Command.Text(String(bytes, Charsets.UTF_8))
            }
            else -> throw IllegalStateException("Unknown control opcode: $op")
        }
    }
}
