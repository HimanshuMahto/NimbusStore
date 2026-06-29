package cloudinary.project.config;

import net.coobird.thumbnailator.filters.ImageFilter;
import java.awt.image.BufferedImage;

public class SepiaFilter implements ImageFilter {
    @Override
    public BufferedImage apply(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        BufferedImage sepia = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int p = img.getRGB(x, y);

                int a = (p >> 24) & 0xff;
                int r = (p >> 16) & 0xff;
                int g = (p >> 8) & 0xff;
                int b = p & 0xff;

                // Calculate sepia values using standard weights
                int newR = (int) (0.393 * r + 0.769 * g + 0.189 * b);
                int newG = (int) (0.349 * r + 0.686 * g + 0.168 * b);
                int newB = (int) (0.272 * r + 0.534 * g + 0.131 * b);

                // Clamp values to 255 maximum
                r = Math.min(255, newR);
                g = Math.min(255, newG);
                b = Math.min(255, newB);

                p = (a << 24) | (r << 16) | (g << 8) | b;
                sepia.setRGB(x, y, p);
            }
        }
        return sepia;
    }
}
