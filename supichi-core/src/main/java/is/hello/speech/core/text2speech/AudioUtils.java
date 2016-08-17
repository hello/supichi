package is.hello.speech.core.text2speech;

import com.google.common.base.Optional;
import davaguine.jeq.core.EqualizerInputStream;
import net.sourceforge.lame.lowlevel.LameEncoder;
import net.sourceforge.lame.mp3.Lame;
import net.sourceforge.lame.mp3.MPEGMode;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.http.util.ByteArrayBuffer;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ksg on 8/2/16
 */
public class AudioUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioUtils.class);

    public static final int WAVE_HEADER_SIZE = 44;

    // for adding WAV headers to watson stream
    private static final int WATSON_WAVE_HEADER_SIZE = 8;      // The WAVE meta-data header size.
    private static final int WATSON_WAVE_SIZE_POS = 4;         // The WAVE meta-data size position.
    private static final int WATSON_WAVE_METADATA_POS = 74;    // The WAVE meta-data position in bytes.

    private static final int MP3_BITRATE = 44;  // TODO: make this configurable
    private static final boolean USE_VBR = false;

    // Equalization settings
    private static final int NUM_CHANNELS = 1;
    private static final int NUM_EQ_BANDS = 10;
    private static final boolean SIGNED_DATA_TRUE = true;
    private static final boolean BIG_ENDIAN_FALSE = false;

    private static final List<Float> EQUALIZED_VALUES = new ArrayList<Float>(NUM_EQ_BANDS) {{
        add(0, 10.0f);  // 32
        add(1, 10.0f);  // 64
        add(2, 8.15f);  // 125
        add(3, 6.98f);  // 250
        add(4, 2.03f);  // 500
        add(5, 1.05f);  // 1k
        add(6, 5.60f);  // 2k
        add(7, 12.9f);  // 4k
        add(8, 16.9f);  // 8k
        add(9, 16.9f);  // 16k
    }};

    public static class AudioBytes {
        public final byte [] bytes;
        public final int contentSize;
        public final Optional<AudioFormat> format;

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
    public static AudioBytes convertStreamToBytesWithWavHeader(final InputStream inputStream) {
        byte [] audioBytes;
        try {
            audioBytes = IOUtils.toByteArray(inputStream);
        } catch (IOException e) {
            LOGGER.warn("error=fail-to-convert-inputstream msg={}", e.getMessage());
            return AudioBytes.empty();
        }

        final int fileSize = audioBytes.length - WATSON_WAVE_HEADER_SIZE;

        writeInt(fileSize, audioBytes, WATSON_WAVE_SIZE_POS);
        writeInt(fileSize - WATSON_WAVE_HEADER_SIZE, audioBytes, WATSON_WAVE_METADATA_POS);

        return new AudioBytes(audioBytes, audioBytes.length, null);
    }

    /**
     * Down-sample audio to a different sampling rate
     * @param bytes raw audio bytes
     * @param targetSampleRate target sample rate
     * @return data in raw bytes
     */
    public static AudioBytes downSampleAudio(final byte[] bytes, Optional<javax.sound.sampled.AudioFormat> optionalSourceFormat, final float targetSampleRate) {

        final Optional<AudioInputStream> optionalSourceStream = getAudioStream(bytes, optionalSourceFormat);
        if (!optionalSourceStream.isPresent()) {
            return AudioBytes.empty();
        }

        final AudioInputStream sourceStream = optionalSourceStream.get();
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
            return new AudioBytes(convertedBytes, convertedBytes.length, targetFormat);
        } catch (IOException e) {
            LOGGER.error("error=fail-to-convert-stream-to-bytes");
            return AudioBytes.empty();
        }
    }

    public static AudioBytes equalize(final byte[] bytes,  Optional<javax.sound.sampled.AudioFormat> optionalSourceFormat) {

        final Optional<AudioInputStream> optionalSourceStream = getAudioStream(bytes, optionalSourceFormat);
        if (!optionalSourceStream.isPresent()) {
            return AudioBytes.empty();
        }

        final AudioInputStream sourceStream = optionalSourceStream.get();
        final javax.sound.sampled.AudioFormat sourceFormat = sourceStream.getFormat();

        final EqualizerInputStream equalizer = new EqualizerInputStream(sourceStream,
                sourceFormat.getSampleRate(),
                NUM_CHANNELS,
                SIGNED_DATA_TRUE,
                sourceFormat.getSampleSizeInBits(),
                BIG_ENDIAN_FALSE,
                NUM_EQ_BANDS);

        for (int band = 0; band < NUM_EQ_BANDS; band++) {
            equalizer.getControls().setBandDbValue(band, 0, EQUALIZED_VALUES.get(band));
        }

        // grab output of equalized audio
        final int bufferSize = bytes.length;
        final ByteArrayBuffer output = new ByteArrayBuffer(bufferSize);
        try {
            byte[] buffer = new byte[bufferSize];
            int bytesRead = 0;
            while (bytesRead >= 0) {
                bytesRead = equalizer.read(buffer, 0, buffer.length);
                if (bytesRead >= 0) {
                    output.append(buffer, 0, bytesRead);
                }
            }
            LOGGER.debug("action=equalize-audio-success bytes_converted={} original_size={}", output.length(), bufferSize);
        } catch (IOException e) {
            LOGGER.error("error=fail-to-equalize error_msg={}", e.getMessage());
            return AudioBytes.empty();
        }

        return new AudioBytes(output.buffer(), output.length(), sourceFormat);
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

    private static Optional<AudioInputStream> getAudioStream(final byte[] bytes, Optional<javax.sound.sampled.AudioFormat> optionalSourceFormat) {

        final ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        final AudioInputStream sourceStream;
        if (optionalSourceFormat.isPresent()) {
            sourceStream = new AudioInputStream(inputStream, optionalSourceFormat.get(), bytes.length);
        } else {
            try {
                sourceStream = AudioSystem.getAudioInputStream(inputStream);
            } catch (UnsupportedAudioFileException e) {
                LOGGER.error("error=fail-to-get-audio-stream reason=unsupported-audio-file msg={}", e.getMessage());
                return Optional.absent();
            } catch (IOException e) {
                LOGGER.error("error=fail-to-convert-bytes-to-audio-stream reason=IO-exception msg={}", e.getMessage());
                return Optional.absent();
            }
        }
        return Optional.of(sourceStream);
    }
}
