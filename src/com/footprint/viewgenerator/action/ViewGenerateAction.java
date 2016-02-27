package com.footprint.viewgenerator.action;

import com.footprint.viewgenerator.common.InjectWriter;
import com.footprint.viewgenerator.common.Utils;
import com.footprint.viewgenerator.form.EntryList;
import com.footprint.viewgenerator.iface.ICancelListener;
import com.footprint.viewgenerator.iface.IConfirmListener;
import com.footprint.viewgenerator.model.Element;
import com.footprint.viewgenerator.model.VGContext;
import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;

import javax.swing.*;
import java.util.ArrayList;

/**
 * Created by liquanmin on 16/2/24.
 */
public class ViewGenerateAction extends BaseGenerateAction implements IConfirmListener, ICancelListener {
    protected JFrame mDialog;
    protected VGContext context;

    public ViewGenerateAction() {
        super(new CodeInsightActionHandler() {
            @Override
            public void invoke(Project project, Editor editor, PsiFile psiFile) {

            }

            @Override
            public boolean startInWriteAction() {
                return false;
            }
        });
    }

    public void actionPerformed(AnActionEvent event) {
        Project project = event.getData(PlatformDataKeys.PROJECT);
        Editor editor = event.getData(PlatformDataKeys.EDITOR);

        actionPerformedImpl(project, editor);
    }

    @Override
    public void actionPerformedImpl(Project project, Editor editor) {
        super.actionPerformedImpl(project, editor);
        PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
        if (file == null) {
            return;
        }

        PsiFile layout = Utils.getLayoutFileFromCaret(editor, file);
        if (layout == null) {
            Utils.showErrorNotification(project, "No layout found");
            return;
        }

        PsiClass clazz = getTargetClass(editor, file);
        if (clazz == null) {
            Utils.showErrorNotification(project, "No class found");
            return;
        }

        ArrayList<Element> elements = Utils.getIDsFromLayout(layout);
        if (elements.isEmpty()) {
            Utils.showErrorNotification(project, "No IDs found in layout");
            return;
        }

        context = new VGContext(project, file, layout, clazz);
        context.parseClass();
        context.preDealWithElements(elements);
        showDialog(elements);
    }

    protected void showDialog(ArrayList<Element> elements) {
        EntryList panel = new EntryList(context, elements, this, this);
        mDialog = new JFrame();
        mDialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        mDialog.getRootPane().setDefaultButton(panel.getConfirmButton());
        mDialog.getContentPane().add(panel);
        mDialog.pack();
        mDialog.setLocationRelativeTo(null);
        mDialog.setVisible(true);
    }

    @Override
    public void onCancel() {
        closeDialog();
    }

    @Override
    public void onConfirm(VGContext context, ArrayList<Element> elements, String fieldNamePrefix) {
        closeDialog();
        Utils.dealElementList(elements);
        if (Utils.getInjectCount(context, elements) > 0 || Utils.getClickCount(context, elements) > 0) { // generate injections
            new InjectWriter(context, "Generate Injections", elements, fieldNamePrefix).execute();
        } else { // just notify user about no element selected
            Utils.showInfoNotification(context.getProject(), "No injection was selected");
        }
    }

    protected void closeDialog() {
        if (mDialog == null) {
            return;
        }

        mDialog.setVisible(false);
        mDialog.dispose();
    }
}
