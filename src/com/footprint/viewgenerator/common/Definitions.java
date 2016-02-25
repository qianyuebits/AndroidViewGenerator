package com.footprint.viewgenerator.common;

import java.util.ArrayList;
import java.util.HashMap;

public class Definitions {

    public static final HashMap<String, String> paths = new HashMap<String, String>();
    public static final ArrayList<String> adapters = new ArrayList<String>();
    public static final String ViewClickListener = "android.view.View.OnClickListener";
    public static final String InitViewMethodInvoked = "initView();";
    public static final String FindViewById = "findViewById(";
    public static final String IMPORT = "import ";

    static {
        // special classes; default package is android.widget.*
        paths.put("WebView", "android.webkit.WebView");
        paths.put("View", "android.view.View");

        // adapters
        adapters.add("android.widget.ListAdapter");
        adapters.add("android.widget.ArrayAdapter");
        adapters.add("android.widget.BaseAdapter");
        adapters.add("android.widget.HeaderViewListAdapter");
        adapters.add("android.widget.SimpleAdapter");
        adapters.add("android.support.v4.widget.CursorAdapter");
        adapters.add("android.support.v4.widget.SimpleCursorAdapter");
        adapters.add("android.support.v4.widget.ResourceCursorAdapter");
    }
}
