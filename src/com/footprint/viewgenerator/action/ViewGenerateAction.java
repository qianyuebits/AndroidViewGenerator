package com.footprint.viewgenerator.action;

import com.footprint.viewgenerator.common.Definitions;
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
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilBase;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
//        //##############################调试代码##############################
//        PsiClass mClass = getTargetClass(editor, file);
//        PsiMethod[] initViews = mClass.findMethodsByName("initView", false);
//        if (initViews.length > 0) {
//            PsiMethod method = initViews[0];
//            final PsiCodeBlock body = method.getBody();
//            if (body != null) {
//                PsiStatement[] statements = body.getStatements();
//                for (PsiStatement psiStatement : statements) {
//                    String statementAsString = psiStatement.getText();
//                    System.out.println(statementAsString + "###");
//                    System.out.println(replaceBlank(statementAsString) + "@@@");
//                }
//            }
//        }
//        for (PsiField psiField : mClass.getAllFields()) {
//            System.out.println(psiField.getName());
//        }
//        System.out.println("@@" + mClass.getName());
//
//        PsiMethod onClick = mClass.findMethodsByName("onClick", false)[0];
//
//        for (PsiStatement statement : onClick.getBody().getStatements()) {
//            System.out.println(statement.getClass() + " " + statement.getText());
//            System.out.println("==========BBB");
//            PsiElement[] elements = statement.getChildren();
//            for(PsiElement psiStatement : elements){
//                if(psiStatement instanceof PsiCodeBlock) {
//                    System.out.println(psiStatement.getClass() + " " + psiStatement.getText() + " " + psiStatement.getTextOffset() + " " + psiStatement.getTextRange() + " " + psiStatement.getTextLength());
//
//                    System.out.println("==========AAA");
//                    PsiElement[] codes = psiStatement.getChildren();
//                    for(PsiElement pp : codes){
//                        psiStatement = pp;
//                        System.out.println(psiStatement.getClass() + " " + psiStatement.getText() + " " + psiStatement.getTextOffset() + " " + psiStatement.getTextRange() + " " + psiStatement.getTextLength());
//                    }
//
//                    break;
//                }
//            }
//        }
//        //############################################################
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

    protected HashMap<String, Element> elementsIdMap = new HashMap<String, Element>();

    //ID到变量名字的Map
    protected HashMap<String, String> elementsIdNameMap = new HashMap<String, String>();
    protected List<String> fieldNameList = new ArrayList<String>(8);

    /**
     * 预处理Element列表
     */
    private void preDealWithElements(PsiClass mClass, List<Element> mElements) {
        //解析实例化变量
        PsiMethod[] initViews = mClass.findMethodsByName("initView", false);
        if (initViews.length > 0) {
            PsiMethod method = initViews[0];
            final PsiCodeBlock body = method.getBody();
            if (body != null) {
                PsiStatement[] statements = body.getStatements();
                for (PsiStatement psiStatement : statements) {
                    String statementAsString = psiStatement.getText();
                    if (statementAsString.contains(Definitions.FindViewById)) {//声明语句
                        statementAsString = Utils.replaceBlank(statementAsString);
                        String fieldName = statementAsString.substring(0, statementAsString.indexOf("="));
                        String id = statementAsString.substring(statementAsString.indexOf("(R.id.") + 1, statementAsString.indexOf(");"));
                        elementsIdNameMap.put(id, fieldName);
                    }
                }
            }
        }
        //获取声明的所有变量名字
        fieldNameList.clear();
        for (PsiField psiField : mClass.getAllFields()) {
            fieldNameList.add(psiField.getName());
        }

        for (Element element : mElements) {
            elementsIdMap.put(element.getFullID(), element);
            //ID映射到的变量名称换成代码中的
            if (elementsIdNameMap.containsKey(element.getFullID())) {
                element.fieldName = elementsIdNameMap.get(element.getFullID());//这个名字可能被修改过，以修改过的为标准
                element.isInit = true;
            }
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
        preDealWithElements(clazz, elements);
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

        // get parent classes and check if it's an adapter
        boolean createHolder = false;
        PsiReferenceList list = clazz.getExtendsList();
        if (list != null) {
            for (PsiJavaCodeReferenceElement element : list.getReferenceElements()) {
                if (Definitions.adapters.contains(element.getQualifiedName())) {
                    createHolder = true;
                }
            }
        }

        EntryList panel = new EntryList(project, editor, elements, ids, createHolder, this, this);

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
            new InjectWriter(file, getTargetClass(editor, file), "Generate Injections", fieldNameList, elements, elementsIdMap, layout.getName(), fieldNamePrefix, createHolder).execute();
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
