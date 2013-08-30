package it.geosolutions.jaiext.interpolators;

import java.awt.Rectangle;
import java.awt.image.DataBuffer;

import javax.media.jai.Interpolation;
import javax.media.jai.RasterAccessor;
import javax.media.jai.iterator.RandomIter;

import org.jaitools.numeric.Range;

public class InterpolationBilinear extends Interpolation {

    /** serialVersionUID */
    private static final long serialVersionUID = 5238694001611785385L;

    // Method overriding. Performs the default bilinear interpolation without NO DATA or ROI control.
    @Override
    public int interpolateH(int[] arg0, int xfrac) {
        return ((arg0[1] - arg0[0]) * xfrac + (arg0[0] << subsampleBits) + round) >> subsampleBits;
    }

    @Override
    public float interpolateH(float[] arg0, float xfrac) {
        return (arg0[1] - arg0[0]) * xfrac + arg0[0];
    }

    @Override
    public double interpolateH(double[] arg0, float xfrac) {
        return (arg0[1] - arg0[0]) * xfrac + arg0[0];
    }

    /** The value of 0.5 scaled by 2^subsampleBits */
    private int round;

    /**
     * The number of bits to shift integer pixels to account for subsampleBits
     */
    private int subsampleBits;

    /**
     * Twice the value of 'shift'. Accounts for accumulated scaling shifts in two-axis interpolation
     */
    private int shift2;

    /** The value of 0.5 scaled by 2^shift2 */
    private int round2;

    /** Range of NO DATA values to be checked */
    private Range noDataRange;

    /** ROI bounds used for checking the position of the pixel */
    private Rectangle roiBounds;

    /** Random interator used for navigate inside the ROI and searching the pixel */
    private RandomIter roiIter;

    /** Boolean for checking if the ROI Accessor must be used by the interpolator */
    private boolean useROIAccessor;

    /**
     * Destination NO DATA value used when the image pixel is outside of the ROI or is contained in the NO DATA range
     * */
    private double destinationNoData;

    /** This value is the destination NO DATA values for binary images */
    private int black;

    /** Image data Type */
    private int dataType;

    /** Boolean for checking if No data is NaN*/
    private boolean isRangeNaN=false;
    
    /** Boolean for checking if No data is Positive Infinity*/
    private boolean isPositiveInf=false;
    
    /** Boolean for checking if No data is Negative Infinity*/
    private boolean isNegativeInf=false;
    
    /**
     * InterpolationBilinear instance used only with the ScaleOpImage constructor for setting the interpolation type as Bilinear
     * */
    private InterpolationBilinear interpBilinear;

    /**
     * Default value for subsample bits
     * */
    public static final int DEFAULT_SUBSAMPLE_BITS = 8;

    /**
     * Simple interpolator object used for Bilinear interpolation. On construction it is possible to set a range for no data values that will be
     * considered in the interpolation method.
     */
    public InterpolationBilinear(int subsampleBits, Range noDataRange,
            boolean useROIAccessor, double destinationNoData, int dataType) {

        super(2, 2, 0, 1, 0, 1, subsampleBits, subsampleBits);

        this.subsampleBits = subsampleBits;
        round = 1 << (subsampleBits - 1);

        shift2 = 2 * subsampleBits;
        round2 = 1 << (shift2 - 1);

        if (noDataRange != null) {
            this.noDataRange = noDataRange;            
            if((dataType==DataBuffer.TYPE_FLOAT||dataType==DataBuffer.TYPE_DOUBLE)){
            	// If the range goes from -Inf to Inf No Data is NaN
            	if(!noDataRange.isPoint() && noDataRange.isMaxInf() && noDataRange.isMinNegInf()){
            		isRangeNaN=true;
            	// If the range is a positive infinite point isPositiveInf flag is set
            	}else if(noDataRange.isPoint() && noDataRange.isMaxInf() && noDataRange.isMinInf()){
            		isPositiveInf=true;
            	// If the range is a negative infinite point isNegativeInf flag is set
            	}else if(noDataRange.isPoint() && noDataRange.isMaxNegInf() && noDataRange.isMinNegInf()){
            		isNegativeInf=true;
            	}
            }        
        }
        this.useROIAccessor = useROIAccessor;
        this.destinationNoData = destinationNoData;
        black = ((int) destinationNoData) & 1;
        this.dataType = dataType;
    }

    public void setROIdata(Rectangle roiBounds, RandomIter roiIter){
        if (roiBounds != null && roiIter != null) {
            this.roiBounds = roiBounds;
            this.roiIter = roiIter;

        } else if ((roiBounds == null && roiIter != null) || (roiBounds != null && roiIter == null)) {
            throw new IllegalArgumentException(
                    "If roiBounds or roiIter are not null, so even the other must be not null");
        }
    }
    
    public double getDestinationNoData() {
        return destinationNoData;
    }
    
    public Range getNoDataRange() {
        return noDataRange;
    }

    public void setNoDataRange(Range noDataRange) {
        if (noDataRange != null) {
            this.noDataRange = noDataRange;            
            if((dataType==DataBuffer.TYPE_FLOAT||dataType==DataBuffer.TYPE_DOUBLE)){
            	// If the range goes from -Inf to Inf No Data is NaN
            	if(!noDataRange.isPoint() && noDataRange.isMaxInf() && noDataRange.isMinNegInf()){
            		isRangeNaN=true;
            	// If the range is a positive infinite point isPositiveInf flag is set
            	}else if(noDataRange.isPoint() && noDataRange.isMaxInf() && noDataRange.isMinInf()){
            		isPositiveInf=true;
            	// If the range is a negative infinite point isNegativeInf flag is set
            	}else if(noDataRange.isPoint() && noDataRange.isMaxNegInf() && noDataRange.isMinNegInf()){
            		isNegativeInf=true;
            	}
            }        
        }
    }
    
    public int getDataType() {
        return dataType;
    }   

    /** This method performs a bilinear interpolation of a pixel inside a not-Binary image. */
    public Number interpolate(RasterAccessor src, int bandIndex, int dnumbands, int posX, int posY,
            Number[] fracValues, Integer yValueROI, RasterAccessor roi, boolean setNoData) {
        // If the value must be set to NO DATA no other operation are needed.
        if (setNoData) {
            return destinationNoData;

        }
        // RasterAccessor data, useful for the pixel position.
        int srcScanLineStride = src.getScanlineStride();
        int srcPixelStride = src.getPixelStride();
        int srcBandOffset = src.getBandOffset(bandIndex);

        // 4 surrounding pixel of the central pixel.
        int posXlow = posX;
        int posYlow = posY;
        int posXhigh = posX + srcPixelStride;
        int posYhigh = posY + srcScanLineStride;
        // Initial value of the 4 pixel (different variables are used for float and double data).
        int s00 = 0;
        int s01 = 0;
        int s10 = 0;
        int s11 = 0;

        float s00f = 0;
        float s01f = 0;
        float s10f = 0;
        float s11f = 0;

        double s00d = 0;
        double s01d = 0;
        double s10d = 0;
        double s11d = 0;
        // src data array initialization

        byte[] srcDataByte;
        short[] srcDataShort;
        int[] srcDataInt;
        float[] srcDataFloat;
        double[] srcDataDouble;
        // Fractional Value
        int xfrac = 0;
        int yfrac = 0;

        float xfracf = 0;
        float yfracf = 0;

        double xfracd = 0;
        double yfracd = 0;
        // Get the four surrounding pixel values and the fractional value for x and y axes
        switch (dataType) {
        case DataBuffer.TYPE_BYTE:
            srcDataByte = src.getByteDataArray(bandIndex);
            s00 = srcDataByte[posXlow + posYlow] & 0xff;
            s01 = srcDataByte[posXhigh + posYlow] & 0xff;
            s10 = srcDataByte[posXlow + posYhigh] & 0xff;
            s11 = srcDataByte[posXhigh + posYhigh] & 0xff;
            xfrac = fracValues[0].intValue();
            yfrac = fracValues[1].intValue();
            break;
        case DataBuffer.TYPE_USHORT:
            srcDataShort = src.getShortDataArray(bandIndex);
            s00 = srcDataShort[posXlow + posYlow] & 0xffff;
            s01 = srcDataShort[posXhigh + posYlow] & 0xffff;
            s10 = srcDataShort[posXlow + posYhigh] & 0xffff;
            s11 = srcDataShort[posXhigh + posYhigh] & 0xffff;
            xfrac = fracValues[0].intValue();
            yfrac = fracValues[1].intValue();
            break;
        case DataBuffer.TYPE_SHORT:
            srcDataShort = src.getShortDataArray(bandIndex);
            s00 = srcDataShort[posXlow + posYlow];
            s01 = srcDataShort[posXhigh + posYlow];
            s10 = srcDataShort[posXlow + posYhigh];
            s11 = srcDataShort[posXhigh + posYhigh];
            xfrac = fracValues[0].intValue();
            yfrac = fracValues[1].intValue();
            break;
        case DataBuffer.TYPE_INT:
            srcDataInt = src.getIntDataArray(bandIndex);
            s00 = srcDataInt[posXlow + posYlow];
            s01 = srcDataInt[posXhigh + posYlow];
            s10 = srcDataInt[posXlow + posYhigh];
            s11 = srcDataInt[posXhigh + posYhigh];
            xfrac = fracValues[0].intValue();
            yfrac = fracValues[1].intValue();
            break;
        case DataBuffer.TYPE_FLOAT:
            srcDataFloat = src.getFloatDataArray(bandIndex);
            s00f = srcDataFloat[posXlow + posYlow];
            s01f = srcDataFloat[posXhigh + posYlow];
            s10f = srcDataFloat[posXlow + posYhigh];
            s11f = srcDataFloat[posXhigh + posYhigh];
            xfracf = fracValues[0].floatValue();
            yfracf = fracValues[1].floatValue();
            break;
        case DataBuffer.TYPE_DOUBLE:
            srcDataDouble = src.getDoubleDataArray(bandIndex);
            s00d = srcDataDouble[posXlow + posYlow];
            s01d = srcDataDouble[posXhigh + posYlow];
            s10d = srcDataDouble[posXlow + posYhigh];
            s11d = srcDataDouble[posXhigh + posYhigh];
            xfracd = fracValues[0].doubleValue();
            yfracd = fracValues[1].doubleValue();
            break;
        default:
            break;
        }

        // Pixel weight initialization. This values are set to 1 for the case that this pixel are
        // all inside the ROI and are not contained in the NO DATA Range.
        int w00 = 1;
        int w01 = 1;
        int w10 = 1;
        int w11 = 1;

        float w00f = 1;
        float w01f = 1;
        float w10f = 1;
        float w11f = 1;

        double w00d = 1;
        double w01d = 1;
        double w10d = 1;
        double w11d = 1;

        // If ROI accessor is present, it is used for checking if any of the 4 surrounding pixel belongs to ROI
        if (useROIAccessor) {
            if (yValueROI == null || roi == null) {
                throw new IllegalArgumentException(
                        "If rasterAccessor is set, ROI value must be provided");
            }
            // ROI scan line stride used for selecting the 4 surrounding pixels
            int roiScanLineStride = roi.getScanlineStride();

            int baseIndex = (posXlow / dnumbands) + (yValueROI);
            
            
            int w00index = baseIndex;
            int w01index = baseIndex + 1 ;
            int w10index = baseIndex + roiScanLineStride;
            int w11index = baseIndex + 1  + roiScanLineStride;
            // Array length initialization
            int roiDataLength = 0;
            // Check if the selected index belongs to the roi data array: if it is not present, the weight is 0,
            // Otherwise it takes the related value.
            byte[] roiDataArrayByte = roi.getByteDataArray(0);
            roiDataLength = roiDataArrayByte.length;            
            
            if(baseIndex>roiDataLength || roiDataArrayByte[w00index]==0){
            	return destinationNoData;
            }
            
            switch (dataType) {
            case DataBuffer.TYPE_BYTE:
                
                w00 = w00index < roiDataLength ? roiDataArrayByte[w00index] & 0xff : 0;
                w01 = w01index < roiDataLength ? roiDataArrayByte[w01index] & 0xff : 0;
                w10 = w10index < roiDataLength ? roiDataArrayByte[w10index] & 0xff : 0;
                w11 = w11index < roiDataLength ? roiDataArrayByte[w11index] & 0xff : 0;
                if (w00 == 0 && w01 == 0 && w10 == 0 && w11 == 0) {
                    return destinationNoData;
                }
                break;
            case DataBuffer.TYPE_USHORT:
                w00 = w00index < roiDataLength ? roiDataArrayByte[w00index] & 0xffff : 0;
                w01 = w01index < roiDataLength ? roiDataArrayByte[w01index] & 0xffff : 0;
                w10 = w10index < roiDataLength ? roiDataArrayByte[w10index] & 0xffff : 0;
                w11 = w11index < roiDataLength ? roiDataArrayByte[w11index] & 0xffff : 0;
                if (w00 == 0 && w01 == 0 && w10 == 0 && w11 == 0) {
                    return destinationNoData;
                }
                break;
            case DataBuffer.TYPE_SHORT:
            case DataBuffer.TYPE_INT:
                w00 = w00index < roiDataLength ? roiDataArrayByte[w00index] : 0;
                w01 = w01index < roiDataLength ? roiDataArrayByte[w01index] : 0;
                w10 = w10index < roiDataLength ? roiDataArrayByte[w10index] : 0;
                w11 = w11index < roiDataLength ? roiDataArrayByte[w11index] : 0;
                if (w00 == 0 && w01 == 0 && w10 == 0 && w11 == 0) {
                    return destinationNoData;
                }
                break;
            case DataBuffer.TYPE_FLOAT:
                w00f = w00index < roiDataLength ? roiDataArrayByte[w00index] : 0;
                w01f = w01index < roiDataLength ? roiDataArrayByte[w01index] : 0;
                w10f = w10index < roiDataLength ? roiDataArrayByte[w10index] : 0;
                w11f = w11index < roiDataLength ? roiDataArrayByte[w11index] : 0;
                if (w00f == 0 && w01f == 0 && w10f == 0 && w11f == 0) {
                    return destinationNoData;
                }
                break;
            case DataBuffer.TYPE_DOUBLE:
                w00d = w00index < roiDataLength ? roiDataArrayByte[w00index] : 0;
                w01d = w01index < roiDataLength ? roiDataArrayByte[w01index] : 0;
                w10d = w10index < roiDataLength ? roiDataArrayByte[w10index] : 0;
                w11d = w11index < roiDataLength ? roiDataArrayByte[w11index] : 0;
                if (w00d == 0 && w01d == 0 && w10d == 0 && w11d == 0) {
                    return destinationNoData;
                }
                break;
            default:
                break;
            }

            // If ROI accessor is not present but an image ROI has been saved, this ROI is used for checking if
            // all the surrounding pixel belongs to the ROI.
        } else if (roiBounds != null) {
            // Central pixel positions
            int x0 = src.getX() + posXlow / srcPixelStride;
            int y0 = src.getY() + (posYlow - srcBandOffset) / srcScanLineStride;
            // ROI control
            if (roiBounds.contains(x0, y0)) {
                switch (dataType) {
                case DataBuffer.TYPE_BYTE:
                    w00 = roiIter.getSample(x0, y0, 0)& 0xFF;
                    w01 = roiIter.getSample(x0 + 1, y0, 0)& 0xFF;
                    w10 = roiIter.getSample(x0, y0 + 1, 0)& 0xFF;
                    w11 = roiIter.getSample(x0 + 1, y0 + 1, 0)& 0xFF;
                    if (w00 == 0 && w01 == 0 && w10 == 0 && w11 == 0) {
                        return destinationNoData;
                    }
                    break;
                case DataBuffer.TYPE_USHORT:
                    w00 = roiIter.getSample(x0, y0, 0)& 0xFFFF;
                    w01 = roiIter.getSample(x0 + 1, y0, 0)& 0xFFFF;
                    w10 = roiIter.getSample(x0, y0 + 1, 0)& 0xFFFF;
                    w11 = roiIter.getSample(x0 + 1, y0 + 1, 0)& 0xFFFF;
                    if (w00 == 0 && w01 == 0 && w10 == 0 && w11 == 0) {
                        return destinationNoData;
                    }
                    break;
                case DataBuffer.TYPE_SHORT:
                case DataBuffer.TYPE_INT:
                    w00 = roiIter.getSample(x0, y0, 0);
                    w01 = roiIter.getSample(x0 + 1, y0, 0);
                    w10 = roiIter.getSample(x0, y0 + 1, 0);
                    w11 = roiIter.getSample(x0 + 1, y0 + 1, 0);
                    if (w00 == 0 && w01 == 0 && w10 == 0 && w11 == 0) {
                        return destinationNoData;
                    }
                    break;
                case DataBuffer.TYPE_FLOAT:
                    w00f = roiIter.getSample(x0, y0, 0);
                    w01f = roiIter.getSample(x0 + 1, y0, 0);
                    w10f = roiIter.getSample(x0, y0 + 1, 0);
                    w11f = roiIter.getSample(x0 + 1, y0 + 1, 0);
                    if (w00f == 0 && w01f == 0 && w10f == 0 && w11f == 0) {
                        return destinationNoData;
                    }
                    break;
                case DataBuffer.TYPE_DOUBLE:
                    w00d = roiIter.getSample(x0, y0, 0);
                    w01d = roiIter.getSample(x0 + 1, y0, 0);
                    w10d = roiIter.getSample(x0, y0 + 1, 0);
                    w11d = roiIter.getSample(x0 + 1, y0 + 1, 0);
                    if (w00d == 0 && w01d == 0 && w10d == 0 && w11d == 0) {
                        return destinationNoData;
                    }
                    break;
                default:
                    break;
                }
            } else {
                return destinationNoData;
            }
        }

        w00 = 1;
        w01 = 1;
        w10 = 1;
        w11 = 1;
        
        w00f = 1f;
        w01f = 1f;
        w10f = 1f;
        w11f = 1f;
        
        w00d = 1d;
        w01d = 1d;
        w10d = 1d;
        w11d = 1d;
        
        // No Data Control for the 4 selected pixels.If any of these 4 pixel is NO DATA,
        // his related weight is set to 0, else it is leaved unchanged.
        if (noDataRange != null) {
            switch (dataType) {
            case DataBuffer.TYPE_BYTE:
                Range<Byte> rangeB = ((Range<Byte>) noDataRange);
                if (rangeB.contains((byte) s00)) {
                    w00 *= 0;
                }
                if (rangeB.contains((byte) s01)) {
                    w01 *= 0;
                }
                if (rangeB.contains((byte) s10)) {
                    w10 *= 0;
                }
                if (rangeB.contains((byte) s11)) {
                    w11 *= 0;
                }
                if (w00 == 0 && w01 == 0 && w10 == 0 && w11 == 0) {
                    return destinationNoData;
                }
                break;
            case DataBuffer.TYPE_USHORT:
            case DataBuffer.TYPE_SHORT:
                Range<Short> rangeS = ((Range<Short>) noDataRange);
                if (rangeS.contains((short) s00)) {
                    w00 *= 0;
                }
                if (rangeS.contains((short) s01)) {
                    w01 *= 0;
                }
                if (rangeS.contains((short) s10)) {
                    w10 *= 0;
                }
                if (rangeS.contains((short) s11)) {
                    w11 *= 0;
                }
                if (w00 == 0 && w01 == 0 && w10 == 0 && w11 == 0) {
                    return destinationNoData;
                }
                break;
            case DataBuffer.TYPE_INT:
                Range<Integer> rangeI = ((Range<Integer>) noDataRange);
                if (rangeI.contains(s00)) {
                    w00 *= 0;
                }
                if (rangeI.contains(s01)) {
                    w01 *= 0;
                }
                if (rangeI.contains(s10)) {
                    w10 *= 0;
                }
                if (rangeI.contains(s11)) {
                    w11 *= 0;
                }
                if (w00 == 0 && w01 == 0 && w10 == 0 && w11 == 0) {
                    return destinationNoData;
                }
                break;
            case DataBuffer.TYPE_FLOAT:
                Range<Float> rangeF = ((Range<Float>) noDataRange);
                // This code is used for checking if No Data value is Double.NaN, 
                // Double.POSITIVE_INFINITY or Double.NEGATIVE_INFINITY 
                if(isNegativeInf||isPositiveInf||isRangeNaN){
                	// If so no data range is not used
                	if(testNaNorInfinity(dataType,0,s00f)){
                		w00f *= 0;
                	}
                // Otherwise the control is performed with the help of the noDataRange object
                }else if (rangeF.contains(s00f)) {                	
                	w00f *= 0;
                }
                if(isNegativeInf||isPositiveInf||isRangeNaN){
                	if(testNaNorInfinity(dataType,0,s01f)){
                		w01f *= 0;
                	} 
                }else if (rangeF.contains(s01f)) {                	
                	w01f *= 0;
                }
                if(isNegativeInf||isPositiveInf||isRangeNaN){
                	if(testNaNorInfinity(dataType,0,s10f)){
                		w10f *= 0;
                	} 
                }else if (rangeF.contains(s10f)) {                	
                	w10f *= 0;
                }
                if(isNegativeInf||isPositiveInf||isRangeNaN){
                	if(testNaNorInfinity(dataType,0,s11f)){
                		w11f *= 0;
                	}               	
                }else if (rangeF.contains(s11f)) {                	
                	w11f *= 0;
                }
                if (w00f == 0 && w01f == 0 && w10f == 0 && w11f == 0) {
                    return destinationNoData;
                }
                break;
            case DataBuffer.TYPE_DOUBLE:
                Range<Double> rangeD = ((Range<Double>) noDataRange);
                // This code is used for checking if No Data value is Double.NaN, 
                // Double.POSITIVE_INFINITY or Double.NEGATIVE_INFINITY 
                if(isNegativeInf||isPositiveInf||isRangeNaN){
                	// If so no data range is not used
                	if(testNaNorInfinity(dataType,s00d,0)){
                		w00d *= 0;
                	} 
                // Otherwise the control is performed with the help of the noDataRange object	
                }else if (rangeD.contains(s00d)) {                	
                	w00d *= 0;
                }
                if(isNegativeInf||isPositiveInf||isRangeNaN){
                	if(testNaNorInfinity(dataType,s01d,0)){
                		w01d *= 0;
                	} 
                }else if (rangeD.contains(s01d)) {                	
                	w01d *= 0;
                }
                if(isNegativeInf||isPositiveInf||isRangeNaN){
                	if(testNaNorInfinity(dataType,s10d,0)){
                		w10d *= 0;
                	} 
                }else if (rangeD.contains(s10d)) {                	
                	w10d *= 0;
                }
                if(isNegativeInf||isPositiveInf||isRangeNaN){
                	if(testNaNorInfinity(dataType,s11d,0)){
                		w11d *= 0;
                	}               	
                }else if (rangeD.contains(s11d)) {                	
                	w11d *= 0;
                }
                if (w00d == 0 && w01d == 0 && w10d == 0 && w11d == 0) {
                    return destinationNoData;
                }
                break;
            default:
                break;
            }
        }
        // Bilinear Interpolation calculation.
        Number s = null;
        switch (dataType) {
        case DataBuffer.TYPE_BYTE:
        case DataBuffer.TYPE_USHORT:
        case DataBuffer.TYPE_SHORT:
        case DataBuffer.TYPE_INT:
            s = computeValue(s00, s01, s10, s11, w00, w01, w10, w11, xfrac, yfrac);
            break;
        case DataBuffer.TYPE_FLOAT:
            s = computeValueDouble(s00f, s01f, s10f, s11f, w00f, w01f, w10f, w11f, xfracf, yfracf,
                    dataType);
            break;
        case DataBuffer.TYPE_DOUBLE:
            s = computeValueDouble(s00d, s01d, s10d, s11d, w00d, w01d, w10d, w11d, xfracd, yfracd,
                    dataType);
            break;
        default:
            break;
        }

        return s;
    }

    /** This method performs a bilinear interpolation of a pixel inside a binary image. */
    public int interpolateBinary(int xNextBitNo, Number[] sourceData, int xfrac, int yfrac,
            int sourceYOffset, int sourceScanlineStride, int[] coordinates, int[] roiDataArray, int roiYOffset, int roiScanlineStride) {

        

        // Shift inside the pixel element, to the adjacent bit.
        int xNextShiftNo = 0;
        // 4 surrounding pixel initialization
        int s00 = 0;
        int s01 = 0;
        int s10 = 0;
        int s11 = 0;
        // Shift to the selected pixel
        int sshift = 0;
        // Calculates the bit number of the selected pixel's position
        int sbitnum = xNextBitNo - 1;
        
        int w00index = 0;
        int w01index = 0;
        int w10index = 0;
        int w11index = 0;

        int w00 = 1;
        int w01 = 1;
        int w10 = 1;
        int w11 = 1;
 
         // If an image ROI has been saved, this ROI is used for checking if
         // all the surrounding pixel belongs to the ROI.   
        if (coordinates != null && roiBounds != null && !useROIAccessor) {            
            // Central pixel positions
            int x0 = coordinates[0];
            int y0 = coordinates[1];
            // ROI control
            if (roiBounds.contains(x0, y0)) {
                w00 = roiIter.getSample(x0, y0, 0) & 0x1;
                w01 = roiIter.getSample(x0 + 1, y0, 0) & 0x1;
                w10 = roiIter.getSample(x0, y0 + 1, 0) & 0x1;
                w11 = roiIter.getSample(x0 + 1, y0 + 1, 0) & 0x1;
            } else {
                return black;
            }
        }

        // Calculates the interpolation for every type of data that allows binary images.
        switch (dataType) {
        case DataBuffer.TYPE_BYTE:
            // This value is used for searching the selected pixel inside the element.
            sshift = 7 - (sbitnum & 7);
            // Conversion from bit to Byte for searching the element in which the selected pixel is found.
            int sbytenum = sbitnum >> 3;
            // Conversion from bit to Byte for searching the element of the pixel adjacent to the selected one.
            int xNextByteNo = xNextBitNo >> 3;
            // This value is used for searching the adjacent pixel inside the element.
            xNextShiftNo = 7 - (xNextBitNo & 7);
            // Searching of the 4 pixels surrounding the selected one.
            s00 = (sourceData[sourceYOffset + sbytenum].byteValue() >> sshift) & 0x01;
            s01 = (sourceData[sourceYOffset + xNextByteNo].byteValue() >> xNextShiftNo) & 0x01;
            s10 = (sourceData[sourceYOffset + sourceScanlineStride + sbytenum].byteValue() >> sshift) & 0x01;
            s11 = (sourceData[sourceYOffset + sourceScanlineStride + xNextByteNo].byteValue() >> xNextShiftNo) & 0x01;

            if(useROIAccessor){
                int roiDataLength=roiDataArray.length;
                w00index = roiYOffset + sbytenum;
                
                if(w00index>roiDataLength || (roiDataArray[w00index]>> sshift & 0x01)==0){
                	return black;
                }
                
                w01index = roiYOffset + xNextByteNo;
                w10index = roiYOffset + roiScanlineStride + sbytenum;
                w11index = roiYOffset + roiScanlineStride + xNextByteNo;
                
                w00 = w00index < roiDataLength ? roiDataArray[w00index] >> sshift & 0x01: 0;
                w01 = w01index < roiDataLength ? roiDataArray[w01index] >> xNextShiftNo & 0x01: 0;
                w10 = w10index < roiDataLength ? roiDataArray[w10index] >> sshift & 0x01: 0;
                w11 = w11index < roiDataLength ? roiDataArray[w11index] >> xNextShiftNo & 0x01: 0;
            }
            break;
        case DataBuffer.TYPE_USHORT:
        case DataBuffer.TYPE_SHORT:
            // Same operations like the ones above but the step is done with short and not byte
            int xNextShortNo = xNextBitNo >> 4;
            xNextShiftNo = 15 - (xNextBitNo & 15);
            int sshortnum = sbitnum >> 4;
            sshift = 15 - (sbitnum & 15);

            s00 = (sourceData[sourceYOffset + sshortnum].shortValue() >> sshift) & 0x01;
            s01 = (sourceData[sourceYOffset + xNextShortNo].shortValue() >> xNextShiftNo) & 0x01;
            s10 = (sourceData[sourceYOffset + sourceScanlineStride + sshortnum].shortValue() >> sshift) & 0x01;
            s11 = (sourceData[sourceYOffset + sourceScanlineStride + xNextShortNo].shortValue() >> xNextShiftNo) & 0x01;

            if(useROIAccessor){
                int roiDataLength=roiDataArray.length;
                w00index = roiYOffset + sshortnum;
                
                if(w00index>roiDataLength || (roiDataArray[w00index]>> sshift & 0x01)==0){
                	return black;
                }
                
                w01index = roiYOffset + xNextShortNo;
                w10index = roiYOffset + roiScanlineStride + sshortnum;
                w11index = roiYOffset + roiScanlineStride + xNextShortNo;
                
                w00 = w00index < roiDataLength ? roiDataArray[w00index] >> sshift & 0x01: 0;
                w01 = w01index < roiDataLength ? roiDataArray[w01index] >> xNextShiftNo & 0x01: 0;
                w10 = w10index < roiDataLength ? roiDataArray[w10index] >> sshift & 0x01: 0;
                w11 = w11index < roiDataLength ? roiDataArray[w11index] >> xNextShiftNo & 0x01: 0;
            }
            
            break;
        case DataBuffer.TYPE_INT:
            // Same operations like the ones above but the step is done with integers and not short
            int xNextIntNo = xNextBitNo >> 5;
            xNextShiftNo = 31 - (xNextBitNo & 31);
            int sintnum = sbitnum >> 5;
            sshift = 31 - (sbitnum & 31);

            s00 = (sourceData[sourceYOffset + sintnum].intValue() >> sshift) & 0x01;
            s01 = (sourceData[sourceYOffset + xNextIntNo].intValue() >> xNextShiftNo) & 0x01;
            s10 = (sourceData[sourceYOffset + sourceScanlineStride + sintnum].intValue() >> sshift) & 0x01;
            s11 = (sourceData[sourceYOffset + sourceScanlineStride + xNextIntNo].intValue() >> xNextShiftNo) & 0x01;
            
            if(useROIAccessor){
                int roiDataLength=roiDataArray.length;
                w00index = roiYOffset + sintnum;
                
                if(w00index>roiDataLength || (roiDataArray[w00index]>> sshift & 0x01)==0){
                	return black;
                }
                
                w01index = roiYOffset + xNextIntNo;
                w10index = roiYOffset + roiScanlineStride + sintnum;
                w11index = roiYOffset + roiScanlineStride + xNextIntNo;
                
                w00 = w00index < roiDataLength ? roiDataArray[w00index] >> sshift & 0x01: 0;
                w01 = w01index < roiDataLength ? roiDataArray[w01index] >> xNextShiftNo & 0x01: 0;
                w10 = w10index < roiDataLength ? roiDataArray[w10index] >> sshift & 0x01: 0;
                w11 = w11index < roiDataLength ? roiDataArray[w11index] >> xNextShiftNo & 0x01: 0;
            }
            
            break;
        default:
            break;
        }

        int sumWeight= w00+w01+w10+w11;
        if(sumWeight==0){
        	return black;
        }
        
        // Bilinear Interpolation
        int s = computeValue(s00, s01, s10, s11, w00, w01, w10, w11, xfrac, yfrac);
        return s;
    }

    /* Private method for checking if the Range contains NaN, Positive Infinity or Negative Infinity*/
    private boolean testNaNorInfinity(int dataType, double valued , float valuef){
    	boolean checkData=false;
    	switch(dataType){
    	case DataBuffer.TYPE_FLOAT:
    		if(isRangeNaN){
    			checkData=Float.isNaN(valuef);
    		}else if(isPositiveInf){
    			checkData= valuef==Float.POSITIVE_INFINITY;
    		}else if(isNegativeInf){
    			checkData= valuef==Float.NEGATIVE_INFINITY;
    		}	
    		break;
    	case DataBuffer.TYPE_DOUBLE:
    		if(isRangeNaN){
    			checkData=Double.isNaN(valued);
    		}else if(isPositiveInf){
    			checkData= valued==Double.POSITIVE_INFINITY;
    		}else if(isNegativeInf){
    			checkData= valued==Double.NEGATIVE_INFINITY;
    		}	
    		break;
    		default:
    			throw new IllegalArgumentException("Wrong control on the selected dataType");
    	}
    	
		return checkData;
    	
    }
    
    
    
    /* Private method for calculate bilinear interpolation for byte, short/ushort, integer dataType */
    private  int computeValue(int s00, int s01, int s10, int s11, int w00, int w01, int w10,
            int w11, int xfrac, int yfrac) {
        int s0 = 0;
        int s1 = 0;
        int s = 0;

        long s0L = 0;
        long s1L = 0;

        //Complementary values of the fractional part
        int xfracCompl= (int) Math.pow(2, subsampleBits) - xfrac;
        int yfracCompl= (int) Math.pow(2, subsampleBits) - yfrac;
        
        int shift = 29 - subsampleBits;
        // For Integer value is possible that a bitshift of "subsampleBits" could shift over the integer bit number
        // so the samples, in this case, are expanded to Long.
        boolean s0Long = ((s00 | s10) >>> shift == 0);
        boolean s1Long = ((s01 | s11) >>> shift == 0);
        // If all the weight are 0 the destination NO DATA is returned
        if (w00 == 0 && w01 == 0 && w10 == 0 && w11 == 0) {
            return (int) destinationNoData;
        }
        // Otherwise all the possible weight combination are checked
        if (w00 == 0 || w01 == 0 || w10 == 0 || w11 == 0) {
            // For integers is even considered the case when the integers are expanded to longs
            if (dataType == DataBuffer.TYPE_INT) {

                if (w00 == 0 && w01 == 0) {

                    s0L = 0;
                } else if (w00 == 0) { // w01 = 1
                    if (s1Long) {
                        s0L = -s01*xfracCompl + (s01 << subsampleBits);
                    } else {
                        s0L = -s01*xfracCompl + ((long) s01 << subsampleBits);
                    }
                } else if (w01 == 0) {// w00 = 1
                    if (s0Long) {
                        s0L = -s00*xfrac + (s00 << subsampleBits);
                    } else {
                        s0L = -s00*xfrac + ((long) s00 << subsampleBits);
                    }
                } else {// w00 = 1 & W01 = 1
                    if (s0Long) {
                        if (s1Long) {
                            s0L = (s01 - s00) * xfrac + (s00 << subsampleBits);
                        } else {
                            s0L = ((long) s01 - s00) * xfrac + (s00 << subsampleBits);
                        }
                    } else {
                        s0L = ((long) s01 - s00) * xfrac + ((long) s00 << subsampleBits);
                    }
                }

                // lower value

                if (w10 == 0 && w11 == 0) {
                    s1L = 0;
                } else if (w10 == 0) { // w11 = 1
                    if (s1Long) {
                        s1L = -s11*xfracCompl + (s11 << subsampleBits);
                    } else {
                        s1L = -s11*xfracCompl + ((long) s11 << subsampleBits);
                    }
                } else if (w11 == 0) { // w10 = 1
                    if (s0Long) {// - (s10 * xfrac); //s10;
                        s1L = -s10*xfrac + (s10 << subsampleBits);
                    } else {
                        s1L = -s10*xfrac + ((long) s10 << subsampleBits);
                    }
                } else {
                    if (s0Long) {
                        if (s1Long) {
                            s1L = (s11 - s10) * xfrac + (s10 << subsampleBits);
                        } else {
                            s1L = ((long) s11 - s10) * xfrac + (s10 << subsampleBits);
                        }
                    } else {
                        s1L = ((long) s11 - s10) * xfrac + ((long) s10 << subsampleBits);
                    }
                }
                if (w00 == 0 && w01 == 0) {
                    s = (int) (-s1L*yfracCompl + ((s1L << subsampleBits) + round2) >> shift2);
                } else {
                    if (w10 == 0 && w11 == 0) {
                        s = (int) (-s0L*yfrac + ((s0L << subsampleBits) + round2) >> shift2);
                    } else {
                        s = (int) (((s1L - s0L) * yfrac + (s0L << subsampleBits) + round2) >> shift2);
                    }
                }

            } else {
                // Interpolation for type byte, ushort, short
                if (w00 == 0 && w01 == 0) {
                    s0 = 0;
                } else if (w00 == 0) { // w01 = 1
                    s0 = -s01*xfracCompl + (s01 << subsampleBits);
                } else if (w01 == 0) {// w00 = 1
                    s0 = -s00*xfrac + (s00 << subsampleBits);// s00;
                } else {// w00 = 1 & W01 = 1
                    s0 = (s01 - s00) * xfrac + (s00 << subsampleBits);
                }

                // lower value

                if (w10 == 0 && w11 == 0) {
                    s1 = 0;
                } else if (w10 == 0) { // w11 = 1
                    s1 = -s11*xfracCompl + (s11 << subsampleBits);
                } else if (w11 == 0) { // w10 = 1
                    s1 = -s10*xfrac + (s10 << subsampleBits);// - (s10 * xfrac); //s10;
                } else {
                    s1 = (s11 - s10) * xfrac + (s10 << subsampleBits);
                }

                if (w00 == 0 && w01 == 0) {
                    s = (-s1*yfracCompl + (s1 << subsampleBits) + round2) >> shift2;
                } else {
                    if (w10 == 0 && w11 == 0) {
                        s = (-s0*yfrac + (s0 << subsampleBits) + round2) >> shift2;
                    } else {
                        s = ((s1 - s0) * yfrac + (s0 << subsampleBits) + round2) >> shift2;
                    }
                }

            }
        } else {
            // Perform the bilinear interpolation
            if (dataType == DataBuffer.TYPE_INT) {
                if (s0Long) {
                    if (s1Long) {
                        s0 = (s01 - s00) * xfrac + (s00 << subsampleBits);
                        s1 = (s11 - s10) * xfrac + (s10 << subsampleBits);
                        s = ((s1 - s0) * yfrac + (s0 << subsampleBits) + round2) >> shift2;
                    } else {
                        s0L = ((long) s01 - s00) * xfrac + (s00 << subsampleBits);
                        s1L = ((long) s11 - s10) * xfrac + (s10 << subsampleBits);
                        s = (int) (((s1L - s0L) * yfrac + (s0L << subsampleBits) + round2) >> shift2);
                    }
                } else {
                    s0L = ((long) s01 - s00) * xfrac + ((long) s00 << subsampleBits);
                    s1L = ((long) s11 - s10) * xfrac + ((long) s10 << subsampleBits);
                    s = (int) (((s1L - s0L) * yfrac + (s0L << subsampleBits) + round2) >> shift2);
                }
            } else {
                s0 = (s01 - s00) * xfrac + (s00 << subsampleBits);
                s1 = (s11 - s10) * xfrac + (s10 << subsampleBits);
                s = ((s1 - s0) * yfrac + (s0 << subsampleBits) + round2) >> shift2;
            }
        }

        switch (dataType) {
        case DataBuffer.TYPE_BYTE:
            s = (byte) s & 0xff;
            break;
        case DataBuffer.TYPE_USHORT:
            s = (short) s & 0xffff;
            break;
        case DataBuffer.TYPE_SHORT:
            s = (short) s;
            break;
        default:
            break;
        }
        return s;
    }
    /* Private method for calculate bilinear interpolation for float/double dataType */
    private Number computeValueDouble(double s00, double s01, double s10, double s11, double w00,
            double w01, double w10, double w11, double xfrac, double yfrac, int dataType) {

        double s0 = 0;
        double s1 = 0;
        double s = 0;

        //Complementary values of the fractional part
        double xfracCompl= 1 - xfrac;
        double yfracCompl= 1 - yfrac;
        
        if (w00 == 0 && w01 == 0 && w10 == 0 && w11 == 0) {
            return destinationNoData;
        }

        if (w00 == 0 || w01 == 0 || w10 == 0 || w11 == 0) {

            if (w00 == 0 && w01 == 0) {
                s0 = 0;
            } else if (w00 == 0) { // w01 = 1
                s0 = s01*xfrac;
            } else if (w01 == 0) {// w00 = 1
                s0 = s00*xfracCompl;// s00;
            } else {// w00 = 1 & W01 = 1
                s0 = (s01 - s00) * xfrac + s00;
            }

            // lower value

            if (w10 == 0 && w11 == 0) {
                s1 = 0;
            } else if (w10 == 0) { // w11 = 1
                s1 = s11*xfrac;
            } else if (w11 == 0) { // w10 = 1
                s1 = s10*xfracCompl;// - (s10 * xfrac); //s10;
            } else {
                s1 = (s11 - s10) * xfrac + s10;
            }

            if (w00 == 0 && w01 == 0) {
                s = s1*yfrac;
            } else {
                if (w10 == 0 && w11 == 0) {
                    s = s0*yfracCompl;
                } else {
                    s = (s1 - s0) * yfrac + s0;
                }
            }
        } else {

            // Perform the bilinear interpolation because all the weight are not 0.
            s0 = (s01 - s00) * xfrac + s00;
            s1 = (s11 - s10) * xfrac + s10;
            s = (s1 - s0) * yfrac + s0;
        }

        // Simple conversion for float dataType.
        if (dataType == DataBuffer.TYPE_FLOAT) {
            return (float) s;
        } else {
            return s;
        }

    }

}