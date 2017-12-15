/*
 * Project:  WTC Collector
 * Purpose:  Mobile application for WTC data collection.
 * Author:   NikitaFeodonit, nfeodonit@yandex.com
 * ****************************************************************************
 * Copyright (c) 2017 NextGIS, info@nextgis.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextgis.wtc_collector.util;

public interface AppConstants
{
    String APP_TAG = "WTC Collector";

    /**
     * init sync status
     */
    String BROADCAST_MESSAGE = "wtc.collector.sync_message";
    String KEY_STEP          = "sync_step";
    String KEY_STATE         = "sync_state";
    String KEY_MESSAGE       = "sync_message";

    int STEP_STATE_WAIT   = 0;
    int STEP_STATE_WORK   = 1;
    int STEP_STATE_DONE   = 2;
    int STEP_STATE_FINISH = 3;
    int STEP_STATE_ERROR  = 4;
    int STEP_STATE_CANCEL = 5;

    /**
     * Fragments tags
     */
    String FRAGMENT_SETTINGS_HEADER_FRAGMENT = "settings_header_fragment";
    String FRAGMENT_SETTINGS_FRAGMENT        = "settings_fragment";

    int DEFAULT_COORDINATES_FRACTION_DIGITS = 6;

    String KEY_ZMUDATA = "wtc_zmudata";
    String KEY_PEOPLE  = "wtc_people";
    String KEY_SPECIES = "wtc_species";
    String KEY_TRACKS  = "wtc_tracks";

    String KEY_LAYER_ZMUDATA = "zmudata";
    String KEY_LAYER_PEOPLE  = "people";
    String KEY_LAYER_SPECIES = "species";
    String KEY_LAYER_TRACKS  = "tracks";

    String FIELD_ZMUDATA_GUID      = "GUID"; // STRING
    String FIELD_ZMUDATA_LAT       = "LAT"; // REAL
    String FIELD_ZMUDATA_LON       = "LON"; // REAL
    String FIELD_ZMUDATA_DATE      = "DATE"; // DATE
    String FIELD_ZMUDATA_TIME      = "TIME"; // TIME
    String FIELD_ZMUDATA_SPECIES   = "SPECIES"; // STRING
    String FIELD_ZMUDATA_COLLECTOR = "COLLECTOR"; // STRING

    String FIELD_TRACKS_LAT       = "LAT"; // REAL
    String FIELD_TRACKS_LON       = "LON"; // REAL
    String FIELD_TRACKS_TIMESTAMP = "TIMESTAMP"; // DATETIME
    String FIELD_TRACKS_COLLECTOR = "COLLECTOR"; // STRING
}