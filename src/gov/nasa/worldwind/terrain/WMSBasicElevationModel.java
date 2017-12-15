/*
 * Copyright (C) 2012 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration.
 * All Rights Reserved.
 */

package gov.nasa.worldwind.terrain;

import gov.nasa.worldwind.avlist.*;
import gov.nasa.worldwind.data.ByteBufferRaster;
import gov.nasa.worldwind.exception.WWRuntimeException;
import gov.nasa.worldwind.formats.tiff.GeotiffWriter;
import gov.nasa.worldwind.geom.*;
import gov.nasa.worldwind.globes.IExportElevationStream;
import gov.nasa.worldwind.ogc.wms.WMSCapabilities;
import gov.nasa.worldwind.retrieve.*;
import gov.nasa.worldwind.util.*;
import org.w3c.dom.*;

import javax.swing.filechooser.*;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * @author tag
 * @version $Id: WMSBasicElevationModel.java 2050 2014-06-09 18:52:26Z tgaskins $
 */
public class WMSBasicElevationModel extends BasicElevationModel
{
    private static final String[] formatOrderPreference = new String[]
        {
            "application/bil32", "application/bil16", "application/bil", "image/bil", "image/png", "image/tiff"
        };

    public WMSBasicElevationModel(AVList params)
    {
        super(params);
    }

    public WMSBasicElevationModel(Element domElement, AVList params)
    {
        this(wmsGetParamsFromDocument(domElement, params));
    }

    public WMSBasicElevationModel(WMSCapabilities caps, AVList params)
    {
        this(wmsGetParamsFromCapsDoc(caps, params));
    }

    public WMSBasicElevationModel(String restorableStateInXml)
    {
        super(wmsRestorableStateToParams(restorableStateInXml));

        RestorableSupport rs;
        try
        {
            rs = RestorableSupport.parse(restorableStateInXml);
        }
        catch (Exception e)
        {
            // Parsing the document specified by stateInXml failed.
            String message = Logging.getMessage("generic.ExceptionAttemptingToParseStateXml", restorableStateInXml);
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message, e);
        }

        this.doRestoreState(rs, null);
    }

    protected static AVList wmsGetParamsFromDocument(Element domElement, AVList params)
    {
        if (domElement == null)
        {
            String message = Logging.getMessage("nullValue.DocumentIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        if (params == null)
            params = new AVListImpl();

        DataConfigurationUtils.getWMSLayerConfigParams(domElement, params);
        BasicElevationModel.getBasicElevationModelConfigParams(domElement, params);
        wmsSetFallbacks(params);

        params.setValue(AVKey.TILE_URL_BUILDER, new URLBuilder(params.getStringValue(AVKey.WMS_VERSION), params));

        return params;
    }

    protected static AVList wmsGetParamsFromCapsDoc(WMSCapabilities caps, AVList params)
    {
        if (caps == null)
        {
            String message = Logging.getMessage("nullValue.WMSCapabilities");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        if (params == null)
        {
            String message = Logging.getMessage("nullValue.ElevationModelConfigParams");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        String wmsVersion;
        try
        {
            wmsVersion = caps.getVersion();
            getWMSElevationModelConfigParams(caps, formatOrderPreference, params);
        }
        catch (IllegalArgumentException e)
        {
            String message = Logging.getMessage("WMS.MissingLayerParameters");
            Logging.logger().log(java.util.logging.Level.SEVERE, message, e);
            throw new IllegalArgumentException(message, e);
        }
        catch (WWRuntimeException e)
        {
            String message = Logging.getMessage("WMS.MissingCapabilityValues");
            Logging.logger().log(java.util.logging.Level.SEVERE, message, e);
            throw new IllegalArgumentException(message, e);
        }

        wmsSetFallbacks(params);

        params.setValue(AVKey.TILE_URL_BUILDER, new URLBuilder(wmsVersion, params));

        return params;
    }

    protected static void wmsSetFallbacks(AVList params)
    {
        if (params.getValue(AVKey.LEVEL_ZERO_TILE_DELTA) == null)
        {
            Angle delta = Angle.fromDegrees(20);
            params.setValue(AVKey.LEVEL_ZERO_TILE_DELTA, new LatLon(delta, delta));
        }

        if (params.getValue(AVKey.TILE_WIDTH) == null)
            params.setValue(AVKey.TILE_WIDTH, 150);

        if (params.getValue(AVKey.TILE_HEIGHT) == null)
            params.setValue(AVKey.TILE_HEIGHT, 150);

        if (params.getValue(AVKey.FORMAT_SUFFIX) == null)
            params.setValue(AVKey.FORMAT_SUFFIX, ".bil");

        if (params.getValue(AVKey.MISSING_DATA_SIGNAL) == null)
            params.setValue(AVKey.MISSING_DATA_SIGNAL, -9999d);

        if (params.getValue(AVKey.NUM_LEVELS) == null)
            params.setValue(AVKey.NUM_LEVELS, 18); // approximately 20 cm per pixel

        if (params.getValue(AVKey.NUM_EMPTY_LEVELS) == null)
            params.setValue(AVKey.NUM_EMPTY_LEVELS, 0);
    }

    // TODO: consolidate common code in WMSTiledImageLayer.URLBuilder and WMSBasicElevationModel.URLBuilder
    protected static class URLBuilder implements TileUrlBuilder
    {
        protected static final String MAX_VERSION = "1.3.0";

        private final String layerNames;
        private final String styleNames;
        private final String imageFormat;
        private final String wmsVersion;
        private final String crs;
        protected String URLTemplate = null;

        protected URLBuilder(String version, AVList params)
        {
            Double d = (Double) params.getValue(AVKey.MISSING_DATA_SIGNAL);

            this.layerNames = params.getStringValue(AVKey.LAYER_NAMES);
            this.styleNames = params.getStringValue(AVKey.STYLE_NAMES);
            this.imageFormat = params.getStringValue(AVKey.IMAGE_FORMAT);

            String coordSystemKey;
            String defaultCS;
            if (version == null || WWUtil.compareVersion(version, "1.3.0") >= 0) // version 1.3.0 or greater
            {
                this.wmsVersion = MAX_VERSION;
                coordSystemKey = "&crs=";
                defaultCS = "CRS:84"; // would like to do EPSG:4326 but that's incompatible with our old WMS server, see WWJ-474
            }
            else
            {
                this.wmsVersion = version;
                coordSystemKey = "&srs=";
                defaultCS = "EPSG:4326";
            }

            String coordinateSystem = params.getStringValue(AVKey.COORDINATE_SYSTEM);
            this.crs = coordSystemKey + (coordinateSystem != null ? coordinateSystem : defaultCS);
        }

        public URL getURL(gov.nasa.worldwind.util.Tile tile, String altImageFormat) throws MalformedURLException
        {
            StringBuffer sb;
            if (this.URLTemplate == null)
            {
                sb = new StringBuffer(tile.getLevel().getService());

                if (!sb.toString().toLowerCase().contains("service=wms"))
                    sb.append("service=WMS");
                sb.append("&request=GetMap");
                sb.append("&version=");
                sb.append(this.wmsVersion);
                sb.append(this.crs);
                sb.append("&layers=");
                sb.append(this.layerNames);
                sb.append("&styles=");
                sb.append(this.styleNames != null ? this.styleNames : "");
                sb.append("&format=");
                if (altImageFormat == null)
                    sb.append(this.imageFormat);
                else
                    sb.append(altImageFormat);

                this.URLTemplate = sb.toString();
            }
            else
            {
                sb = new StringBuffer(this.URLTemplate);
            }

            sb.append("&width=");
            sb.append(tile.getWidth());
            sb.append("&height=");
            sb.append(tile.getHeight());

            Sector s = tile.getSector();
            sb.append("&bbox=");
            // The order of the coordinate specification matters, and it changed with WMS 1.3.0.
            if (WWUtil.compareVersion(this.wmsVersion, "1.1.1") <= 0 || this.crs.contains("CRS:84"))
            {
                // 1.1.1 and earlier and CRS:84 use lon/lat order
                sb.append(s.getMinLongitude().getDegrees());
                sb.append(",");
                sb.append(s.getMinLatitude().getDegrees());
                sb.append(",");
                sb.append(s.getMaxLongitude().getDegrees());
                sb.append(",");
                sb.append(s.getMaxLatitude().getDegrees());
            }
            else
            {
                // 1.3.0 uses lat/lon ordering
                sb.append(s.getMinLatitude().getDegrees());
                sb.append(",");
                sb.append(s.getMinLongitude().getDegrees());
                sb.append(",");
                sb.append(s.getMaxLatitude().getDegrees());
                sb.append(",");
                sb.append(s.getMaxLongitude().getDegrees());
            }

            sb.append("&"); // terminate the query string

            return new java.net.URL(sb.toString().replace(" ", "%20"));
        }
    }

    //**************************************************************//
    //********************  Configuration  *************************//
    //**************************************************************//

    /**
     * Parses WMSBasicElevationModel configuration parameters from a specified WMS Capabilities source. This writes
     * output as key-value pairs to params. Supported key and parameter names are: <table>
     * <th><td>Parameter</td><td>Value</td><td>Type</td></th> <tr><td>{@link AVKey#ELEVATION_MAX}</td><td>WMS layer's
     * maximum extreme elevation</td><td>Double</td></tr> <tr><td>{@link AVKey#ELEVATION_MIN}</td><td>WMS layer's
     * minimum extreme elevation</td><td>Double</td></tr> <tr><td>{@link AVKey#DATA_TYPE}</td><td>Translate WMS layer's
     * image format to a matching data type</td><td>String</td></tr> </table> This also parses common WMS layer
     * parameters by invoking {@link DataConfigurationUtils#getWMSLayerConfigParams(gov.nasa.worldwind.ogc.wms.WMSCapabilities,
     * String[], gov.nasa.worldwind.avlist.AVList)}.
     *
     * @param caps                  the WMS Capabilities source to parse for WMSBasicElevationModel configuration
     *                              parameters.
     * @param formatOrderPreference an ordered array of preferred image formats, or null to use the default format.
     * @param params                the output key-value pairs which recieve the WMSBasicElevationModel configuration
     *                              parameters.
     *
     * @return a reference to params.
     *
     * @throws IllegalArgumentException if either the document or params are null, or if params does not contain the
     *                                  required key-value pairs.
     * @throws gov.nasa.worldwind.exception.WWRuntimeException
     *                                  if the Capabilities document does not contain any of the required information.
     */
    public static AVList getWMSElevationModelConfigParams(WMSCapabilities caps, String[] formatOrderPreference,
        AVList params)
    {
        if (caps == null)
        {
            String message = Logging.getMessage("nullValue.WMSCapabilities");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        if (params == null)
        {
            String message = Logging.getMessage("nullValue.ElevationModelConfigParams");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        // Get common WMS layer parameters.
        DataConfigurationUtils.getWMSLayerConfigParams(caps, formatOrderPreference, params);

        // Attempt to extract the WMS layer names from the specified parameters.
        String layerNames = params.getStringValue(AVKey.LAYER_NAMES);
        if (layerNames == null || layerNames.length() == 0)
        {
            String message = Logging.getMessage("nullValue.WMSLayerNames");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        String[] names = layerNames.split(",");
        if (names == null || names.length == 0)
        {
            String message = Logging.getMessage("nullValue.WMSLayerNames");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        // Get the layer's extreme elevations.
        Double[] extremes = caps.getLayerExtremeElevations(names);

        Double d = (Double) params.getValue(AVKey.ELEVATION_MIN);
        if (d == null && extremes != null && extremes[0] != null)
            params.setValue(AVKey.ELEVATION_MIN, extremes[0]);

        d = (Double) params.getValue(AVKey.ELEVATION_MAX);
        if (d == null && extremes != null && extremes[1] != null)
            params.setValue(AVKey.ELEVATION_MAX, extremes[1]);

        // Compute the internal pixel type from the image format.
        if (params.getValue(AVKey.DATA_TYPE) == null && params.getValue(AVKey.IMAGE_FORMAT) != null)
        {
            String s = WWIO.makeDataTypeForMimeType(params.getValue(AVKey.IMAGE_FORMAT).toString());
            if (s != null)
                params.setValue(AVKey.DATA_TYPE, s);
        }

        // Use the default data type.
        if (params.getValue(AVKey.DATA_TYPE) == null)
            params.setValue(AVKey.DATA_TYPE, AVKey.INT16);

        // Use the default byte order.
        if (params.getValue(AVKey.BYTE_ORDER) == null)
            params.setValue(AVKey.BYTE_ORDER, AVKey.LITTLE_ENDIAN);

        return params;
    }

    // 为ElevationModel添加获取Sector覆盖的所有Tile的方法
    public Tile[][] getTilesInSector(Sector sector, int levelNumber)
    {
        if (sector == null)
        {
            String msg = Logging.getMessage("nullValue.SectorIsNull");
            Logging.logger().severe(msg);
            throw new IllegalArgumentException(msg);
        }

        Level targetLevel = this.getLevels().getLastLevel();
        if (levelNumber >= 0)
        {
            for (int i = levelNumber; i < this.getLevels().getLastLevel().getLevelNumber(); i++)
            {
                if (this.getLevels().isLevelEmpty(i))
                    continue;

                targetLevel = this.getLevels().getLevel(i);
                break;
            }
        }

        // Collect all the tiles intersecting the input sector.
        LatLon delta = targetLevel.getTileDelta();
        LatLon origin = this.getLevels().getTileOrigin();
        final int nwRow = Tile.computeRow(delta.getLatitude(), sector.getMaxLatitude(), origin.getLatitude());
        final int nwCol = Tile.computeColumn(delta.getLongitude(), sector.getMinLongitude(), origin.getLongitude());
        final int seRow = Tile.computeRow(delta.getLatitude(), sector.getMinLatitude(), origin.getLatitude());
        final int seCol = Tile.computeColumn(delta.getLongitude(), sector.getMaxLongitude(), origin.getLongitude());

        int numRows = nwRow - seRow + 1;
        int numCols = seCol - nwCol + 1;
        Tile[][] sectorTiles = new Tile[numRows][numCols];

        for (int row = nwRow; row >= seRow; row--)
        {
            for (int col = nwCol; col <= seCol; col++)
            {
                TileKey key = new TileKey(targetLevel.getLevelNumber(), row, col, targetLevel.getCacheName());
                Sector tileSector = this.getLevels().computeSectorForKey(key);
                sectorTiles[nwRow - row][col - nwCol] = new Tile(tileSector, targetLevel, row, col);
            }
        }

        return sectorTiles;
    }

    /**
     * Appends WMS basic elevation model configuration elements to the superclass configuration document.
     *
     * @param params configuration parameters describing this WMS basic elevation model.
     *
     * @return a WMS basic elevation model configuration document.
     */
    protected Document createConfigurationDocument(AVList params)
    {
        Document doc = super.createConfigurationDocument(params);
        if (doc == null || doc.getDocumentElement() == null)
            return doc;

        DataConfigurationUtils.createWMSLayerConfigElements(params, doc.getDocumentElement());

        return doc;
    }

    //**************************************************************//
    //********************  Composition  ***************************//
    //**************************************************************//

    protected static class ElevationCompositionTile extends ElevationTile
    {
        private int width;
        private int height;
        private File file;

        public ElevationCompositionTile(Sector sector, Level level, int width, int height)
            throws IOException
        {
            super(sector, level, -1, -1); // row and column aren't used and need to signal that

            this.width = width;
            this.height = height;

            this.file = File.createTempFile(WWIO.DELETE_ON_EXIT_PREFIX, level.getFormatSuffix());
        }

        @Override
        public int getWidth()
        {
            return this.width;
        }

        @Override
        public int getHeight()
        {
            return this.height;
        }

        @Override
        public String getPath()
        {
            return this.file.getPath();
        }

        public File getFile()
        {
            return this.file;
        }
    }

    public void composeElevations(Sector sector, List<? extends LatLon> latlons, int tileWidth, double[] buffer)
        throws Exception
    {
        if (sector == null)
        {
            String msg = Logging.getMessage("nullValue.SectorIsNull");
            Logging.logger().severe(msg);
            throw new IllegalArgumentException(msg);
        }

        if (latlons == null)
        {
            String msg = Logging.getMessage("nullValue.LatLonListIsNull");
            Logging.logger().severe(msg);
            throw new IllegalArgumentException(msg);
        }

        if (buffer == null)
        {
            String msg = Logging.getMessage("nullValue.ElevationsBufferIsNull");
            Logging.logger().severe(msg);
            throw new IllegalArgumentException(msg);
        }

        if (buffer.length < latlons.size() || tileWidth > latlons.size())
        {
            String msg = Logging.getMessage("ElevationModel.ElevationsBufferTooSmall", latlons.size());
            Logging.logger().severe(msg);
            throw new IllegalArgumentException(msg);
        }

        ElevationCompositionTile tile = new ElevationCompositionTile(sector, this.getLevels().getLastLevel(),
            tileWidth, latlons.size() / tileWidth);

        this.downloadElevations(tile);
        tile.setElevations(this.readElevations(tile.getFile().toURI().toURL()), this);

        for (int i = 0; i < latlons.size(); i++)
        {
            LatLon ll = latlons.get(i);
            if (ll == null)
                continue;

            double value = this.lookupElevation(ll.getLatitude(), ll.getLongitude(), tile);

            // If an elevation at the given location is available, then write that elevation to the destination buffer.
            // Otherwise do nothing.
            if (value != this.getMissingDataSignal())
                buffer[i] = value;
        }
    }

    public class ExportElevationBuffer implements IExportElevationStream{
        //private double [] buffer;
        private BufferWrapper elevations;
        final private Sector sector;
        final private int width;
        final private int height;
        final private int row;
        final private int col;

        ExportElevationBuffer(Sector sector, int row, int col, int width, int height, BufferWrapper elevations) {
            this.sector = sector;
            this.width = width;
            this.height = height;
            this.elevations = elevations;
            this.row = row;
            this.col = col;
        }

        public int getWidth() {return width;}
        public int getHeight() {return height;}
        public String getFileName() {return String.format("%d_%d.tif", row, col);}
        public double[] getBuffer() {return null;}
        public BufferWrapper getBufferWrapper() {return elevations;}

        private String getPath() {
            String desktopPath = FileSystemView.getFileSystemView() .getHomeDirectory().getAbsolutePath();
            String imagePath = desktopPath + "\\" + getFileName();
            return imagePath;
        }

        public void doSave() throws Exception {
            //FileWriter fw = new FileWriter(new File(getPath()));

            AVList elev32 = new AVListImpl();

            elev32.setValue(AVKey.SECTOR, this.sector);
            elev32.setValue(AVKey.WIDTH, this.width);
            elev32.setValue(AVKey.HEIGHT, this.height);
            elev32.setValue(AVKey.COORDINATE_SYSTEM, AVKey.COORDINATE_SYSTEM_GEOGRAPHIC);
            elev32.setValue(AVKey.PIXEL_FORMAT, AVKey.ELEVATION);
            elev32.setValue(AVKey.DATA_TYPE, AVKey.FLOAT32);
            elev32.setValue(AVKey.ELEVATION_UNIT, AVKey.UNIT_METER);
            elev32.setValue(AVKey.BYTE_ORDER, AVKey.BIG_ENDIAN);
            elev32.setValue(AVKey.MISSING_DATA_SIGNAL, (double) Short.MIN_VALUE);

            ByteBufferRaster raster = (ByteBufferRaster) ByteBufferRaster.createGeoreferencedRaster(elev32);
            // copy elevation values to the elevation raster
            int i = 0;
            for (int y = 0; y < this.height; y++)
                for (int x = 0; x < this.width; x++)
                    raster.setDoubleAtPosition(y, x, elevations.getDouble(i++));

            GeotiffWriter writer = new GeotiffWriter(new File(getPath()));

            try
            {
                writer.write(raster);
            }
            finally
            {
                writer.close();
            }
        }
    }

    public IExportElevationStream[] composeElevations(Sector sector) throws Exception {
        if (sector == null) {
            String msg = Logging.getMessage("nullValue.SectorIsNull");
            Logging.logger().severe(msg);
            throw new IllegalArgumentException(msg);
        }

        // int numTiles = 0;

        Sector intersection = this.levels.getSector().intersection(sector);
        int levelNumber = this.getLevels().getLastLevel().getLevelNumber();
        Tile[][] tiles = this.getTilesInSector(intersection, levelNumber);

        if (tiles.length == 0 || tiles[0].length == 0) {
            Logging.logger().severe(Logging.getMessage("ElevationModel.NoElevationAvailable"));
            return null;
        }
        // 计算每份的sector并将相应的tile合并到该sector表示的范围中
        return  computeSectorToTile(intersection, tiles);
    }

    protected ExportElevationBuffer[] computeSectorToTile(Sector sector, Tile[][] tiles) throws Exception {

        if (tiles.length == 0 || tiles[0].length == 0) {
            String msg = Logging.getMessage("nullValue.tilesIsNull");
            Logging.logger().severe(msg);
            throw new IllegalArgumentException(msg);
        }

        // 计算tiles的范围
        int tileWidth = 0;
        int tileHeight = 0;
        int sectorWidth = 0;
        int sectorHeight = 0;
        ArrayList<Sector> sectors = new ArrayList<Sector>();

        for (Tile[] row : tiles)
            for (Tile tile : row)
                sectors.add(tile.getSector());

        Sector tilesSector = Sector.union(sectors);
        // 计算sector所占范围的像素值 width, height
               if (tiles.length > 0){
            tileHeight = tiles.length * this.levels.getLastLevel().getTileHeight();

            if (tiles[0].length > 0)
                tileWidth = tiles[0].length * this.levels.getLastLevel().getTileWidth();
        }

        sectorHeight = (int)(sector.getDeltaLatDegrees() / tilesSector.getDeltaLatDegrees() * tileHeight);
        sectorWidth = (int)(sector.getDeltaLonDegrees() / tilesSector.getDeltaLonDegrees() * tileWidth);
        // 平均切分乘n份， 参考大小2048
        int maxSide = sectorWidth > sectorHeight ? sectorWidth : sectorHeight;
        int numParts = (int)(maxSide / 2048) + 1;
        int subWidth = sectorWidth / numParts;
        int subHeight = sectorHeight / numParts;
        double deltaLatDegrees = sector.getDeltaLatDegrees() / numParts;
        double deltaLonDegress = sector.getDeltaLonDegrees() / numParts;

//        ElevationCompositionTile[] compositionTiles = new ElevationCompositionTile[numParts * numParts];
        ExportElevationBuffer[] buffers = new ExportElevationBuffer[numParts * numParts];

        for (int row = 0; row < numParts; ++row) {
            Angle subSectorMinLat = sector.getMinLatitude().addDegrees(row * deltaLatDegrees);
            Angle subSectorMaxLat = subSectorMinLat.addDegrees(deltaLatDegrees);

            for (int col = 0; col < numParts; ++col){
                Angle subSectorMinLon = sector.getMinLongitude().addDegrees(col * deltaLonDegress);
                Angle subSectorMaxLon = subSectorMinLon.addDegrees(deltaLonDegress);
                Sector subSector = new Sector(subSectorMinLat, subSectorMaxLat, subSectorMinLon, subSectorMaxLon);

                ElevationCompositionTile tile = new ElevationCompositionTile(subSector, this.getLevels().getLastLevel(),
                    subWidth, subHeight);

                this.downloadElevations(tile);
                tile.setElevations(this.readElevations(tile.getFile().toURI().toURL()), this);
                buffers[row * numParts + col] = new ExportElevationBuffer(subSector, row, col, subWidth, subHeight, tile.getElevations());
            }
        }

//        double [] buffer = new double[sectorWidth * sectorHeight];
//
//        for (int i = 0; i < sectorHeight; ++i) {
//            int row = i / subHeight;
//            int rowIndex = i % subHeight;
//
//            for (int j = 0; j < sectorWidth; ++j) {
//                // 计算当前象所所在行列
//                int col = j / subWidth;
//                int colIndex = j % subWidth;
//
//                ElevationCompositionTile tile = compositionTiles[row * numParts + col];
//                buffer[i * sectorWidth + j] = tile.getElevations().getDouble(rowIndex * subWidth + colIndex);
//            }
//        }
//
//        return new ExportElevationBuffer(sectorWidth, sectorHeight, buffer);

        return buffers;
    }


    protected void downloadElevations(ElevationCompositionTile tile) throws Exception
    {
        URL url = tile.getResourceURL();

        Retriever retriever = new HTTPRetriever(url, new CompositionRetrievalPostProcessor(tile.getFile()));
        retriever.setConnectTimeout(10000);
        retriever.setReadTimeout(60000);
        retriever.call();
    }

    protected static class CompositionRetrievalPostProcessor extends AbstractRetrievalPostProcessor
    {
        // Note: Requested data is never marked as absent because the caller may want to continually re-try retrieval
        protected File outFile;

        public CompositionRetrievalPostProcessor(File outFile)
        {
            this.outFile = outFile;
        }

        protected File doGetOutputFile()
        {
            return this.outFile;
        }

        @Override
        protected boolean overwriteExistingFile()
        {
            return true;
        }

        @Override
        protected boolean isDeleteOnExit(File outFile)
        {
            return outFile.getPath().contains(WWIO.DELETE_ON_EXIT_PREFIX);
        }
    }

    //**************************************************************//
    //********************  Restorable Support  ********************//
    //**************************************************************//

    public void getRestorableStateForAVPair(String key, Object value,
        RestorableSupport rs, RestorableSupport.StateObject context)
    {
        if (value instanceof URLBuilder)
        {
            rs.addStateValueAsString(context, "wms.Version", ((URLBuilder) value).wmsVersion);
            rs.addStateValueAsString(context, "wms.Crs", ((URLBuilder) value).crs);
        }
        else
        {
            super.getRestorableStateForAVPair(key, value, rs, context);
        }
    }

    protected static AVList wmsRestorableStateToParams(String stateInXml)
    {
        if (stateInXml == null)
        {
            String message = Logging.getMessage("nullValue.StringIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        RestorableSupport rs;
        try
        {
            rs = RestorableSupport.parse(stateInXml);
        }
        catch (Exception e)
        {
            // Parsing the document specified by stateInXml failed.
            String message = Logging.getMessage("generic.ExceptionAttemptingToParseStateXml", stateInXml);
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message, e);
        }

        AVList params = new AVListImpl();
        wmsRestoreStateForParams(rs, null, params);
        return params;
    }

    protected static void wmsRestoreStateForParams(RestorableSupport rs, RestorableSupport.StateObject context,
        AVList params)
    {
        // Invoke the BasicElevationModel functionality.
        restoreStateForParams(rs, null, params);

        String s = rs.getStateValueAsString(context, AVKey.IMAGE_FORMAT);
        if (s != null)
            params.setValue(AVKey.IMAGE_FORMAT, s);

        s = rs.getStateValueAsString(context, AVKey.TITLE);
        if (s != null)
            params.setValue(AVKey.TITLE, s);

        s = rs.getStateValueAsString(context, AVKey.DISPLAY_NAME);
        if (s != null)
            params.setValue(AVKey.DISPLAY_NAME, s);

        RestorableSupport.adjustTitleAndDisplayName(params);

        s = rs.getStateValueAsString(context, AVKey.LAYER_NAMES);
        if (s != null)
            params.setValue(AVKey.LAYER_NAMES, s);

        s = rs.getStateValueAsString(context, AVKey.STYLE_NAMES);
        if (s != null)
            params.setValue(AVKey.STYLE_NAMES, s);

        s = rs.getStateValueAsString(context, "wms.Version");
        params.setValue(AVKey.TILE_URL_BUILDER, new URLBuilder(s, params));
    }
}
