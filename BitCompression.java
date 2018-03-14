package bitcompression;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;

/**
 *
 * @author B Ricks, PhD <bricks@unomaha.edu>
 */
public class BitCompression {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {

            BufferedImage original = ImageIO.read(new File("villa.jpeg"));

            String compressedFilename = compress(original);

            BufferedImage decompressed = decompress(compressedFilename);

            ImageIO.write(decompressed, "PNG", new File("villa.png"));

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static String compress(BufferedImage original) {

        String filename = "compressed.txt";

        try (BufferedOutputStream br = new BufferedOutputStream(new FileOutputStream(filename))) {
            int width = original.getWidth();
            int height = original.getHeight();
            br.write(("" + width).getBytes()); //Write the width in bytes
            br.write((" ").getBytes());//Write a space delimiter
            br.write(("" + height).getBytes());//Write the height in bytes
            br.write((" ").getBytes()); //Write a space delimiter

            Set<Color> uniqueColors = new HashSet<Color>(); //Create an object to store all the unique colors in the image

            //Loop over the image and add all the colors so we have a set of all the colors
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    Color pixel = new Color(original.getRGB(x, y));
                    uniqueColors.add(pixel);
                }
            }

            br.write(("" + uniqueColors.size()).getBytes()); //Write the numbe of unique colors
            br.write(("\r\n").getBytes()); //Write a new line delimiter

            Color[] allColors = uniqueColors.toArray(new Color[]{}); //Get the unique colors as an array
            Arrays.sort(allColors, (a, b) -> Integer.compare(a.hashCode(), b.hashCode())); //Sort using the color's hash codes
            
            //Write the sorted colors that make up the index
            for(Color color : allColors)
            {
                byte rByte = (byte)color.getRed();
                byte gByte = (byte)color.getGreen();
                byte bByte = (byte)color.getBlue();
                
                br.write(rByte);
                br.write(gByte);
                br.write(bByte);
                
            }

            //Store all the indeces in an array so we can save them as bits later on.
            List<Integer> indeces = new ArrayList<Integer>();

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    Color pixel = new Color(original.getRGB(x, y));

                    int index = Arrays.binarySearch(allColors, pixel, (a, b) -> Integer.compare(a.hashCode(), b.hashCode()));
                    indeces.add(index);
                }
            }

            List<Byte> allBits = new ArrayList<Byte>();

            int indexSizeInBits = (int) Math.ceil(Math.log(allColors.length) / Math.log(2));

            for (Integer i : indeces) {

                for (int inc = 0; inc < indexSizeInBits; inc++) {
                    allBits.add(getBit(i, inc));
                }

            }

            int numBytes = (int) Math.ceil(allBits.size() / 8);
            for (int inc = 0; inc < numBytes; inc++) {
                int bitStart = inc * 8;
                byte b = 0;
                for (int index = 0; index < 8; index++) {
                    byte bit = allBits.get(bitStart + index);
                    bit <<= index;
                    b |= bit;
                }

                br.write(b);
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return filename;

    }

    private static byte getBit(int integer, int index) {
        return (byte) ((integer >> index) & 1);
    }

    private static BufferedImage decompress(String compressed) {

        BufferedImage bi = null;

        try (FileInputStream fr = new FileInputStream(compressed)) {

            int endOfLineIndex = 0;
            byte fileContent[] = new byte[(int) new File(compressed).length()];
            fr.read(fileContent);
            while (endOfLineIndex < fileContent.length && fileContent[endOfLineIndex++] != 0x0A);

            if (endOfLineIndex >= fileContent.length) {
                throw new RuntimeException("Couldn't find the first line of the file");
            }

            byte[] header = Arrays.copyOfRange(fileContent, 0, endOfLineIndex);
            String headerString = new String(header);
            String[] widthAndHeightAndIndeces = headerString.split(" ");
            int width = Integer.parseInt(widthAndHeightAndIndeces[0].trim());
            int height = Integer.parseInt(widthAndHeightAndIndeces[1].trim());
            int indeces = Integer.parseInt(widthAndHeightAndIndeces[2].trim());
            bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

            int index = endOfLineIndex;
            int pixelIndex = 0;

            int indexSizeInBits = (int) Math.ceil(Math.log(indeces) / Math.log(2));

            Color[] indexColors = new Color[indeces];
            for (int inc = 0; inc < indeces; inc++) {
                int currentByte = index + inc * 3;
                int r = Byte.toUnsignedInt(fileContent[currentByte]);
                int g = Byte.toUnsignedInt(fileContent[currentByte + 1]);
                int b = Byte.toUnsignedInt(fileContent[currentByte + 2]);

                Color pixel = new Color(r, g, b);

                indexColors[inc] = pixel;

            }
            index += indeces * 3;

            List<Byte> allBits = new ArrayList<Byte>();

            for (int i = index; i < fileContent.length; i++) {
                byte b = fileContent[i];
                for (int inc = 0; inc < 8; inc++) {
                    byte bit = getBit(b, inc);
                    allBits.add(bit);
                }
            }

            int bitIndex = 0;

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {

                    int myIndex = 0;

                    for (int inc = 0; inc < indexSizeInBits; inc++) {
                        int bit = allBits.get(bitIndex + inc);
                        bit <<= inc;
                        myIndex |= bit;
                    }
                    
                    bitIndex += indexSizeInBits;

                    bi.setRGB(x,y, indexColors[myIndex].getRGB());
                }
            }
        } catch (IOException ex) {
        }

        return bi;
    }
}
