package com.example.farmroutesaathi.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

class VoiceRecorder(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    @Volatile
    private var isRecording = false
    private var recordThread: Thread? = null
    private var activeFile: File? = null

    val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    fun isCurrentlyRecording(): Boolean = isRecording

    @SuppressLint("MissingPermission")
    @Synchronized
    fun startRecording(): File {
        if (isRecording) {
            stopRecording()
        }

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = maxOf(minBufferSize, 4096)

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )
        audioRecord = record

        val outputFile = File(context.cacheDir, "sarvam_record_${System.currentTimeMillis()}.wav")
        activeFile = outputFile
        isRecording = true

        record.startRecording()

        recordThread = Thread({
            var fos: FileOutputStream? = null
            var totalAudioBytes = 0L
            try {
                fos = FileOutputStream(outputFile)
                // Write 44-byte placeholder header
                fos.write(ByteArray(44))

                val buffer = ByteArray(bufferSize)
                while (isRecording) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        fos.write(buffer, 0, read)
                        totalAudioBytes += read
                    }
                }
                fos.flush()
            } catch (e: Exception) {
                // handle error
            } finally {
                try {
                    fos?.close()
                } catch (e: Exception) {}
            }

            // Write authentic WAV header
            try {
                if (outputFile.exists() && totalAudioBytes > 0) {
                    writeWavHeader(outputFile, totalAudioBytes)
                }
            } catch (e: Exception) {}
        }, "VoiceRecordThread")

        recordThread?.start()
        return outputFile
    }

    @Synchronized
    fun stopRecording(): File? {
        if (!isRecording) return activeFile
        isRecording = false

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // ignore
        }
        audioRecord = null

        try {
            recordThread?.join(1500)
        } catch (e: Exception) {
            // ignore
        }
        recordThread = null

        return activeFile
    }

    private fun writeWavHeader(file: File, totalAudioBytes: Long) {
        val totalDataLen = totalAudioBytes + 36
        val channels = 1
        val byteRate = sampleRate * channels * 2

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte() // RIFF
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte() // WAVE
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // 'fmt '
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // Subchunk1Size (16 for PCM)
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // AudioFormat 1 = PCM
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * 2).toByte() // BlockAlign
        header[33] = 0
        header[34] = 16 // BitsPerSample
        header[35] = 0
        header[36] = 'd'.code.toByte() // 'data'
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioBytes and 0xff).toByte()
        header[41] = ((totalAudioBytes shr 8) and 0xff).toByte()
        header[42] = ((totalAudioBytes shr 16) and 0xff).toByte()
        header[43] = ((totalAudioBytes shr 24) and 0xff).toByte()

        val raf = RandomAccessFile(file, "rw")
        raf.seek(0)
        raf.write(header)
        raf.close()
    }
}
