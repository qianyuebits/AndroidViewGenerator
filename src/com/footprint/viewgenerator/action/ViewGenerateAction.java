package com.footprint.viewgenerator.action;

import com.footprint.viewgenerator.common.InjectWriter;
import com.footprint.viewgenerator.common.Utils;
import com.footprint.viewgenerator.form.EntryList;
import com.footprint.viewgenerator.iface.ICancelListener;
import com.footprint.viewgenerator.iface.IConfirmListener;
import com.footprint.viewgenerator.model.Element;
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

    public ViewGenerateAction(CodeInsightActionHandler handler) {
        super(handler);
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
        PsiFile layout = Utils.getLayoutFileFromCaret(editor, file);

        if (layout == null) {
            Utils.showErrorNotification(project, "No layout found");
            return; // no layout found
        }

        ArrayList<Element> elements = Utils.getIDsFromLayout(layout);
        if (!elements.isEmpty()) {
            showDialog(project, editor, elements);
        } else {
            Utils.showErrorNotification(project, "No IDs found in layout");
        }
    }

    protected void showDialog(Project project, Editor editor, ArrayList<Element> elements) {
        PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
        if (file == null) {
            return;
        }
        PsiClass clazz = getTargetClass(editor, file);

        if (clazz == null) {
            return;
        }

        // get already generated injections——这里需要进一步优化，去方法里面找相关的代码进行分析
        ArrayList<String> ids = new ArrayList<String>();
//        PsiField[] fields = clazz.getAllFields();
//        String[] annotations;
//        String id;
//
//        for (PsiField field : fields) {
//            annotations = field.getFirstChild().getText().split(" ");
//
//            for (String annotation : annotations) {
//                id = Utils.getInjectionID(butterKnife, annotation.trim());
//                if (!Utils.isEmptyString(id)) {
//                    ids.add(id);
//                }
//            }
//        }

        EntryList panel = new EntryList(project, editor, elements, ids, this, this);

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
    public void onConfirm(Project project, Editor editor, ArrayList<Element> elements, String fieldNamePrefix, boolean createHolder) {
        PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
        if (file == null) {
            return;
        }
        PsiFile layout = Utils.getLayoutFileFromCaret(editor, file);

        closeDialog();


        if (Utils.getInjectCount(elements) > 0 || Utils.getClickCount(elements) > 0) { // generate injections
            new InjectWriter(file, getTargetClass(editor, file), "Generate Injections", elements, layout.getName(), fieldNamePrefix, createHolder).execute();
        } else { // just notify user about no element selected
            Utils.showInfoNotification(project, "No injection was selected");
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
