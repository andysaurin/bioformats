//
// BaseTiffReader.java
//

/*
OME Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-@year@ UW-Madison LOCI and Glencoe Software, Inc.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package loci.formats.in;

import java.io.IOException;

import loci.common.DateTools;
import loci.formats.FormatException;
import loci.formats.MetadataTools;
import loci.formats.meta.FilterMetadata;
import loci.formats.meta.MetadataStore;
import loci.formats.tiff.IFD;
import loci.formats.tiff.IFDList;
import loci.formats.tiff.PhotoInterp;
import loci.formats.tiff.TiffCompression;
import loci.formats.tiff.TiffRational;

/**
 * BaseTiffReader is the superclass for file format readers compatible with
 * or derived from the TIFF 6.0 file format.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/in/BaseTiffReader.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/src/loci/formats/in/BaseTiffReader.java">SVN</a></dd></dl>
 *
 * @author Curtis Rueden ctrueden at wisc.edu
 * @author Melissa Linkert linkert at wisc.edu
 */
public abstract class BaseTiffReader extends MinimalTiffReader {

  // -- Constants --

  public static final String[] DATE_FORMATS = {
    "yyyy:MM:dd HH:mm:ss",
    "dd/MM/yyyy HH:mm:ss.SS",
    "MM/dd/yyyy hh:mm:ss.SSS aa"
  };

  // -- Constructors --

  /** Constructs a new BaseTiffReader. */
  public BaseTiffReader(String name, String suffix) { super(name, suffix); }

  /** Constructs a new BaseTiffReader. */
  public BaseTiffReader(String name, String[] suffixes) {
    super(name, suffixes);
  }

  // -- Internal BaseTiffReader API methods --

  /** Populates the metadata hashtable and metadata store. */
  protected void initMetadata() throws FormatException, IOException {
    initStandardMetadata();
    initMetadataStore();
  }

  /**
   * Parses standard metadata.
   *
   * NOTE: Absolutely <b>no</b> calls to the metadata store should be made in
   * this method or methods that override this method. Data <b>will</b> be
   * overwritten if you do so.
   */
  protected void initStandardMetadata() throws FormatException, IOException {
    if (getMetadataOptions().getMetadataLevel() != MetadataLevel.ALL) {
      return;
    }
    IFD firstIFD = ifds.get(0);
    put("ImageWidth", firstIFD, IFD.IMAGE_WIDTH);
    put("ImageLength", firstIFD, IFD.IMAGE_LENGTH);
    put("BitsPerSample", firstIFD, IFD.BITS_PER_SAMPLE);

    // retrieve EXIF values, if available

    IFDList exifIFDs = tiffParser.getExifIFDs();
    if (exifIFDs.size() > 0) {
      IFD exif = exifIFDs.get(0);
      for (Integer key : exif.keySet()) {
        int k = key.intValue();
        addGlobalMeta(getExifTagName(k), exif.get(key));
      }
    }

    TiffCompression comp = firstIFD.getCompression();
    put("Compression", comp.getCodecName());

    PhotoInterp photo = firstIFD.getPhotometricInterpretation();
    String photoInterp = photo.getName();
    String metaDataPhotoInterp = photo.getMetadataType();
    put("PhotometricInterpretation", photoInterp);
    put("MetaDataPhotometricInterpretation", metaDataPhotoInterp);

    putInt("CellWidth", firstIFD, IFD.CELL_WIDTH);
    putInt("CellLength", firstIFD, IFD.CELL_LENGTH);

    int or = firstIFD.getIFDIntValue(IFD.ORIENTATION);

    // adjust the width and height if necessary
    if (or == 8) {
      put("ImageWidth", firstIFD, IFD.IMAGE_LENGTH);
      put("ImageLength", firstIFD, IFD.IMAGE_WIDTH);
    }

    String orientation = null;
    // there is no case 0
    switch (or) {
      case 1:
        orientation = "1st row -> top; 1st column -> left";
        break;
      case 2:
        orientation = "1st row -> top; 1st column -> right";
        break;
      case 3:
        orientation = "1st row -> bottom; 1st column -> right";
        break;
      case 4:
        orientation = "1st row -> bottom; 1st column -> left";
        break;
      case 5:
        orientation = "1st row -> left; 1st column -> top";
        break;
      case 6:
        orientation = "1st row -> right; 1st column -> top";
        break;
      case 7:
        orientation = "1st row -> right; 1st column -> bottom";
        break;
      case 8:
        orientation = "1st row -> left; 1st column -> bottom";
        break;
    }
    put("Orientation", orientation);
    putInt("SamplesPerPixel", firstIFD, IFD.SAMPLES_PER_PIXEL);

    put("Software", firstIFD, IFD.SOFTWARE);
    put("Instrument Make", firstIFD, IFD.MAKE);
    put("Instrument Model", firstIFD, IFD.MODEL);
    put("Document Name", firstIFD, IFD.DOCUMENT_NAME);
    put("DateTime", firstIFD, IFD.DATE_TIME);
    put("Artist", firstIFD, IFD.ARTIST);

    put("HostComputer", firstIFD, IFD.HOST_COMPUTER);
    put("Copyright", firstIFD, IFD.COPYRIGHT);

    put("NewSubfileType", firstIFD, IFD.NEW_SUBFILE_TYPE);

    int thresh = firstIFD.getIFDIntValue(IFD.THRESHHOLDING);
    String threshholding = null;
    switch (thresh) {
      case 1:
        threshholding = "No dithering or halftoning";
        break;
      case 2:
        threshholding = "Ordered dithering or halftoning";
        break;
      case 3:
        threshholding = "Randomized error diffusion";
        break;
    }
    put("Threshholding", threshholding);

    int fill = firstIFD.getIFDIntValue(IFD.FILL_ORDER);
    String fillOrder = null;
    switch (fill) {
      case 1:
        fillOrder = "Pixels with lower column values are stored " +
          "in the higher order bits of a byte";
        break;
      case 2:
        fillOrder = "Pixels with lower column values are stored " +
          "in the lower order bits of a byte";
        break;
    }
    put("FillOrder", fillOrder);

    putInt("Make", firstIFD, IFD.MAKE);
    putInt("Model", firstIFD, IFD.MODEL);
    putInt("MinSampleValue", firstIFD, IFD.MIN_SAMPLE_VALUE);
    putInt("MaxSampleValue", firstIFD, IFD.MAX_SAMPLE_VALUE);
    putInt("XResolution", firstIFD, IFD.X_RESOLUTION);
    putInt("YResolution", firstIFD, IFD.Y_RESOLUTION);

    int planar = firstIFD.getIFDIntValue(IFD.PLANAR_CONFIGURATION);
    String planarConfig = null;
    switch (planar) {
      case 1:
        planarConfig = "Chunky";
        break;
      case 2:
        planarConfig = "Planar";
        break;
    }
    put("PlanarConfiguration", planarConfig);

    putInt("XPosition", firstIFD, IFD.X_POSITION);
    putInt("YPosition", firstIFD, IFD.Y_POSITION);
    putInt("FreeOffsets", firstIFD, IFD.FREE_OFFSETS);
    putInt("FreeByteCounts", firstIFD, IFD.FREE_BYTE_COUNTS);
    putInt("GrayResponseUnit", firstIFD, IFD.GRAY_RESPONSE_UNIT);
    putInt("GrayResponseCurve", firstIFD, IFD.GRAY_RESPONSE_CURVE);
    putInt("T4Options", firstIFD, IFD.T4_OPTIONS);
    putInt("T6Options", firstIFD, IFD.T6_OPTIONS);

    int res = firstIFD.getIFDIntValue(IFD.RESOLUTION_UNIT);
    String resUnit = null;
    switch (res) {
      case 1:
        resUnit = "None";
        break;
      case 2:
        resUnit = "Inch";
        break;
      case 3:
        resUnit = "Centimeter";
        break;
    }
    put("ResolutionUnit", resUnit);

    putInt("PageNumber", firstIFD, IFD.PAGE_NUMBER);
    putInt("TransferFunction", firstIFD, IFD.TRANSFER_FUNCTION);

    int predict = firstIFD.getIFDIntValue(IFD.PREDICTOR);
    String predictor = null;
    switch (predict) {
      case 1:
        predictor = "No prediction scheme";
        break;
      case 2:
        predictor = "Horizontal differencing";
        break;
    }
    put("Predictor", predictor);

    putInt("WhitePoint", firstIFD, IFD.WHITE_POINT);
    putInt("PrimaryChromacities", firstIFD, IFD.PRIMARY_CHROMATICITIES);

    putInt("HalftoneHints", firstIFD, IFD.HALFTONE_HINTS);
    putInt("TileWidth", firstIFD, IFD.TILE_WIDTH);
    putInt("TileLength", firstIFD, IFD.TILE_LENGTH);
    putInt("TileOffsets", firstIFD, IFD.TILE_OFFSETS);
    putInt("TileByteCounts", firstIFD, IFD.TILE_BYTE_COUNTS);

    int ink = firstIFD.getIFDIntValue(IFD.INK_SET);
    String inkSet = null;
    switch (ink) {
      case 1:
        inkSet = "CMYK";
        break;
      case 2:
        inkSet = "Other";
        break;
    }
    put("InkSet", inkSet);

    putInt("InkNames", firstIFD, IFD.INK_NAMES);
    putInt("NumberOfInks", firstIFD, IFD.NUMBER_OF_INKS);
    putInt("DotRange", firstIFD, IFD.DOT_RANGE);
    put("TargetPrinter", firstIFD, IFD.TARGET_PRINTER);
    putInt("ExtraSamples", firstIFD, IFD.EXTRA_SAMPLES);

    int fmt = firstIFD.getIFDIntValue(IFD.SAMPLE_FORMAT);
    String sampleFormat = null;
    switch (fmt) {
      case 1:
        sampleFormat = "unsigned integer";
        break;
      case 2:
        sampleFormat = "two's complement signed integer";
        break;
      case 3:
        sampleFormat = "IEEE floating point";
        break;
      case 4:
        sampleFormat = "undefined";
        break;
    }
    put("SampleFormat", sampleFormat);

    putInt("SMinSampleValue", firstIFD, IFD.S_MIN_SAMPLE_VALUE);
    putInt("SMaxSampleValue", firstIFD, IFD.S_MAX_SAMPLE_VALUE);
    putInt("TransferRange", firstIFD, IFD.TRANSFER_RANGE);

    int jpeg = firstIFD.getIFDIntValue(IFD.JPEG_PROC);
    String jpegProc = null;
    switch (jpeg) {
      case 1:
        jpegProc = "baseline sequential process";
        break;
      case 14:
        jpegProc = "lossless process with Huffman coding";
        break;
    }
    put("JPEGProc", jpegProc);

    putInt("JPEGInterchangeFormat", firstIFD, IFD.JPEG_INTERCHANGE_FORMAT);
    putInt("JPEGRestartInterval", firstIFD, IFD.JPEG_RESTART_INTERVAL);

    putInt("JPEGLosslessPredictors", firstIFD, IFD.JPEG_LOSSLESS_PREDICTORS);
    putInt("JPEGPointTransforms", firstIFD, IFD.JPEG_POINT_TRANSFORMS);
    putInt("JPEGQTables", firstIFD, IFD.JPEG_Q_TABLES);
    putInt("JPEGDCTables", firstIFD, IFD.JPEG_DC_TABLES);
    putInt("JPEGACTables", firstIFD, IFD.JPEG_AC_TABLES);
    putInt("YCbCrCoefficients", firstIFD, IFD.Y_CB_CR_COEFFICIENTS);

    int ycbcr = firstIFD.getIFDIntValue(IFD.Y_CB_CR_SUB_SAMPLING);
    String subSampling = null;
    switch (ycbcr) {
      case 1:
        subSampling = "chroma image dimensions = luma image dimensions";
        break;
      case 2:
        subSampling = "chroma image dimensions are " +
          "half the luma image dimensions";
        break;
      case 4:
        subSampling = "chroma image dimensions are " +
          "1/4 the luma image dimensions";
        break;
    }
    put("YCbCrSubSampling", subSampling);

    putInt("YCbCrPositioning", firstIFD, IFD.Y_CB_CR_POSITIONING);
    putInt("ReferenceBlackWhite", firstIFD, IFD.REFERENCE_BLACK_WHITE);

    // bits per sample and number of channels
    int[] q = firstIFD.getBitsPerSample();
    int bps = q[0];
    int numC = q.length;

    // numC isn't set properly if we have an indexed color image, so we need
    // to reset it here

    if (photo == PhotoInterp.RGB_PALETTE || photo == PhotoInterp.CFA_ARRAY) {
      numC = 3;
    }

    put("BitsPerSample", bps);
    put("NumberOfChannels", numC);
  }

  /**
   * Populates the metadata store using the data parsed in
   * {@link #initStandardMetadata()} along with some further parsing done in
   * the method itself.
   *
   * All calls to the active <code>MetadataStore</code> should be made in this
   * method and <b>only</b> in this method. This is especially important for
   * sub-classes that override the getters for pixel set array size, etc.
   */
  protected void initMetadataStore() throws FormatException {
    LOGGER.info("Populating OME metadata");

    // the metadata store we're working with
    MetadataStore store =
      new FilterMetadata(getMetadataStore(), isMetadataFiltered());
    MetadataTools.populatePixels(store, this);

    IFD firstIFD = ifds.get(0);

    // format the creation date to ISO 8601

    String creationDate = getImageCreationDate();
    String date = DateTools.formatDate(creationDate, DATE_FORMATS);
    if (creationDate != null && date == null) {
      LOGGER.warn("unknown creation date format: {}", creationDate);
    }
    creationDate = date;

    // populate Image

    if (creationDate != null) {
      store.setImageCreationDate(creationDate, 0);
    }
    else {
       MetadataTools.setDefaultCreationDate(store, getCurrentFile(), 0);
    }

    if (getMetadataOptions().getMetadataLevel() == MetadataLevel.ALL) {
      // populate Experimenter
      String artist = firstIFD.getIFDTextValue(IFD.ARTIST);

      if (artist != null) {
        String firstName = null, lastName = null;
        int ndx = artist.indexOf(" ");
        if (ndx < 0) lastName = artist;
        else {
          firstName = artist.substring(0, ndx);
          lastName = artist.substring(ndx + 1);
        }
        String email = firstIFD.getIFDStringValue(IFD.HOST_COMPUTER);
        store.setExperimenterFirstName(firstName, 0);
        store.setExperimenterLastName(lastName, 0);
        store.setExperimenterEmail(email, 0);
      }

      store.setImageDescription(firstIFD.getComment(), 0);

      // set the X and Y pixel dimensions

      int resolutionUnit = firstIFD.getIFDIntValue(IFD.RESOLUTION_UNIT);
      TiffRational xResolution = firstIFD.getIFDRationalValue(IFD.X_RESOLUTION);
      TiffRational yResolution = firstIFD.getIFDRationalValue(IFD.Y_RESOLUTION);
      double pixX = xResolution == null ? 0 : 1 / xResolution.doubleValue();
      double pixY = yResolution == null ? 0 : 1 / yResolution.doubleValue();

      switch (resolutionUnit) {
        case 2:
          // resolution is expressed in pixels per inch
          pixX *= 25400;
          pixY *= 25400;
          break;
        case 3:
          // resolution is expressed in pixels per centimeter
          pixX *= 10000;
          pixY *= 10000;
          break;
      }

      store.setDimensionsPhysicalSizeX(pixX, 0, 0);
      store.setDimensionsPhysicalSizeY(pixY, 0, 0);
      store.setDimensionsPhysicalSizeZ(0.0, 0, 0);
    }
  }

  /**
   * Retrieves the image creation date.
   * @return the image creation date.
   */
  protected String getImageCreationDate() {
    Object o = ifds.get(0).getIFDValue(IFD.DATE_TIME);
    if (o instanceof String) return (String) o;
    if (o instanceof String[]) return ((String[]) o)[0];
    return null;
  }

  // -- Internal FormatReader API methods - metadata convenience --

  // TODO : the 'put' methods that accept primitive types could probably be
  // removed, as there are now 'addGlobalMeta' methods that accept
  // primitive types

  protected void put(String key, Object value) {
    if (value == null) return;
    if (value instanceof String) value = ((String) value).trim();
    addGlobalMeta(key, value);
  }

  protected void put(String key, int value) {
    if (value == -1) return; // indicates missing value
    addGlobalMeta(key, value);
  }

  protected void put(String key, boolean value) {
    put(key, new Boolean(value));
  }
  protected void put(String key, byte value) { put(key, new Byte(value)); }
  protected void put(String key, char value) { put(key, new Character(value)); }
  protected void put(String key, double value) { put(key, new Double(value)); }
  protected void put(String key, float value) { put(key, new Float(value)); }
  protected void put(String key, long value) { put(key, new Long(value)); }
  protected void put(String key, short value) { put(key, new Short(value)); }

  protected void put(String key, IFD ifd, int tag) {
    put(key, ifd.getIFDValue(tag));
  }

  protected void putInt(String key, IFD ifd, int tag) {
    put(key, ifd.getIFDIntValue(tag));
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    super.initFile(id);
    initMetadata();
  }

  // -- Helper methods --

  public static String getExifTagName(int tag) {
    return IFD.getIFDTagName(tag);
  }

}
