package com.footprint.viewgenerator.iface;

import com.footprint.viewgenerator.model.Element;
import com.footprint.viewgenerator.model.VGContext;

import java.util.ArrayList;

public interface IConfirmListener {
    public void onConfirm(VGContext context, ArrayList<Element> elements, String fieldNamePrefix);
}
