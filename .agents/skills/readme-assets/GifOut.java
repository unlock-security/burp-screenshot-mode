package it.unlocksecurity.burp.screenshotmode.ui;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Streams frames into an animated GIF89a that loops forever. Each frame only stores the rectangle that changed since
 * the previous one, with its own color table (median cut when it has more than 256 colors), drawn over the previous
 * frame; a frame identical to the previous one only lengthens it.
 */
final class GifOut implements AutoCloseable {

    private final OutputStream out;
    /** What the last frame drew: unchanged pixels of a frame are left transparent, so they never change color. */
    private static final int KEEP = 0x01000000;

    private int[] previous;
    private int[] shown;
    private int canvasWidth;
    private int[] pending;
    private int pendingX;
    private int pendingY;
    private int pendingWidth;
    private int pendingHeight;
    private double pendingDelay;
    private double carry;
    int frames;
    /** The most pixels with a visible color error in any frame: a handful at most, else the palettes are wrong. */
    int worst;

    GifOut(File file) throws Exception {
        out = new BufferedOutputStream(new FileOutputStream(file));
    }

    void add(BufferedImage frame, double delayMs) throws Exception {
        int w = frame.getWidth(),
            h = frame.getHeight();
        int[] pixels = frame.getRGB(0, 0, w, h, null, 0, w);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] &= 0xffffff;
        }
        if (previous == null) {
            header(w, h);
            shown = new int[w * h];
            canvasWidth = w;
            previous = pixels;
            pending = pixels.clone();
            pendingWidth = w;
            pendingHeight = h;
            pendingDelay = delayMs;
            return;
        }
        int x0 = w,
            y0 = h,
            x1 = -1,
            y1 = -1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                if (pixels[row + x] != previous[row + x]) {
                    if (x < x0) x0 = x;
                    if (x > x1) x1 = x;
                    if (y < y0) y0 = y;
                    y1 = y;
                }
            }
        }
        if (x1 < 0) {
            pendingDelay += delayMs;
            return;
        }
        flush();
        int rw = x1 - x0 + 1,
            rh = y1 - y0 + 1;
        int[] rect = new int[rw * rh];
        for (int y = 0; y < rh; y++) {
            for (int x = 0; x < rw; x++) {
                int i = (y0 + y) * w + x0 + x;
                // Unchanged and shown close enough: left as it is. Otherwise drawn again, which also fixes a color
                // an earlier palette could only approximate.
                rect[y * rw + x] = pixels[i] == previous[i] && distance(shown[i], pixels[i]) <= 24 ? KEEP : pixels[i];
            }
        }
        previous = pixels;
        pending = rect;
        pendingX = x0;
        pendingY = y0;
        pendingWidth = rw;
        pendingHeight = rh;
        pendingDelay = delayMs;
    }

    private void flush() throws Exception {
        if (pending == null) {
            return;
        }
        double total = pendingDelay + carry;
        int cs = Math.max(2, (int) Math.round(total / 10));
        carry = total - cs * 10;
        write(cs);
        pending = null;
        frames++;
        // What a decoder now shows against the source: a large difference means a visible color error.
        int off = 0;
        for (int i = 0; i < shown.length; i++) {
            if (distance(shown[i], previous[i]) > 90) {
                off++;
            }
        }
        worst = Math.max(worst, off);
    }

    private void header(int w, int h) throws Exception {
        out.write("GIF89a".getBytes(StandardCharsets.US_ASCII));
        short16(w);
        short16(h);
        out.write(0); // no global color table
        out.write(0);
        out.write(0);
        out.write(new byte[] { 0x21, (byte) 0xff, 0x0b });
        out.write("NETSCAPE2.0".getBytes(StandardCharsets.US_ASCII));
        out.write(new byte[] { 3, 1, 0, 0, 0 });
    }

    private void short16(int value) throws Exception {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
    }

    private void write(int delayCs) throws Exception {
        Indexed indexed = quantize(pending);
        // Graphic control: "do not dispose", so the next frame draws over this one; KEEP pixels are transparent.
        out.write(new byte[] { 0x21, (byte) 0xf9, 4, (byte) ((1 << 2) | (indexed.transparent >= 0 ? 1 : 0)) });
        short16(delayCs);
        out.write(Math.max(0, indexed.transparent));
        out.write(0);
        out.write(0x2c);
        short16(pendingX);
        short16(pendingY);
        short16(pendingWidth);
        short16(pendingHeight);
        out.write(0x80 | (indexed.bits - 1));
        for (int i = 0; i < 1 << indexed.bits; i++) {
            int c = i < indexed.palette.length ? indexed.palette[i] : 0;
            out.write((c >> 16) & 0xff);
            out.write((c >> 8) & 0xff);
            out.write(c & 0xff);
        }
        lzw(indexed.pixels, Math.max(2, indexed.bits));
        for (int y = 0; y < pendingHeight; y++) {
            for (int x = 0; x < pendingWidth; x++) {
                int index = indexed.pixels[y * pendingWidth + x] & 0xff;
                if (index != indexed.transparent) {
                    shown[(pendingY + y) * canvasWidth + pendingX + x] = indexed.palette[index];
                }
            }
        }
    }

    // --- LZW, in sub-blocks of at most 255 bytes ---

    private final byte[] block = new byte[255];
    private int blockLength;
    private int bitBuffer;
    private int bitCount;

    private void lzw(byte[] pixels, int minCodeSize) throws Exception {
        out.write(minCodeSize);
        int clear = 1 << minCodeSize,
            end = clear + 1;
        int codeSize = minCodeSize + 1,
            next = end + 1;
        Map<Integer, Integer> codes = new HashMap<>();
        blockLength = 0;
        bitBuffer = 0;
        bitCount = 0;
        bits(clear, codeSize);
        int prefix = pixels[0] & 0xff;
        for (int i = 1; i < pixels.length; i++) {
            int k = pixels[i] & 0xff;
            int key = (prefix << 8) | k;
            Integer code = codes.get(key);
            if (code != null) {
                prefix = code;
                continue;
            }
            bits(prefix, codeSize);
            // The decoder adds each code one step later: grow once the next free code no longer fits.
            if (next >= 1 << codeSize && codeSize < 12) {
                codeSize++;
            }
            if (next < 4096) {
                codes.put(key, next++);
            } else {
                bits(clear, codeSize);
                codes.clear();
                codeSize = minCodeSize + 1;
                next = end + 1;
            }
            prefix = k;
        }
        bits(prefix, codeSize);
        if (next >= 1 << codeSize && codeSize < 12) {
            codeSize++;
        }
        bits(end, codeSize);
        if (bitCount > 0) {
            byteOut(bitBuffer & 0xff);
        }
        if (blockLength > 0) {
            out.write(blockLength);
            out.write(block, 0, blockLength);
        }
        out.write(0);
    }

    private void bits(int code, int size) throws Exception {
        bitBuffer |= code << bitCount;
        bitCount += size;
        while (bitCount >= 8) {
            byteOut(bitBuffer & 0xff);
            bitBuffer >>>= 8;
            bitCount -= 8;
        }
    }

    private void byteOut(int b) throws Exception {
        block[blockLength++] = (byte) b;
        if (blockLength == 255) {
            out.write(255);
            out.write(block, 0, 255);
            blockLength = 0;
        }
    }

    // --- Palette ---

    private record Indexed(int[] palette, int bits, byte[] pixels, int transparent) {}

    private static Indexed quantize(int[] pixels) {
        Map<Integer, Integer> counts = new HashMap<>();
        boolean keeps = false;
        for (int p : pixels) {
            if (p == KEEP) {
                keeps = true;
            } else {
                counts.merge(p, 1, Integer::sum);
            }
        }
        int room = keeps ? 255 : 256;
        int[] colors;
        if (counts.size() <= room) {
            colors = counts.keySet().stream().mapToInt(Integer::intValue).toArray();
        } else {
            // The most frequent colors (backgrounds, text) exactly, the rest by median cut.
            List<Map.Entry<Integer, Integer>> byCount = new ArrayList<>(counts.entrySet());
            byCount.sort((a, b) -> b.getValue() - a.getValue());
            int exact = room / 2;
            Map<Integer, Integer> rest = new HashMap<>();
            for (int i = exact; i < byCount.size(); i++) {
                rest.put(byCount.get(i).getKey(), byCount.get(i).getValue());
            }
            int[] cut = medianCut(rest, room - exact);
            colors = new int[exact + cut.length];
            for (int i = 0; i < exact; i++) {
                colors[i] = byCount.get(i).getKey();
            }
            System.arraycopy(cut, 0, colors, exact, cut.length);
        }
        int transparent = keeps ? colors.length : -1;
        int[] palette = keeps ? java.util.Arrays.copyOf(colors, colors.length + 1) : colors;
        int bits = 1;
        while (1 << bits < palette.length) bits++;
        Map<Integer, Integer> nearest = new HashMap<>();
        byte[] indices = new byte[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int c = pixels[i];
            indices[i] = (byte) (
                c == KEEP ? transparent : (int) nearest.computeIfAbsent(c, color -> closest(colors, color))
            );
        }
        return new Indexed(palette, bits, indices, transparent);
    }

    private static int distance(int a, int b) {
        return (
            Math.abs(((a >> 16) & 255) - ((b >> 16) & 255)) +
            Math.abs(((a >> 8) & 255) - ((b >> 8) & 255)) +
            Math.abs((a & 255) - (b & 255))
        );
    }

    private static int closest(int[] palette, int color) {
        int best = 0,
            bestDistance = Integer.MAX_VALUE;
        int r = (color >> 16) & 255,
            g = (color >> 8) & 255,
            b = color & 255;
        for (int i = 0; i < palette.length; i++) {
            int dr = r - ((palette[i] >> 16) & 255),
                dg = g - ((palette[i] >> 8) & 255),
                db = b - (palette[i] & 255);
            int distance = 2 * dr * dr + 4 * dg * dg + 3 * db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    /** Colors as {rgb, count}. */
    private record Box(List<int[]> colors) {
        int range(int shift) {
            int min = 255,
                max = 0;
            for (int[] c : colors) {
                int v = (c[0] >> shift) & 255;
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            return max - min;
        }

        long weight() {
            long sum = 0;
            for (int[] c : colors) sum += c[1];
            return sum;
        }

        int average() {
            long r = 0,
                g = 0,
                b = 0,
                n = 0;
            for (int[] c : colors) {
                r += (long) ((c[0] >> 16) & 255) * c[1];
                g += (long) ((c[0] >> 8) & 255) * c[1];
                b += (long) (c[0] & 255) * c[1];
                n += c[1];
            }
            return (int) (((r / n) << 16) | ((g / n) << 8) | (b / n));
        }
    }

    private static int[] medianCut(Map<Integer, Integer> counts, int size) {
        List<int[]> all = new ArrayList<>();
        counts.forEach((color, count) -> all.add(new int[] { color, count }));
        List<Box> boxes = new ArrayList<>(List.of(new Box(all)));
        while (boxes.size() < size) {
            Box widest = null;
            int widestShift = 0;
            double widestScore = -1;
            for (Box box : boxes) {
                if (box.colors.size() < 2) continue;
                for (int shift : new int[] { 16, 8, 0 }) {
                    double score = box.range(shift) * Math.sqrt(box.weight());
                    if (score > widestScore) {
                        widestScore = score;
                        widest = box;
                        widestShift = shift;
                    }
                }
            }
            if (widest == null) break;
            int shift = widestShift;
            widest.colors.sort(Comparator.comparingInt(c -> (c[0] >> shift) & 255));
            long half = widest.weight() / 2,
                sum = 0;
            int split = 1;
            for (int i = 0; i < widest.colors.size() - 1; i++) {
                sum += widest.colors.get(i)[1];
                if (sum >= half) {
                    split = i + 1;
                    break;
                }
            }
            boxes.remove(widest);
            boxes.add(new Box(new ArrayList<>(widest.colors.subList(0, split))));
            boxes.add(new Box(new ArrayList<>(widest.colors.subList(split, widest.colors.size()))));
        }
        return boxes.stream().mapToInt(Box::average).toArray();
    }

    @Override
    public void close() throws Exception {
        flush();
        out.write(0x3b);
        out.close();
    }
}
