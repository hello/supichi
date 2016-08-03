package is.hello.speech.core.text2speech;

import com.google.common.base.Optional;
import net.sourceforge.lame.lowlevel.LameEncoder;
import net.sourceforge.lame.mp3.Lame;
import net.sourceforge.lame.mp3.MPEGMode;
import org.apache.commons.compress.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by ksg on 8/2/16
 */
public class AudioUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioUtils.class);

    // for adding WAV headers to watson stream
    private static final int WAVE_HEADER_SIZE = 8;      // The WAVE meta-data header size.
    private static final int WAVE_SIZE_POS = 4;         // The WAVE meta-data size position.
    private static final int WAVE_METADATA_POS = 74;    // The WAVE meta-data position in bytes.

    private static final int MP3_BITRATE = 44;  // TODO: make this configurable
    private static final boolean USE_VBR = false;


    static class AudioBytes {
        final byte [] bytes;
        final int contentSize;
        final Optional<AudioFormat> format;

        private AudioBytes(byte[] bytes, int contentSize, javax.sound.sampled.AudioFormat format) {
            this.bytes = bytes;
            this.contentSize = contentSize;
            this.format = Optional.fromNullable(format);
        }

        static AudioBytes empty() {
            return new AudioBytes(new byte[0], 0, null);
        }
    }

    /**
     * Note: from IBM WaveUtils
     * Re-writes the data size in the header(bytes 4-8) of the WAVE(.wav) input stream.<br>
     * It needs to be read in order to calculate the size.
     *
     * @param inputStream the input stream
     */
    static AudioBytes convertStreamToBytesWithWavHeader(final InputStream inputStream) {
        byte [] audioBytes;
        try {
            audioBytes = IOUtils.toByteArray(inputStream);
        } catch (IOException e) {
            LOGGER.warn("error=fail-to-convert-inputstream msg={}", e.getMessage());
            return new AudioBytes(null, 0, null);
        }

        final int fileSize = audioBytes.length - WAVE_HEADER_SIZE;

        writeInt(fileSize, audioBytes, WAVE_SIZE_POS);
        writeInt(fileSize - WAVE_HEADER_SIZE, audioBytes, WAVE_METADATA_POS);

        return new AudioBytes(audioBytes, audioBytes.length, null);
    }

    /**
     * Down-sample audio to a different sampling rate
     * @param bytes raw audio bytes
     * @param targetSampleRate target sample rate
     * @return data in raw bytes
     */
    static AudioBytes downSampleAudio(final byte[] bytes, final float targetSampleRate) {
        AudioInputStream sourceStream;
        try {
            final ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
            sourceStream = AudioSystem.getAudioInputStream(inputStream);
        } catch (UnsupportedAudioFileException e) {
            LOGGER.error("error=unsupported-audio-file msg={}", e.getMessage());
            return AudioBytes.empty();
        } catch (IOException e) {
            LOGGER.error("error=fail-to-convert-bytes-to-audiostream reason=IO-exception msg={}", e.getMessage());
            return AudioBytes.empty();
        }

        final javax.sound.sampled.AudioFormat sourceFormat = sourceStream.getFormat();
        final javax.sound.sampled.AudioFormat targetFormat = new javax.sound.sampled.AudioFormat(
                sourceFormat.getEncoding(),
                targetSampleRate,
                sourceFormat.getSampleSizeInBits(),
                sourceFormat.getChannels(),
                sourceFormat.getFrameSize(),
                targetSampleRate,
                sourceFormat.isBigEndian()
        );

        final AudioInputStream convertedStream = AudioSystem.getAudioInputStream(targetFormat, sourceStream);
        try {
            final byte[] convertedBytes = IOUtils.toByteArray(convertedStream);
            return new AudioBytes(convertedBytes, convertedBytes.length, convertedStream.getFormat());
        } catch (IOException e) {
            LOGGER.error("error=fail-to-convert-stream-to-bytes");
            return AudioBytes.empty();
        }
    }


    /**
     * Writes an number into an array using 4 bytes
     *
     * @param value the number to write
     * @param array the byte array
     * @param offset the offset
     */
    private static void writeInt(int value, byte[] array, int offset) {
        for (int i = 0; i < 4; i++) {
            array[offset + i] = (byte) (value >>> (8 * i));
        }
    }

    static byte[] encodePcmToMp3(final AudioBytes pcm) {

        if (!pcm.format.isPresent()) {
            LOGGER.error("error=no-pcm-format-found-for-mp3-conversion");
            return new byte[0];
        }

        final javax.sound.sampled.AudioFormat sourceFormat = pcm.format.get();
        final javax.sound.sampled.AudioFormat inputFormat = new javax.sound.sampled.AudioFormat(
                javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                sourceFormat.getSampleRate(),
                sourceFormat.getSampleSizeInBits(),
                sourceFormat.getChannels(),
                sourceFormat.getFrameSize(),
                sourceFormat.getFrameRate(),
                sourceFormat.isBigEndian()
        );

        LameEncoder encoder = new LameEncoder(inputFormat, MP3_BITRATE, MPEGMode.MONO, Lame.QUALITY_HIGHEST, USE_VBR);

        ByteArrayOutputStream mp3 = new ByteArrayOutputStream();
        final byte[] buffer = new byte[encoder.getPCMBufferSize()];
        int bytesToTransfer = Math.min(buffer.length, pcm.contentSize);
        int bytesWritten;
        int currentPcmPosition = 0;
        while (0 < (bytesWritten = encoder.encodeBuffer(pcm.bytes, currentPcmPosition, bytesToTransfer, buffer))) {
            currentPcmPosition += bytesToTransfer;
            bytesToTransfer = Math.min(buffer.length, pcm.contentSize - currentPcmPosition);

            mp3.write(buffer, 0, bytesWritten);
        }

        encoder.close();
        return mp3.toByteArray();
    }
}
