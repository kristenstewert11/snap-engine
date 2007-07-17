package org.esa.beam.framework.gpf.operators.common;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.dataio.ProductWriter;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.AbstractOperator;
import org.esa.beam.framework.gpf.AbstractOperatorSpi;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Raster;
import org.esa.beam.framework.gpf.annotations.OperatorAlias;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.SubProgressMonitor;

/**
 * The <code>WriteProductOp</code> writes
 * the in-memory representation of its first input product.
 * <p/>
 * Configuration Elements:
 * <ul>
 * <li><b>filePath</b> the path of the product file to write to
 * <li><b>formatName</b> the format of the file
 * </ul>
 *
 * @author Maximilian Aulinger
 */
@OperatorAlias("ProductWriter")
public class WriteProductOp extends AbstractOperator {

    @TargetProduct
    private Product targetProduct;
    @SourceProduct(alias = "input")
    private Product sourceProduct;

    @Parameter
    private String filePath = null;
    @Parameter
    private String formatName = ProductIO.DEFAULT_FORMAT_NAME;

    private ProductWriter productWriter;
    private List<Band> bandsToWrite;
    private boolean productFileWritten;

    public WriteProductOp(OperatorSpi spi) {
        super(spi);
    }

    @Override
    public Product initialize(ProgressMonitor pm) throws OperatorException {
        targetProduct = sourceProduct;
        productWriter = ProductIO.getProductWriter(formatName);
        if (productWriter == null) {
            throw new OperatorException("No product writer for the '" + formatName + "' format available");
        }
        productWriter.setIncrementalMode(false);
        Band[] bands = targetProduct.getBands();
        bandsToWrite = new ArrayList<Band>(bands.length);
        for (Band band : bands) {
            if (productWriter.shouldWrite(band)) {
                bandsToWrite.add(band);
            }
        }
        return targetProduct;
    }


    @Override
    public void computeBand(Raster targetRaster, ProgressMonitor pm) throws OperatorException {
        if (!productFileWritten) {
            try {
                productWriter.writeProductNodes(targetProduct, filePath);
                productFileWritten = true;
            } catch (IOException e) {
                throw new OperatorException(e);
            }
        }
        if (targetRaster.getRasterDataNode() instanceof Band) {
            Band band = (Band) targetRaster.getRasterDataNode();
            pm.beginTask("Writing product...", 1);
            Rectangle rectangle = targetRaster.getRectangle();
            try {
            	ProductData dataBuffer = targetRaster.getDataBuffer();
                getRaster(band, rectangle, dataBuffer);                
                band.writeRasterData(rectangle.x, rectangle.y, rectangle.width, rectangle.height, dataBuffer, new SubProgressMonitor(pm, 1));
            } catch (IOException e) {
                Throwable cause = e.getCause();
                if (cause instanceof OperatorException) {
                    throw (OperatorException) cause;
                }
                throw new OperatorException(e);
            } finally {
                pm.done();
            }

        }
    }

    @Override
    public void dispose() {
        try {
            targetProduct.closeIO();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static class Spi extends AbstractOperatorSpi {
        public Spi() {
            super(WriteProductOp.class, "WriteProduct");
        }
    }
}