package it.geosolutions.jaiext.interpolators;

import java.awt.Rectangle;
import java.awt.image.DataBuffer;
import javax.media.jai.Interpolation;
import javax.media.jai.RasterAccessor;
import javax.media.jai.iterator.RandomIter;

import org.jaitools.numeric.Range;

public class InterpolationNearest extends Interpolation {

    /** serialVersionUID */
    private static final long serialVersionUID = -6994369085300227735L;

    /** Boolean for checking if the ROI Accessor must be used by the interpolator */
    private boolean useROIAccessor;

    /** Range of NO DATA values to be checked */
    private Range noDataRange;

    /**
     * Interpolation Nearest instance used only with the ScaleOpImage constructor for setting the interpolation type as Nearest-neighbor
     * */
    private InterpolationNearest interpNearest;

    /**
     * Destination NO DATA value used when the image pixel is outside of the ROI or is contained in the NO DATA range
     * */
    private double destinationNoData;

    /** ROI bounds used for checking the position of the pixel */
    private Rectangle roiBounds;

    /** Random interator used for navigate inside the ROI and searching the pixel */
    private RandomIter roiIter;

    /** Image data Type */
    private int dataType;

    /** This value is the destination NO DATA values for binary images */
    private int black;

    /** Boolean for checking if No data is NaN*/
    private boolean isRangeNaN=false;
    
    /** Boolean for checking if No data is Positive Infinity*/
    private boolean isPositiveInf=false;
    
    /** Boolean for checking if No data is Negative Infinity*/
    private boolean isNegativeInf=false;
    
    
    // Method overriding. Performs the default nearest-neighbor interpolation without NO DATA or ROI control.
    @Override
    public int interpolateH(int[] samples, int arg1) {
        return samples[0];
    }

    @Override
    public float interpolateH(float[] samples, float arg1) {
        return samples[0];
    }

    @Override
    public double interpolateH(double[] samples, float arg1) {
        return samples[0];
    }

    /**
     * Simple interpolator object used for Nearest-Neighbor interpolation. On construction it is possible to set a range for no data values that will
     * be considered in the interpolation method.
     */
    public InterpolationNearest(Range noDataRange, boolean useROIAccessor,
            double destinationNoData, int dataType) {
        super(1, 1, 0, 0, 0, 0, 0, 0);
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

    public void setROIdata(Rectangle roiBounds, RandomIter roiIter) {
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

    // method for calculating the nearest-neighbor interpolation (no Binary data).
    public Number interpolate(RasterAccessor src, int bandIndex, int dnumband, int posx,
            int posy, Integer yROIValue, RasterAccessor roiAccessor, boolean setNoData) {
        // src data and destination data
        Number destData = null;
        // the destination data is set equal to the source data but could change if the ROI is present. If
        // it is no data, destination no data value is returned.
        switch (dataType) {
        case DataBuffer.TYPE_BYTE:
            byte srcDataByte = src.getByteDataArray(bandIndex)[posx + posy];
            if ((noDataRange != null && ((Range<Byte>) noDataRange).contains(srcDataByte))
                    || setNoData) {
                return destinationNoData;
            }
            destData = srcDataByte;
            break;
        case DataBuffer.TYPE_USHORT:
        case DataBuffer.TYPE_SHORT:
            short srcDataShort = src.getShortDataArray(bandIndex)[posx + posy];
            if ((noDataRange != null && ((Range<Short>) noDataRange).contains(srcDataShort))
                    || setNoData) {
                return destinationNoData;
            }
            destData = srcDataShort;
            break;
        case DataBuffer.TYPE_INT:
            int srcDataInt = src.getIntDataArray(bandIndex)[posx + posy];
            if ((noDataRange != null && ((Range<Integer>) noDataRange).contains(srcDataInt))
                    || setNoData) {
                return destinationNoData;
            }
            destData = srcDataInt;
            break;
        case DataBuffer.TYPE_FLOAT:
            float srcDataFloat = src.getFloatDataArray(bandIndex)[posx + posy];
            // Additional control due to the fact that Range<T> can't contain NaN, Positive Inf and Negative Inf
            if(isRangeNaN && Float.isNaN(srcDataFloat)){
            	return destinationNoData;
            }else if(isPositiveInf && srcDataFloat==Float.POSITIVE_INFINITY){
            	return destinationNoData;
            }else if(isNegativeInf && srcDataFloat==Float.NEGATIVE_INFINITY){
            	return destinationNoData;
            }else if ((noDataRange != null && ((Range<Float>) noDataRange).contains(srcDataFloat))
                    || setNoData) {
                return destinationNoData;
            }
            destData = srcDataFloat;
            break;
        case DataBuffer.TYPE_DOUBLE:
            double srcDataDouble = src.getDoubleDataArray(bandIndex)[posx + posy];
            if(isRangeNaN && Double.isNaN(srcDataDouble)){
            	return destinationNoData;
            }else if(isPositiveInf && srcDataDouble==Double.POSITIVE_INFINITY){
            	return destinationNoData;
            }else if(isNegativeInf && srcDataDouble==Double.NEGATIVE_INFINITY){
            	return destinationNoData;
            }else if ((noDataRange != null && ((Range<Double>) noDataRange).contains(srcDataDouble))
                    || setNoData) {
                return destinationNoData;
            }
            destData = srcDataDouble;
            break;
        default:
            break;
        }
        // If ROI accessor is used,source pixel is tested if is contained inside the ROI.
        if (useROIAccessor) {
            if (roiAccessor == null || yROIValue == null) {
                throw new IllegalArgumentException("ROI Accessor or ROI y value not found");
            }
            // Operations for taking the correct index pixel in roi array.
            int roiIndex = posx / dnumband + yROIValue;

            byte[] roiDataArray = roiAccessor.getByteDataArray(0);

            if (roiIndex < roiDataArray.length) {
                // if the ROI pixel value is 0 the value returned is NO DATA
                switch (dataType) {
                case DataBuffer.TYPE_BYTE:

                    byte valueROIByte = (byte) (roiDataArray[roiIndex] & 0xff);
                    if (valueROIByte != 0) {
                        return destData;
                    } else {
                        return destinationNoData;
                    }
                case DataBuffer.TYPE_USHORT:
                    short valueROIUShort = (short) (roiDataArray[roiIndex] & 0xffff);
                    if (valueROIUShort != 0) {
                        return destData;
                    } else {
                        return destinationNoData;
                    }
                case DataBuffer.TYPE_SHORT:
                case DataBuffer.TYPE_INT:
                case DataBuffer.TYPE_FLOAT:
                case DataBuffer.TYPE_DOUBLE:
                    double valueROI = roiDataArray[roiIndex];
                    if (valueROI != 0) {
                        return destData;
                    } else {
                        return destinationNoData;
                    }
                default:
                    break;
                }

            } else {
                return destinationNoData;
            }
            // If there is no ROI accessor but a ROI object is present, a test similar to that above is performed.
        } else if (roiBounds != null) {
            // Pixel position
            int x0 = src.getX() + posx / src.getPixelStride();
            int y0 = src.getY() + (posy - src.getBandOffset(bandIndex)) / src.getScanlineStride();
            // check if the roi pixel is inside the roi bounds
            if (!roiBounds.contains(x0, y0)) {
                return destinationNoData;
            } else {
                // if it is inside ROI bounds and the associated roi pixel is 1, the src pixel is returned.
                // Otherwise, destination NO DATA is returned.
                int wx = 0;
                switch (dataType) {
                case DataBuffer.TYPE_BYTE:
                    wx = roiIter.getSample(x0, y0, 0) & 0xFF;
                    break;
                case DataBuffer.TYPE_USHORT:
                    wx = roiIter.getSample(x0, y0, 0) & 0xFFFF;
                    break;
                case DataBuffer.TYPE_SHORT:
                case DataBuffer.TYPE_INT:
                case DataBuffer.TYPE_FLOAT:
                case DataBuffer.TYPE_DOUBLE:
                    wx = roiIter.getSample(x0, y0, 0);
                    break;
                default:
                    break;
                }

                final boolean insideROI = wx == 1;
                if (insideROI) {
                    return destData;
                } else {
                    return destinationNoData;
                }
            }
        }
        return destData;
    }

    // Interpolation operation for Binary images (coordinates are useful only if ROI is present)
    public int interpolateBinary(int xNextBitNo,Number[] sourceData, int sourceYOffset,int  sourceScanlineStride,
            int[] coordinates, int[] roiDataArray, int roiYOffset, int roiScanlineStride) {
        //pixel initialization
        int s = 0;
        // Shift to the selected pixel
        int sshift = 0;
        // Calculates the bit number of the selected pixel's position
        int sbitnum = xNextBitNo - 1;
        
        int w00index = 0;

        int w00 = 1;
 
         // If an image ROI has been saved, this ROI is used for checking if
         // all the surrounding pixel belongs to the ROI.   
        if (coordinates != null && roiBounds != null && !useROIAccessor) {            
            // Central pixel positions
            int x0 = coordinates[0];
            int y0 = coordinates[1];
            // ROI control
            if (roiBounds.contains(x0, y0)) {
                w00 = roiIter.getSample(x0, y0, 0) & 0x1;
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
            // Searching of the 4 pixels surrounding the selected one.
            s = (sourceData[sourceYOffset + sbytenum].byteValue() >> sshift) & 0x01;
            

            if(useROIAccessor){
                int roiDataLength=roiDataArray.length;
                w00index = roiYOffset + sbytenum;
                
                w00 *= w00index < roiDataLength ? roiDataArray[w00index] >> sshift & 0x01: 0;
            }
            break;
        case DataBuffer.TYPE_USHORT:
        case DataBuffer.TYPE_SHORT:
            // Same operations like the ones above but the step is done with short and not byte
            int sshortnum = sbitnum >> 4;
            sshift = 15 - (sbitnum & 15);

            s = (sourceData[sourceYOffset + sshortnum].shortValue() >> sshift) & 0x01;
            
            if(useROIAccessor){
                int roiDataLength=roiDataArray.length;
                w00index = roiYOffset + sshortnum;
                               
                w00 *= w00index < roiDataLength ? roiDataArray[w00index] >> sshift & 0x01: 0;
             }
            
            break;
        case DataBuffer.TYPE_INT:
            // Same operations like the ones above but the step is done with integers and not short
            int sintnum = sbitnum >> 5;
            sshift = 31 - (sbitnum & 31);

            s = (sourceData[sourceYOffset + sintnum].intValue() >> sshift) & 0x01;           
            
            if(useROIAccessor){
                int roiDataLength=roiDataArray.length;
                w00index = roiYOffset + sintnum;                
                
                w00 *= w00index < roiDataLength ? roiDataArray[w00index] >> sshift & 0x01: 0;               
            }            
            break;
        default:
            break;
        }
        
        if(w00==0){
            return black;
        }
            
        
        return s;          
   }
}