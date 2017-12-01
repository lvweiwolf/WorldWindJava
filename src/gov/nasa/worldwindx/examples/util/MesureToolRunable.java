/*
 * Copyright (C) 2017 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration.
 * All Rights Reserved.
 */

package gov.nasa.worldwindx.examples.util;

import java.awt.*;
import java.io.*;
import java.lang.Runnable;
import java.util.*;

import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.avlist.*;
import gov.nasa.worldwind.data.ByteBufferRaster;
import gov.nasa.worldwind.formats.tiff.GeotiffWriter;
import gov.nasa.worldwind.geom.*;
import gov.nasa.worldwind.globes.*;
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.util.measure.MeasureTool;

import javax.swing.*;

/**
 * Created by lvwei on 2017/11/29.
 *
 * 导出高程数据线程处理
 */
public class MesureToolRunable implements Runnable
{
    private final WorldWindow wwd;
    private final MeasureTool measureTool;
    private final JDialog     jd;
    private final JPanel      toolPanel;
    private final File        file;


    public MesureToolRunable(WorldWindow wwdObject,
                             MeasureTool measureToolObject,
                             JDialog jdObject,
                             JPanel panelObject,
                             File file)
    {
        this.wwd = wwdObject;
        this.measureTool = measureToolObject;
        this.jd = jdObject;
        this.toolPanel = panelObject;
        this.file = file;
    }

    // 运行方法，此过程处理界面和数据
    public void run()
    {
        try
        {
            DrawContext dc = this.wwd.getSceneController().getDrawContext();
            Sector selectedSector = this.measureTool.getSurfaceShape().getSectors(dc).get(0);

            // 调整Sector矩形采样点，矩形最长边对应512个高程采样点
            int[] size = adjustSize(selectedSector, 512);
            int width = size[0], height = size[1];

            double[] elevations = readElevations(selectedSector, width, height);
            if (null != elevations)
            {
                jd.setTitle("Writing elevations to " + this.file.getName());
                writeElevationsToFile(selectedSector, width, height, elevations, this.file);
                jd.setVisible(false);
                JOptionPane.showMessageDialog(toolPanel,
                    "Elevations saved into the " + this.file.getName());
            }
            else
            {
                jd.setVisible(false);
                JOptionPane.showMessageDialog(toolPanel,
                    "Attempt to save elevations to the " + this.file.getName() + " has failed.");
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            jd.setVisible(false);
            JOptionPane.showMessageDialog(toolPanel, e.getMessage());
        }
        finally
        {
            SwingUtilities.invokeLater(new Runnable()
            {
                public void run()
                {
                    toolPanel.setCursor(Cursor.getDefaultCursor());
                    wwd.redraw();
                    jd.setVisible(false);
                }
            });
        }
    }

    // 调整采样点数量
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
                size[1] = minSize;          // height
            }
            else
            {
                size[0] = minSize;          // width
                size[1] = desiredSize;      // height
            }
        }

        return size;
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
            Globe globe = wwd.getModel().getGlobe();
            ElevationModel model = globe.getElevationModel();

            elevations = new double[latlons.size()];
            Arrays.fill(elevations, (double) Short.MIN_VALUE);

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
        elev32.setValue(AVKey.MISSING_DATA_SIGNAL, (double) Short.MIN_VALUE);

        ByteBufferRaster raster = (ByteBufferRaster) ByteBufferRaster.createGeoreferencedRaster(elev32);
        // copy elevation values to the elevation raster
        int i = 0;
        for (int y = height - 1; y >= 0; y--)
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
