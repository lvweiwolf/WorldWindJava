/*
 * Copyright (C) 2017 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration.
 * All Rights Reserved.
 */

package gov.nasa.worldwind.globes;

import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.util.BufferWrapper;

/**
 * Created by lvwei on 2017/12/14.
 */
public interface IExportElevationStream
{
    int getWidth();

    int getHeight();

    String getFileName();

    double[] getBuffer();

    BufferWrapper getBufferWrapper();

    void doSave() throws Exception;
}
