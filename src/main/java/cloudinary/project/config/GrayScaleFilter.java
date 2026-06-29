package cloudinary.project.config;

import net.coobird.thumbnailator.filters.ImageFilter;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;

public class GrayScaleFilter implements ImageFilter {

    @Override
    public BufferedImage apply(BufferedImage img) {
        BufferedImage grayscale = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        ColorConvertOp op = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
        return op.filter(img, grayscale);
    }

}
