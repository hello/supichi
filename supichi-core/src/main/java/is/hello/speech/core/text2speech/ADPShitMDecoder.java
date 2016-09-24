/*
Copyright 2009 The Open University
http://www.open.ac.uk/lts/projects/audioapplets/

This file is part of the "Open University audio applets" project.

The "Open University audio applets" project is free software: you can
redistribute it and/or modify it under the terms of the GNU General Public
License as published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

The "Open University audio applets" project is distributed in the hope that it
will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with the "Open University audio applets" project.
If not, see <http://www.gnu.org/licenses/>.
*/
package is.hello.speech.core.text2speech;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static is.hello.speech.core.text2speech.AudioUtils.INDEX_TABLE;
import static is.hello.speech.core.text2speech.AudioUtils.STEP_SIZE_TABLE;

/**
 * ADPCM decoder.
 */
public class ADPShitMDecoder
{


    private static final Logger LOGGER = LoggerFactory.getLogger(ADPShitMDecoder.class);


    /**
     * Decodes a block (ADPCMEncoder.BLOCKBYTES) of ADPCM data.
     * @param adpcm Block of data
     * @return Block of 16-bit 16 kHz decoded audio (size ADPCMEncoder.BLOCKSAMPLES*2)
     */
    public static byte[] decodeADPCMAudio(byte[] adpcm) throws IOException {
        {
            final DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(adpcm));

            final int outputSize = (adpcm.length + 1) * 4;

            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            final DataOutputStream dataOutputStream = new DataOutputStream(outputStream);

            short valpred = 0;
            int index = 0;
            int step = STEP_SIZE_TABLE[0];

            boolean bufferstep = false;

            int sign;
            int vpdiff;

            int delta;
            int inPos = 0;

            int readValue = 0;
            for (int i = 0; i < adpcm.length * 2 - 1; i++) {

                // step 1
                final byte currentData = adpcm[inPos];

                if (bufferstep) {
                    delta = readValue & 0x0f;
                } else {
                    readValue = dataInputStream.readByte();
                    delta = (currentData >> 4) & 0x0f;
                    inPos++;
                }

                bufferstep = !bufferstep;

                // step 2
                index += INDEX_TABLE[delta];
                if (index < 0) index = 0;
                if (index > 88) index = 88;

                // step 3
                sign = delta & 8;
                delta = delta & 7;

                // step 4
                vpdiff = step >> 3;
                if ((delta & 4) != 0) vpdiff += step;
                if ((delta & 2) != 0) vpdiff += step >> 1;
                if ((delta & 1) != 0) vpdiff += step >> 2;

                if (sign != 0) {
                    valpred -= vpdiff;
                } else {
                    valpred += vpdiff;
                }

                // step 5 - clamp values
                if (valpred > 32767) {
                    valpred = 32767;
                } else if (valpred < -32768) {
                    valpred = -32768;
                }

                // step 6 - update step
                step = STEP_SIZE_TABLE[index];

                // step 7 - output value
                System.out.println(valpred);
                dataOutputStream.writeShort(valpred);
            }

            return outputStream.toByteArray();

        }
    }

    public static void main(String[] args) throws Exception {
        Path path = Paths.get("/Users/kingshy/DEV/Hello/supichi/scripts/chris_adpcm.raw");
        byte[] inputBytes = Files.readAllBytes(path);
        byte[] outputBytes = decodeADPCMAudio(inputBytes);

        final String outputFile = "/Users/kingshy/DEV/Hello/supichi/scripts/output.raw";
        FileOutputStream fos = new FileOutputStream(outputFile);
        fos.write(outputBytes);
        fos.close();

//        Path path = Paths.get("/Users/kingshy/DEV/Hello/supichi/scripts/output.raw");
//        byte[] inputBytes = Files.readAllBytes(path);
//
//
//        Path path2 = Paths.get("/Users/kingshy/DEV/Hello/supichi/scripts/chroutput2.raw");
//        byte[] inputBytes2 = Files.readAllBytes(path2);
//
//        int size = Math.max(inputBytes.length, inputBytes2.length);
//        for (int i = 0; i<size; i++) {
//            if (inputBytes[i] != inputBytes2[i]) {
//                System.out.println("difference at " + i + "java: " + inputBytes[i] + "C: " + inputBytes2[i]);
//            }
//        }

    }

}
