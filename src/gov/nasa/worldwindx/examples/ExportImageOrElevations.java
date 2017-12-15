/*
 * Copyright (C) 2012 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration.
 * All Rights Reserved.
 */

package gov.nasa.worldwindx.examples;

import gov.nasa.worldwind.Configuration;
import gov.nasa.worldwind.avlist.*;
import gov.nasa.worldwind.data.*;
import gov.nasa.worldwind.util.*;
import gov.nasa.worldwind.wms.WMSTiledImageLayer;
import gov.nasa.worldwindx.examples.util.SectorSelector;
import gov.nasa.worldwind.formats.tiff.GeotiffWriter;
import gov.nasa.worldwind.geom.*;
import gov.nasa.worldwind.globes.*;
import gov.nasa.worldwind.layers.*;
import gov.nasa.worldwind.render.DrawContext;

import javax.imageio.ImageIO;
import javax.swing.filechooser.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.beans.*;
import java.io.*;
import java.util.*;

/**
 * Demonstrates how to use the {@link SectorSelector} utility to save selected image or/and elevations to a GeoTIFF
 * file. Click "Start selection", select a region on the globe, and then click "Save elevations..." to export elevation
 * data for the selected region to a GeoTIFF, or "Save image..." to export imagery.
 *
 * @author Lado Garakanidze
 * @version $Id: ExportImageOrElevations.java 2109 2014-06-30 16:52:38Z tgaskins $
 */
public class ExportImageOrElevations extends ApplicationTemplate
{
    public static class AppFrame extends ApplicationTemplate.AppFrame
    {
        private static final double MISSING_DATA_SIGNAL = (double) Short.MIN_VALUE;

        private JButton btnSaveElevations = null;
        private JButton btnSaveImage = null;
        private Sector selectedSector = null;
        private JFileChooser fileChooser = null;
        private SectorSelector selector;

        public AppFrame()
        {
            super(true, true, false);

            this.selector = new SectorSelector(getWwd());
            this.selector.setInteriorColor(new Color(1f, 1f, 1f, 0.1f));
            this.selector.setBorderColor(new Color(1f, 0f, 0f, 0.5f));
            this.selector.setBorderWidth(3);

            JPanel btnPanel = new JPanel(new GridLayout(5, 1, 0, 5));
            {
                JButton
                    // Set up a button to enable and disable region selection.
                    btn = new JButton(new EnableSelectorAction());
                btn.setToolTipText("Press Start then press and drag button 1 on globe");
                btnPanel.add(btn);

                btnSaveElevations = new JButton(new SaveElevationsAction());
                btnSaveElevations.setEnabled(false);
                btnSaveElevations.setToolTipText("Click the button to save elevations of the selected area");
                btnPanel.add(btnSaveElevations);

                btnSaveImage = new JButton(new SaveImageAction());
                btnSaveImage.setEnabled(false);
                btnSaveImage.setToolTipText("Click the button to save image of the selected area");
                btnPanel.add(btnSaveImage);
            }
            this.getControlPanel().add(btnPanel, BorderLayout.SOUTH);

            // Listen for changes to the sector selector's region. Could also just wait until the user finishes
            // and query the result using selector.getSector().
            this.selector.addPropertyChangeListener(SectorSelector.SECTOR_PROPERTY, new PropertyChangeListener()
            {
                public void propertyChange(PropertyChangeEvent evt)
                {
                    Sector sector = (Sector) evt.getNewValue();
                    if (null != sector)
                    {
                        selectedSector = sector;
                        btnSaveElevations.setEnabled(true);
                        btnSaveImage.setEnabled(true);
                    }
                }
            });

            this.enableNAIPLayer();
        }

        private class SaveElevationsAction extends AbstractAction
        {
            public SaveElevationsAction()
            {
                super("Save elevations ...");
            }

            public void actionPerformed(ActionEvent e)
            {
                doSaveElevations();
            }
        }

        private class SaveImageAction extends AbstractAction
        {
            public SaveImageAction()
            {
                super("Save image ...");
            }

            public void actionPerformed(ActionEvent e)
            {
                doSaveImage();
            }
        }

        private class EnableSelectorAction extends AbstractAction
        {
            public EnableSelectorAction()
            {
                super("Start selection");
            }

            public void actionPerformed(ActionEvent e)
            {
                ((JButton) e.getSource()).setAction(new DisableSelectorAction());
                selector.enable();
            }
        }

        private class DisableSelectorAction extends AbstractAction
        {
            public DisableSelectorAction()
            {
                super("Clear selection");
            }

            public void actionPerformed(ActionEvent e)
            {
                selector.disable();
                btnSaveElevations.setEnabled(false);
                btnSaveImage.setEnabled(false);
                selectedSector = null;
                ((JButton) e.getSource()).setAction(new EnableSelectorAction());
            }
        }

        public static class GeotiffFileFilter extends javax.swing.filechooser.FileFilter
        {
            public boolean accept(File file)
            {
                if (file == null)
                {
                    String message = Logging.getMessage("nullValue.FileIsNull");
                    Logging.logger().severe(message);
                    throw new IllegalArgumentException(message);
                }

                return file.isDirectory() || file.getName().toLowerCase().endsWith(".tif");
            }

            public String getDescription()
            {
                return "Geo-TIFF (tif)";
            }
        }

        public static class JPEGFileFilter extends javax.swing.filechooser.FileFilter
        {
            public boolean accept(File file)
            {
                if (file == null)
                {
                    String message = Logging.getMessage("nullValue.FileIsNull");
                    Logging.logger().severe(message);
                    throw new IllegalArgumentException(message);
                }

                return file.isDirectory() || file.getName().toLowerCase().endsWith(".jpeg")
                    || file.getName().toLowerCase().endsWith(".jpg");
            }

            public String getDescription()
            {
                return "JPEG(jpeg)";
            }
        }

        public static class PngFileFilter extends javax.swing.filechooser.FileFilter
        {
            public boolean accept(File file)
            {
                if (file == null)
                {
                    String message = Logging.getMessage("nullValue.FileIsNull");
                    Logging.logger().severe(message);
                    throw new IllegalArgumentException(message);
                }

                return file.isDirectory() || file.getName().toLowerCase().endsWith(".png");
            }

            public String getDescription()
            {
                return "PNG(png)";
            }
        }

        public class ExportImagePackage
        {
            private final File imageFile;
            private final File jgwFile;
            private final String jgw;
            private final BufferedImage image;

            public ExportImagePackage(BufferedImage image, File imageFile, String jgw, File jgwFile){
                this.image = image;
                this.imageFile = imageFile;
                this.jgwFile = jgwFile;
                this.jgw = jgw;
            }

            public void doSave() throws IOException
            {
                // 存储图片
                ImageIO.write(this.image, "jpeg", imageFile);
                // 存储jgw文件
                FileWriter fw = new FileWriter(jgwFile);
                fw.write(jgw);
                fw.flush();
                fw.close();
            }

            public String getFileName() {
                return imageFile.getName();
            }
        }

        private File selectDestinationFile(String title, String filename)
        {
            File destFile = null;

            if (this.fileChooser == null)
            {
                this.fileChooser = new JFileChooser();
                this.fileChooser.setCurrentDirectory(new File(Configuration.getUserHomeDirectory()));
                this.fileChooser.addChoosableFileFilter(new JPEGFileFilter());
            }

            this.fileChooser.setDialogTitle(title);
            this.fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            this.fileChooser.setMultiSelectionEnabled(false);
            this.fileChooser.setDialogType(JFileChooser.SAVE_DIALOG);

            this.fileChooser.setName(filename);

            int status = this.fileChooser.showSaveDialog(null);
            if (status == JFileChooser.APPROVE_OPTION)
            {
                destFile = this.fileChooser.getSelectedFile();
                if (!destFile.getName().endsWith(".tif"))
                    destFile = new File(destFile.getPath() + ".tif");
            }
            return destFile;
        }

        public void doSaveElevations()
        {
//            final File saveToFile = this.selectDestinationFile(
//                "Select a destination GeoTiff file to save elevations", "elevation");
//
//            if (saveToFile == null)
//                return;

            final JOptionPane jop = new JOptionPane("Requesting elevations ...",
                JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, null, new Object[] {}, null);

            final JDialog jd = jop.createDialog(this.getRootPane().getTopLevelAncestor(), "Please wait...");
            jd.setModal(false);
            jd.setVisible(true);

            Thread t = new Thread(new Runnable()
            {
                public void run()
                {
                    try
                    {
                        int[] size = adjustSize(selectedSector, 512);
                        int width = size[0], height = size[1];

                        // double[] elevations = readElevations(selectedSector, width, height);
                        Globe globe = getWwd().getModel().getGlobe();
                        IExportElevationStream[] streams = globe.getElevationModel().composeElevations(selectedSector);

                        for (IExportElevationStream stream : streams) {
                            if (null != stream)
                            {
                                jd.setTitle("Writing elevations to " + stream.getFileName());
                                // writeElevationsToFile(selectedSector, stream, saveToFile);
                                stream.doSave();

                                jd.setVisible(false);
                                JOptionPane.showMessageDialog(wwjPanel,
                                    "Elevations saved into the " + stream.getFileName());
                            }
                            else
                            {
                                jd.setVisible(false);
                                JOptionPane.showMessageDialog(wwjPanel,
                                    "Attempt to save elevations to the " + stream.getFileName() + " has failed.");
                            }
                        }
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                        jd.setVisible(false);
                        JOptionPane.showMessageDialog(wwjPanel, e.getMessage());
                    }
                    finally
                    {
                        SwingUtilities.invokeLater(new Runnable()
                        {
                            public void run()
                            {
                                setCursor(Cursor.getDefaultCursor());
                                getWwd().redraw();
                                jd.setVisible(false);
                            }
                        });
                    }
                }
            });

            this.setCursor(new Cursor(Cursor.WAIT_CURSOR));
            this.getWwd().redraw();
            t.start();
        }

        public void enableNAIPLayer()
        {
            LayerList list = this.getWwd().getModel().getLayers();
            ListIterator iterator = list.listIterator();
            while (iterator.hasNext())
            {
                Layer layer = (Layer) iterator.next();
                if (layer.getName().contains("NAIP"))
                {
                    layer.setEnabled(true);
                    break;
                }
            }
        }

        public void doSaveImage()
        {
            TiledImageLayer currentLayer = null;
            LayerList list = this.getWwd().getModel().getLayers();
            DrawContext dc = this.getWwd().getSceneController().getDrawContext();

            ListIterator iterator = list.listIterator();
            while (iterator.hasNext())
            {
                Object o = iterator.next();
                if (o instanceof TiledImageLayer)
                {
                    TiledImageLayer layer = (TiledImageLayer) o;
                    if (layer.isEnabled() && layer.isLayerActive(dc) && layer.isLayerInView(dc))
                        currentLayer = layer;

                    if (layer.getName().equals(new String("Bing Imagery")))
                        break;
                }
            }

            if (null == currentLayer)
                return;

//            final File saveToFile = this.selectDestinationFile("Select a destination GeoTiff file to save the image",
//                "image");
//
//            if (saveToFile == null)
//                return;

            final TiledImageLayer activeLayer = currentLayer;

            final JOptionPane jop = new JOptionPane("Requesting image ...",
                JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, null, new Object[] {}, null);

            final JDialog jd = jop.createDialog(this.getRootPane().getTopLevelAncestor(), "Please wait...");
            jd.setModal(false);
            jd.setVisible(true);

            Thread t = new Thread(new Runnable()
            {
                public void run()
                {
                    try
                    {
                       // BufferedImage image = captureImage(activeLayer, selectedSector, 8192);
                        ExportImagePackage[] exportImages = captureImage(activeLayer, selectedSector);


                        for (int i = 0; i < exportImages.length; ++i) {

                            if (null != exportImages[i])
                            {
                                jd.setTitle("Writing image to " + exportImages[i].getFileName());
                                //writeImageToFile(null, images[i], files[i]);
                                exportImages[i].doSave();
                                jd.setVisible(false);
                                JOptionPane.showMessageDialog(wwjPanel, "Image saved into the " + exportImages[i].getFileName());
                            }
                            else
                            {
                                jd.setVisible(false);
                                JOptionPane.showMessageDialog(wwjPanel,
                                    "Attempt to save image to the " + exportImages[i].getFileName() + " has failed.");
                            }
                        }
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                        jd.setVisible(false);
                        JOptionPane.showMessageDialog(wwjPanel, e.getMessage());
                    }
                    finally
                    {
                        SwingUtilities.invokeLater(new Runnable()
                        {
                            public void run()
                            {
                                setCursor(Cursor.getDefaultCursor());
                                getWwd().redraw();
                                jd.setVisible(false);
                            }
                        });
                    }
                }
            });

            this.setCursor(new Cursor(Cursor.WAIT_CURSOR));
            this.getWwd().redraw();
            t.start();
        }

        private int[] adjustSize(Sector sector, int desiredSize)
        {
            int[] size = new int[] {desiredSize, desiredSize};

            if (null != sector && desiredSize > 0)
            {
                LatLon centroid = sector.getCentroid();
                Angle dLat = LatLon.greatCircleDistance(new LatLon(sector.getMinLatitude(), sector.getMinLongitude()),
                    new LatLon(sector.getMaxLatitude(), sector.getMinLongitude()));
                Angle dLon = LatLon.greatCircleDistance(new LatLon(centroid.getLatitude(), sector.getMinLongitude()),
                    new LatLon(centroid.getLatitude(), sector.getMaxLongitude()));

                double max = Math.max(dLat.radians, dLon.radians);
                double min = Math.min(dLat.radians, dLon.radians);

                int minSize = (int) ((min == 0d) ? desiredSize : ((double) desiredSize * min / max));

                if (dLon.radians > dLat.radians)
                {
                    size[0] = desiredSize;      // width
                    size[1] = minSize;  // height
                }
                else
                {
                    size[0] = minSize;  // width
                    size[1] = desiredSize;      // height
                }
            }

            return size;
        }

        private BufferedImage captureImage(TiledImageLayer layer, Sector sector, int minSize)
            throws Exception
        {
            int[] size = this.adjustSize(sector, minSize);
            int width = size[0], height = size[1];

            String mimeType = layer.getDefaultImageFormat();
            if (layer.isImageFormatAvailable("image/png"))
                mimeType = "image/png";
            else if (layer.isImageFormatAvailable("image/jpg"))
                mimeType = "image/jpeg";

            WMSTiledImageLayer.setComposeWithSuper(true);
            return layer.composeImageForSector(this.selectedSector, width, height, 1d, -1, mimeType, true, null, 30000);
        }

        private ExportImagePackage[] captureImage(TiledImageLayer layer, Sector sector)
            throws Exception
        {
            String mimeType = layer.getDefaultImageFormat();
            return composeImageForSector(layer, sector, -1, mimeType);
        }

        private BufferedImage composeImageForSubSector(TiledImageLayer layer,  Sector sector,int canvasWidth, int canvasHeight, double aspectRatio,
            int levelNumber, String mimeType, boolean abortOnError, BufferedImage image, int timeout) throws Exception
        {

            Sector intersection = layer.getLevels().getSector().intersection(sector);
            if (levelNumber < 0)
            {
                levelNumber = layer.getLevels().getLastLevel().getLevelNumber();
            }
            else if (levelNumber > layer.getLevels().getLastLevel().getLevelNumber())
            {
                Logging.logger().warning(Logging.getMessage("generic.LevelRequestedGreaterThanMaxLevel",
                    levelNumber, layer.getLevels().getLastLevel().getLevelNumber()));
                levelNumber = layer.getLevels().getLastLevel().getLevelNumber();
            }

            TextureTile[][] tiles = layer.getTilesInSector(intersection, levelNumber);
            if (tiles.length == 0 || tiles[0].length == 0)
            {
                Logging.logger().severe(Logging.getMessage("layers.TiledImageLayer.NoImagesAvailable"));
                return image;
            }

            if (image == null)
                image = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_RGB);

            double tileCount = 0;
            for (TextureTile[] row : tiles)
            {
                for (TextureTile tile : row)
                {
                    if (tile == null)
                        continue;

                    BufferedImage tileImage;
                    try
                    {
                        tileImage = layer.getImage(tile, mimeType, timeout);
                        Thread.sleep(1); // generates InterruptedException if thread has been interupted

                        if (tileImage != null)
                            ImageUtil.mergeImage(sector, tile.getSector(), aspectRatio, tileImage, image);

                        ++tileCount;
                        // this.firePropertyChange(AVKey.PROGRESS, tileCount / numTiles, ++tileCount / numTiles);
                    }
                    catch (InterruptedException e)
                    {
                        throw e;
                    }
                    catch (InterruptedIOException e)
                    {
                        throw e;
                    }
                    catch (Exception e)
                    {
                        if (abortOnError)
                            throw e;

                        String message = Logging.getMessage("generic.ExceptionWhileRequestingImage", tile.getPath());
                        Logging.logger().log(java.util.logging.Level.WARNING, message, e);
                    }
                }
            }

            return image;
        }

        // 自定义生成导出影像方法
        private ExportImagePackage[] composeImageForSector(TiledImageLayer layer,      // 瓦片图所在层
                                                           Sector selectSector,        // 选中的区域用Sector的经纬度表示
                                                           int levelNumber,            // 组成导出影像的瓦片图精细程度
                                                           String mimeType)             // 描述图像与经纬度关系的文件
                                                           throws Exception
        {

            int canvasWidth = 0;
            int canvasHeight = 0;

            if (selectSector == null)
            {
                String message = Logging.getMessage("nullValue.SectorIsNull");
                Logging.logger().severe(message);
                throw new IllegalArgumentException(message);
            }

            if (!layer.getLevels().getSector().intersects(selectSector))
            {
                String message = Logging.getMessage("generic.SectorRequestedOutsideCoverageArea",
                                                    selectSector,
                                                    layer.getLevels().getSector());
                Logging.logger().severe(message);
                throw new IllegalArgumentException(message);
            }

            Sector intersection = layer.getLevels().getSector().intersection(selectSector);

            if (levelNumber < 0)
            {
                levelNumber = layer.getLevels().getLastLevel().getLevelNumber();
            }
            else if (levelNumber > layer.getLevels().getLastLevel().getLevelNumber())
            {
                Logging.logger().warning(Logging.getMessage("generic.LevelRequestedGreaterThanMaxLevel",
                    levelNumber, layer.getLevels().getLastLevel().getLevelNumber()));
                levelNumber = layer.getLevels().getLastLevel().getLevelNumber();
            }

            int numTiles = 0;
            Angle minLatitude = Angle.fromDegrees(90);
            Angle maxLatitude = Angle.fromDegrees(-90);
            Angle minLongitude = Angle.fromDegrees(180);
            Angle maxLongitude = Angle.fromDegrees(-180);

            TextureTile[][] tiles = layer.getTilesInSector(intersection, levelNumber);
            if (tiles.length == 0 || tiles[0].length == 0)
            {
                Logging.logger().severe(Logging.getMessage("layers.TiledImageLayer.NoImagesAvailable"));
                return null;
            }

            for (TextureTile[] row : tiles) {
                for (TextureTile tile : row) {

                    if (tile.getSector().getMinLatitude().degrees < minLatitude.degrees)
                        minLatitude = tile.getSector().getMinLatitude();

                    if (tile.getSector().getMinLongitude().degrees < minLongitude.degrees)
                        minLongitude = tile.getSector().getMinLongitude();

                    if (tile.getSector().getMaxLatitude().degrees > maxLatitude.degrees)
                        maxLatitude = tile.getSector().getMaxLatitude();

                    if (tile.getSector().getMaxLongitude().degrees > maxLongitude.degrees)
                        maxLongitude = tile.getSector().getMaxLongitude();

                    numTiles ++;
                }
            }

            // 计算框选Sector包含tile所占范围Sector对应宽度，用像素表示
            if (numTiles != 0) {
                canvasWidth = tiles[0].length * layer.getLevels().getLevel(levelNumber).getTileWidth();
                canvasHeight = tiles.length * layer.getLevels().getLevel(levelNumber).getTileHeight();
            }

            // 按比例计算框选范围sector保存图像的场和宽
            double sectorWidth = selectSector.getDeltaLon().divide(maxLongitude.subtract(minLongitude)) * canvasWidth;
            double sectorHeight = selectSector.getDeltaLat().divide(maxLatitude.subtract(minLatitude)) * canvasHeight;
            double maxSide = sectorWidth > sectorHeight ? sectorWidth : sectorHeight;
            // 计算selectSector应该被切分成几分（横向）
            int numParts = (int)(maxSide / 8192) + 1;
            double deltaLatDegrees = selectSector.getDeltaLatDegrees() / numParts;
            double deltaLonDegress = selectSector.getDeltaLonDegrees() / numParts;

            ExportImagePackage []packages = new ExportImagePackage[numParts * numParts];

            for (int row = 0; row < numParts; ++row) {
                Angle subSectorMinLat = selectSector.getMinLatitude().addDegrees(row * deltaLatDegrees);
                Angle subSectorMaxLat = subSectorMinLat.addDegrees(deltaLatDegrees);

                for (int col = 0; col < numParts; ++col){
                    Angle subSectorMinLon = selectSector.getMinLongitude().addDegrees(col * deltaLonDegress);
                    Angle subSectorMaxLon = subSectorMinLon.addDegrees(deltaLonDegress);

                    Sector subSector = new Sector(subSectorMinLat, subSectorMaxLat, subSectorMinLon, subSectorMaxLon);

                    // BufferedImage image = new BufferedImage(8192, 8192, BufferedImage.TYPE_INT_RGB);
                    BufferedImage image = composeImageForSubSector(layer, subSector, (int)(sectorWidth / numParts),
                        (int)(sectorHeight / numParts),1.0, levelNumber, mimeType, true, null, 30000);

                    packages[row * numParts + col] = makeExportImage(row, col, subSector,
                        (int)(sectorWidth / numParts), (int)(sectorHeight / numParts), image);
                }
            }

            return packages;
        }

        ExportImagePackage makeExportImage(int row, int col, Sector sector, int xPixel, int yPixel,
            BufferedImage image) {

            String desktopPath = FileSystemView.getFileSystemView() .getHomeDirectory().getAbsolutePath();
            String imagePath = desktopPath + String.format("\\%d_%d.jpeg", row, col);
            String jgwPath = desktopPath + String.format("\\%d_%d.jgw", row, col);
            File file = new File(imagePath);
            File jgwFile = new File(jgwPath);

            String jgw = (new StringBuilder()).append(String.format("%4.17f%n", sector.getDeltaLonDegrees() / xPixel))
                .append(String.format("%d%n", 0))
                .append(String.format("%d%n", 0))
                .append(String.format("-%4.17f%n", sector.getDeltaLatDegrees() / yPixel))
                .append(String.format("%4.15f%n", sector.getMinLongitude().degrees))
                .append(String.format("%4.15f%n", sector.getMaxLatitude().degrees))
                .toString();

            return new ExportImagePackage(image, file, jgw, jgwFile);
        }


        private double[] readElevations(Sector sector, int width, int height)
        {
            double[] elevations;

            double latMin = sector.getMinLatitude().radians;
            double latMax = sector.getMaxLatitude().radians;
            double dLat = (latMax - latMin) / (double) (height - 1);

            double lonMin = sector.getMinLongitude().radians;
            double lonMax = sector.getMaxLongitude().radians;
            double dLon = (lonMax - lonMin) / (double) (width - 1);

            ArrayList<LatLon> latlons = new ArrayList<LatLon>(width * height);

            int maxx = width - 1, maxy = height - 1;

            double lat = latMin;
            for (int y = 0; y < height; y++)
            {
                double lon = lonMin;

                for (int x = 0; x < width; x++)
                {
                    latlons.add(LatLon.fromRadians(lat, lon));
                    lon = (x == maxx) ? lonMax : (lon + dLon);
                }

                lat = (y == maxy) ? latMax : (lat + dLat);
            }

            try
            {
                Globe globe = this.getWwd().getModel().getGlobe();
                ElevationModel model = globe.getElevationModel();

                elevations = new double[latlons.size()];
                Arrays.fill(elevations, MISSING_DATA_SIGNAL);

                // retrieve elevations
                model.composeElevations(sector, latlons, width, elevations);
            }
            catch (Exception e)
            {
                e.printStackTrace();
                elevations = null;
            }

            return elevations;
        }


        private void writeImageToFile(Sector sector, BufferedImage image, File gtFile)
            throws IOException
        {
//            AVList params = new AVListImpl();
//
//            params.setValue(AVKey.SECTOR, sector);
//            params.setValue(AVKey.COORDINATE_SYSTEM, AVKey.COORDINATE_SYSTEM_GEOGRAPHIC);
//            params.setValue(AVKey.PIXEL_FORMAT, AVKey.IMAGE);
//            params.setValue(AVKey.BYTE_ORDER, AVKey.BIG_ENDIAN);

            // 输出为JPEG
            ImageIO.write(image, "jpeg", gtFile);

//            GeotiffWriter writer = new GeotiffWriter(gtFile);
//            try
//            {
//                writer.write(BufferedImageRaster.wrapAsGeoreferencedRaster(image, params));
//            }
//            finally
//            {
//                writer.close();
//            }
        }

        private void writeElevationsToFile(Sector sector, IExportElevationStream stream, File gtFile)
            throws IOException
        {
            writeElevationsToFile(sector, stream.getWidth(), stream.getHeight(), stream.getBuffer(), gtFile);
        }

        private void writeElevationsToFile(Sector sector, int width, int height, double[] elevations, File gtFile)
            throws IOException
        {
            // These parameters are required for writeElevation
            AVList elev32 = new AVListImpl();

            elev32.setValue(AVKey.SECTOR, sector);
            elev32.setValue(AVKey.WIDTH, width);
            elev32.setValue(AVKey.HEIGHT, height);
            elev32.setValue(AVKey.COORDINATE_SYSTEM, AVKey.COORDINATE_SYSTEM_GEOGRAPHIC);
            elev32.setValue(AVKey.PIXEL_FORMAT, AVKey.ELEVATION);
            elev32.setValue(AVKey.DATA_TYPE, AVKey.FLOAT32);
            elev32.setValue(AVKey.ELEVATION_UNIT, AVKey.UNIT_METER);
            elev32.setValue(AVKey.BYTE_ORDER, AVKey.BIG_ENDIAN);
            elev32.setValue(AVKey.MISSING_DATA_SIGNAL, MISSING_DATA_SIGNAL);

            ByteBufferRaster raster = (ByteBufferRaster) ByteBufferRaster.createGeoreferencedRaster(elev32);
            // copy elevation values to the elevation raster
            int i = 0;
            for (int y = 0; y < height; y++)
            {
                for (int x = 0; x < width; x++)
                {
                    raster.setDoubleAtPosition(y, x, elevations[i++]);
                }
            }


            GeotiffWriter writer = new GeotiffWriter(gtFile);
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

    public static void main(String[] args)
    {
        // zoom to San Francisco downtown
        Configuration.setValue(AVKey.INITIAL_ALTITUDE, 1000d);
        Configuration.setValue(AVKey.INITIAL_LATITUDE, 37.7794d);
        Configuration.setValue(AVKey.INITIAL_LONGITUDE, -122.4192d);

        ApplicationTemplate.start("World Wind Exporting Surface Imagery and Elevations", AppFrame.class);
    }
}
