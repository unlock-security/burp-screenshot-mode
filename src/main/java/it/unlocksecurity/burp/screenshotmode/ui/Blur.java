package it.unlocksecurity.burp.screenshotmode.ui;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/** In-place blur of an image region; three box blur passes approximate a Gaussian. */
final class Blur {

    private static final int PASSES = 3;

    private Blur() {}

    static void apply(BufferedImage image, Rectangle region, int radius) {
        int width = region.width;
        int height = region.height;
        int[] pixels = image.getRGB(region.x, region.y, width, height, null, 0, width);
        int[] buffer = new int[pixels.length];
        for (int pass = 0; pass < PASSES; pass++) {
            boxBlur(pixels, buffer, width, height, radius, true);
            boxBlur(buffer, pixels, width, height, radius, false);
        }
        image.setRGB(region.x, region.y, width, height, pixels, 0, width);
    }

    /** One direction of a box blur; edge pixels are clamped, so nothing outside the region bleeds in. */
    private static void boxBlur(int[] source, int[] target, int width, int height, int radius, boolean horizontal) {
        int lines = horizontal ? height : width;
        int length = horizontal ? width : height;
        int window = 2 * radius + 1;
        for (int line = 0; line < lines; line++) {
            int r = 0;
            int g = 0;
            int b = 0;
            for (int i = -radius; i <= radius; i++) {
                int pixel = source[index(line, clamp(i, length), width, horizontal)];
                r += (pixel >> 16) & 0xFF;
                g += (pixel >> 8) & 0xFF;
                b += pixel & 0xFF;
            }
            for (int i = 0; i < length; i++) {
                target[index(line, i, width, horizontal)] =
                    0xFF000000 | ((r / window) << 16) | ((g / window) << 8) | (b / window);
                int outgoing = source[index(line, clamp(i - radius, length), width, horizontal)];
                int incoming = source[index(line, clamp(i + radius + 1, length), width, horizontal)];
                r += ((incoming >> 16) & 0xFF) - ((outgoing >> 16) & 0xFF);
                g += ((incoming >> 8) & 0xFF) - ((outgoing >> 8) & 0xFF);
                b += (incoming & 0xFF) - (outgoing & 0xFF);
            }
        }
    }

    private static int index(int line, int position, int width, boolean horizontal) {
        return horizontal ? line * width + position : position * width + line;
    }

    private static int clamp(int value, int length) {
        return Math.max(0, Math.min(length - 1, value));
    }
}
