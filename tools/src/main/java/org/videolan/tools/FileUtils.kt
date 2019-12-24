package org.videolan.tools

import androidx.annotation.WorkerThread
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Size of the chunks that will be hashed in bytes (64 KB)
 */
private const val HASH_CHUNK_SIZE = 64 * 1024
object FileUtils {
    @WorkerThread
    fun computeHash(file: File): String? {
        val size = file.length()
        val chunkSizeForFile = HASH_CHUNK_SIZE.toLong().coerceAtMost(size)
        val head: Long
        val tail: Long
        var fis: FileInputStream? = null
        var fileChannel: FileChannel? = null
        try {
            fis = FileInputStream(file)
            fileChannel = fis.channel
            head = computeHashForChunk(fileChannel!!.map(FileChannel.MapMode.READ_ONLY, 0, chunkSizeForFile))

            //Alternate way to calculate tail hash for files over 4GB.
            val bb = ByteBuffer.allocateDirect(chunkSizeForFile.toInt())
            var position = (size - HASH_CHUNK_SIZE).coerceAtLeast(0)
            var read = fileChannel.read(bb, position)
            while (read > 0) {
                position += read.toLong()
                read = fileChannel.read(bb, position)
            }
            bb.flip()
            tail = computeHashForChunk(bb)
            return String.format("%016x", size + head + tail)
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        } finally {
            fileChannel?.close()
            fis?.close()
        }
    }

    @WorkerThread
    private fun computeHashForChunk(buffer: ByteBuffer): Long {
        val longBuffer = buffer.order(ByteOrder.LITTLE_ENDIAN).asLongBuffer()
        var hash: Long = 0
        while (longBuffer.hasRemaining()) hash += longBuffer.get()
        return hash
    }
}
