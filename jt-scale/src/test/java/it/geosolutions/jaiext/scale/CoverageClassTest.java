package it.geosolutions.jaiext.scale;

import static org.junit.Assert.*;
import it.geosolutions.jaiext.interpolators.InterpolationBicubicNew;
import it.geosolutions.jaiext.interpolators.InterpolationBilinearNew;
import it.geosolutions.jaiext.interpolators.InterpolationNearestNew;
import it.geosolutions.jaiext.scale.ScaleDataDescriptor;
import it.geosolutions.jaiext.scale.ScalePropertyGenerator;

import java.awt.Rectangle;
import java.awt.image.DataBuffer;
import java.awt.image.RenderedImage;
import javax.media.jai.PlanarImage;
import javax.media.jai.ROI;
import javax.media.jai.ROIShape;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.NullDescriptor;
import org.jaitools.numeric.Range;
import org.junit.Test;


/**
 * This test-class extends the TestScale class and is used for extending the code-coverage of the project. In this test-class
 * are checked the getProperty() method of the ScaleDataDescriptor class and the capability of the ScaleDataCRIF.create() 
 * method to call the TranslateIntOpImage class or the CopyOpImage class when the requested operation is simply a translation
 * or a copy of the source image without ROI object. 
 */
public class CoverageClassTest extends TestScale {

    // this test-case is used for testing the getProperty() method of the ScaleDataDescriptor class
    @Test
    public void testROIProperty() {
        ScaleDataDescriptor descriptor = new ScaleDataDescriptor();
        ScalePropertyGenerator propertyGenerator = (ScalePropertyGenerator) descriptor
                .getPropertyGenerators()[0];

        boolean useROIAccessor = false;
        boolean bicubic2Disabled = false;
        int dataType = DataBuffer.TYPE_BYTE;
        Range<Byte> noDataRange = null;

        // Interpolators initialization
        // Nearest-Neighbor
        InterpolationNearestNew interpNear = new InterpolationNearestNew(noDataRange,
                useROIAccessor, destinationNoData, dataType);
        // Bilinear
        InterpolationBilinearNew interpBil = new InterpolationBilinearNew(DEFAULT_SUBSAMPLE_BITS,
                noDataRange, useROIAccessor, destinationNoData, dataType);
        // Bicubic
        InterpolationBicubicNew interpBic = new InterpolationBicubicNew(DEFAULT_SUBSAMPLE_BITS,
                noDataRange, useROIAccessor, destinationNoData, dataType, bicubic2Disabled,
                DEFAULT_PRECISION_BITS);

        // ROI creation
        ROIShape roi = roiCreation();

        byte imageValue = 127;

        // Test image creation

        RenderedImage testImg = createTestImage(DataBuffer.TYPE_BYTE, DEFAULT_WIDTH,
                DEFAULT_HEIGHT, imageValue, false);

        RenderedOp testIMG = NullDescriptor.create(testImg, null);

        // Scaled images
        RenderedImage scaleImgNear = ScaleDataDescriptor.create(testIMG, scaleX, scaleY, transX,
                transY, interpNear, roi, useROIAccessor, null);

        RenderedImage scaleImgBil = ScaleDataDescriptor.create(testIMG, scaleX, scaleY, transX,
                transY, interpBil, roi, useROIAccessor, null);

        RenderedImage scaleImgBic = ScaleDataDescriptor.create(testIMG, scaleX, scaleY, transX,
                transY, interpBic, roi, useROIAccessor, null);

        scaleImgNear.getTile(0, 0);
        scaleImgBil.getTile(0, 0);
        scaleImgBic.getTile(0, 0);

        // Scale operstion on ROI
        ROI roiNear = (ROI) propertyGenerator.getProperty("roi", scaleImgNear);
        ROI roiBil = (ROI) propertyGenerator.getProperty("roi", scaleImgBil);
        ROI roiBic = (ROI) propertyGenerator.getProperty("roi", scaleImgBic);

        // ROI starting bounds
        int roiWidth = roi.getBounds().width;
        int roiHeight = roi.getBounds().height;
        // ROI end bounds
        int roiNearWidth = roiNear.getBounds().width;
        int roiNearHeight = roiNear.getBounds().height;

        Rectangle scaleImgBilBounds = new Rectangle(testIMG.getMinX() + interpBil.getLeftPadding(),
                testIMG.getMinY() + interpBil.getTopPadding(), testIMG.getWidth()
                        - interpBil.getWidth() + 1, testIMG.getHeight() - interpBil.getHeight() + 1);

        int roiBoundWidth = (int) scaleImgBilBounds.getWidth();
        int roiBoundHeight = (int) scaleImgBilBounds.getHeight();

        int roiBilWidth = roiBil.getBounds().width;
        int roiBilHeighth = roiBil.getBounds().height;

        int roiBicWidth = roiBic.getBounds().width ;
        int roiBicHeight = roiBic.getBounds().height;

        // Nearest
        assertEquals((int) (roiWidth * scaleX), roiNearWidth);
        assertEquals((int) (roiHeight * scaleY), roiNearHeight);
        // Bilinear
        assertEquals((int) (roiBoundWidth * scaleX), roiBilWidth);
        assertEquals((int) (roiBoundHeight * scaleY), roiBilHeighth);
        // Bicubic
        assertEquals((int) (roiWidth * scaleX), roiBicWidth);
        assertEquals((int) (roiHeight * scaleY), roiBicHeight);

    }

    @Test
    public void testTranslation() {

        boolean useROIAccessor = false;
        int dataType = DataBuffer.TYPE_BYTE;
        Range<Byte> noDataRange = null;

        float xScale = 1.0f;
        float yScale = 1.0f;
        float xTrans = 3f;
        float yTrans = 3f;

        byte imageValue = 127;
        
        // Nearest-Neighbor
        InterpolationNearestNew interpNear = new InterpolationNearestNew(noDataRange,
                useROIAccessor, destinationNoData, dataType);

        RenderedImage testIMG = createTestImage(dataType, DEFAULT_WIDTH, DEFAULT_HEIGHT, imageValue,
                false);

        // Scaled images
        PlanarImage scaleImgNear = ScaleDataDescriptor.create(testIMG, xScale, yScale, xTrans,
                yTrans, interpNear, null, useROIAccessor, null);
        scaleImgNear.getTiles();
        
        double actualX=scaleImgNear.getMinX();
        double actualY=scaleImgNear.getMinY();
        
        double expectedX=testIMG.getMinX()+ xTrans;
        double expectedY=testIMG.getMinY()+ yTrans;
        
        double tolerance = 0.1f;
        
        assertEquals(expectedX, actualX,tolerance);
        assertEquals(expectedY, actualY,tolerance);
    }
    
    @Test
    public void testCopy() {

        boolean useROIAccessor = false;
        int dataType = DataBuffer.TYPE_BYTE;
        Range<Byte> noDataRange = null;

        float xScale = 1.0f;
        float yScale = 1.0f;
        float xTrans = 0.0f;
        float yTrans = 0.0f;

        byte imageValue = 127;
        
        // Nearest-Neighbor
        InterpolationNearestNew interpNear = new InterpolationNearestNew(noDataRange,
                useROIAccessor, destinationNoData, dataType);

        RenderedImage testIMG = createTestImage(dataType, DEFAULT_WIDTH, DEFAULT_HEIGHT, imageValue,
                false);

        // Scaled images
        PlanarImage scaleImgNear = ScaleDataDescriptor.create(testIMG, xScale, yScale, xTrans,
                yTrans, interpNear, null, useROIAccessor, null);
        scaleImgNear.getTiles();
        
        double actualX=scaleImgNear.getMinX();
        double actualY=scaleImgNear.getMinY();
        
        double expectedX=testIMG.getMinX();
        double expectedY=testIMG.getMinY();
        
        double tolerance = 0.1f;
        
        assertEquals(expectedX, actualX,tolerance);
        assertEquals(expectedY, actualY,tolerance); 
    }    
}