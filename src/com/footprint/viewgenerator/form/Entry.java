package com.footprint.viewgenerator.form;

import com.footprint.viewgenerator.model.Element;
import com.footprint.viewgenerator.model.VGContext;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

public class Entry extends JPanel {

    protected EntryList mParent;
    protected Element mElement;
    protected VGContext mContext;

    // UI
    protected JCheckBox mCheck;
    protected JLabel mType;
    protected JLabel mID;
    protected JCheckBox mEvent;
    protected JTextField mName;
    protected Color mNameDefaultColor;
    protected Color mNameErrorColor = new Color(0x880000);

    public Entry(EntryList parent, Element element, VGContext context) {
        mElement = element;
        mParent = parent;
        mContext = context;

        mCheck = new JCheckBox();
        mCheck.setPreferredSize(new Dimension(40, 26));
        mCheck.addChangeListener(new CheckListener());

        mEvent = new JCheckBox();
        mEvent.setPreferredSize(new Dimension(100, 26));

        mType = new JLabel(mElement.name);
        mType.setPreferredSize(new Dimension(100, 26));

        mID = new JLabel(mElement.id);
        mID.setPreferredSize(new Dimension(100, 26));

        mName = new JTextField(mElement.fieldName, 10);
        mNameDefaultColor = mName.getBackground();
        mName.setPreferredSize(new Dimension(120, 26));
        mName.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                // empty
            }

            @Override
            public void focusLost(FocusEvent e) {
                syncElement();
            }
        });

        mCheck.setSelected(true);
        if (element.isDeclared) {
            mCheck.setEnabled(false);//默认选中且不能取消
        }

        if (mContext.getClickIdsList().contains(mElement.getFullID())) {
            mEvent.setSelected(true);
            mEvent.setEnabled(false);//默认选中且不能取消
        }

        if (mContext.getFieldNameList().contains(mElement.fieldName)) {
            mName.setEditable(false);//已经声明则不能编辑
        }

        setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
        setMaximumSize(new Dimension(Short.MAX_VALUE, 54));
        add(mCheck);
        add(Box.createRigidArea(new Dimension(10, 0)));
        add(mType);
        add(Box.createRigidArea(new Dimension(10, 0)));
        add(mID);
        add(Box.createRigidArea(new Dimension(10, 0)));
        add(mEvent);
        add(Box.createRigidArea(new Dimension(10, 0)));
        add(mName);
        add(Box.createHorizontalGlue());

        checkState();
    }

    public Element syncElement() {
        mElement.needDeal = mCheck.isSelected();
        mElement.isClick = mEvent.isSelected() && mElement.needDeal;//需要处理
        mElement.fieldName = mName.getText();

        if (mElement.checkValidity()) {
            mName.setBackground(mNameDefaultColor);
        } else {
            mName.setBackground(mNameErrorColor);
        }

        return mElement;
    }

    private void checkState() {
        if (mCheck.isSelected()) {
            mType.setEnabled(true);
            mID.setEnabled(true);
            mName.setEnabled(true);
        } else {
            mType.setEnabled(false);
            mID.setEnabled(false);
            mName.setEnabled(false);
        }
    }

    public class CheckListener implements ChangeListener {
        @Override
        public void stateChanged(ChangeEvent event) {
            checkState();
        }
    }
}
