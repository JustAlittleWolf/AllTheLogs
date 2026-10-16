package me.wolfii.allthelogs.client.ui.widget;

import com.mojang.blaze3d.platform.NativeImage;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.wolfii.allthelogs.client.playtime.PlaytimeColors;
import me.wolfii.allthelogs.client.playtime.PlaytimePattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Off-screen image in screen pixels that a chart paints once and then draws every frame as one textured
 * quad. Painting tens of thousands of small rectangles every frame is what made large charts slow.
 * Callers repaint only when the data, the size, or the highlight changes, and {@link #close()} the canvas
 * when their screen closes.
 */
final class PixelCanvas implements AutoCloseable {
    private static final AtomicInteger NEXT_ID = new AtomicInteger();

    private final Identifier id = Identifier.fromNamespaceAndPath("allthelogs",
        "dynamic/playtime_" + NEXT_ID.getAndIncrement());
    private DynamicTexture texture;
    private int[] pixels = new int[0];
    private int width;
    private int height;

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    /**
     * Resizes to {@code width} by {@code height} and clears every pixel to transparent.
     */
    void reset(int width, int height) {
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        if (w != this.width || h != this.height) {
            this.width = w;
            this.height = h;
            pixels = new int[w * h];
            releaseTexture();
        } else {
            Arrays.fill(pixels, 0);
        }
    }

    void fill(int left, int top, int right, int bottom, int argb) {
        int x0 = Math.max(0, left);
        int x1 = Math.min(width, right);
        int y0 = Math.max(0, top);
        int y1 = Math.min(height, bottom);
        if (x1 <= x0) return;
        for (int y = y0; y < y1; y++) Arrays.fill(pixels, y * width + x0, y * width + x1, argb);
    }

    /**
     * Fills the rectangle with {@code argb}, and its {@code pattern} pixels with a darker shade of it.
     */
    void fill(int left, int top, int right, int bottom, int argb, PlaytimePattern pattern, int unit) {
        if (pattern == PlaytimePattern.SOLID) {
            fill(left, top, right, bottom, argb);
            return;
        }
        int shade = PlaytimeColors.stripe(argb);
        int x0 = Math.max(0, left);
        int x1 = Math.min(width, right);
        int y0 = Math.max(0, top);
        int y1 = Math.min(height, bottom);
        for (int y = y0; y < y1; y++) {
            int row = y * width;
            for (int x = x0; x < x1; x++) pixels[row + x] = pattern.on(x, y, unit) ? shade : argb;
        }
    }

    /**
     * Sends the painted pixels to the GPU. Call after painting and before {@link #draw}.
     */
    void upload() {
        if (texture == null) {
            texture = new DynamicTexture(id::toString, new NativeImage(width, height, false));
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }
        NativeImage image = texture.getPixels();
        if (image == null) return;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) image.setPixel(x, y, pixels[row + x]);
        }
        texture.upload();
    }

    /**
     * Draws the whole canvas with its top-left corner at screen pixel {@code x}, {@code y}.
     */
    void draw(OwoUIGraphics graphics, int scale, int x, int y) {
        if (texture == null) return;
        PlaytimePainter.inPixels(graphics, scale, () ->
            graphics.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, width, height, width, height));
    }

    @Override
    public void close() {
        releaseTexture();
    }

    private void releaseTexture() {
        if (texture == null) return;
        Minecraft.getInstance().getTextureManager().release(id);
        texture = null;
    }
}
