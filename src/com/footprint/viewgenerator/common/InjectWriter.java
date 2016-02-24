package com.footprint.viewgenerator.common;

import com.footprint.viewgenerator.model.Element;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.ArrayList;

public class InjectWriter extends WriteCommandAction.Simple {

    protected PsiFile mFile;
    protected Project mProject;
    protected PsiClass mClass;
    protected ArrayList<Element> mElements;
    protected PsiElementFactory mFactory;
    protected String mLayoutFileName;
    protected String mFieldNamePrefix;
    protected boolean mCreateHolder;

    public InjectWriter(PsiFile file, PsiClass clazz, String command, ArrayList<Element> elements, String layoutFileName, String fieldNamePrefix, boolean createHolder) {
        super(clazz.getProject(), command);

        mFile = file;
        mProject = clazz.getProject();
        mClass = clazz;
        mElements = elements;
        mFactory = JavaPsiFacade.getElementFactory(mProject);
        mLayoutFileName = layoutFileName;
        mFieldNamePrefix = fieldNamePrefix;
        mCreateHolder = createHolder;
    }

    @Override
    public void run() throws Throwable {

        if (mCreateHolder) {//目前永远为False
        } else {
            if (Utils.getInjectCount(mElements) > 0) {
                generateFields();
            }
            generateInitMethods();
            if (Utils.getClickCount(mElements) > 0) {
                generateClick();
            }
            Utils.showInfoNotification(mProject, String.valueOf(Utils.getInjectCount(mElements)) + " injections and " + String.valueOf(Utils.getClickCount(mElements)) + " onClick added to " + mFile.getName());
        }

        // reformat class
        JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(mProject);
        styleManager.optimizeImports(mFile);
        styleManager.shortenClassReferences(mClass);
        new ReformatCodeProcessor(mProject, mClass.getContainingFile(), null, false).runWithoutProgress();
    }

    //TODO 避免多次生成
    protected void generateClick() {
        StringBuilder method = new StringBuilder();
        if (Utils.getClickCount(mElements) > 0) {
            //添加接口实现
            PsiClass clickClass = getClickListenerClass();
            if (clickClass != null) {
                PsiJavaCodeReferenceElement ref = mFactory.createClassReferenceElement(clickClass);
                mClass.getImplementsList().add(ref);
            } else {
                System.out.println("Can't find View.OnClickListener!");
            }

            method.append("@Override \n");
            method.append("public void onClick(android.view.View view) {switch (view.getId()){\n");
            for (Element element : mElements) {
                if (element.isClick) {
                    method.append("case " + element.getFullID() + ": \nbreak;");
                }
            }
            method.append("}}");
            mClass.add(mFactory.createMethodFromText(method.toString(), mClass));
        }
    }

    private PsiClass getClickListenerClass() {
        GlobalSearchScope searchScope = GlobalSearchScope.allScope(mProject);
        JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(mProject);
        return javaPsiFacade.findClass("android.view.View.OnClickListener", searchScope);
    }

    /**
     * Create fields for injections inside main class
     */
    protected void generateFields() {
        // add injections into main class
        for (Element element : mElements) {
            if (!element.used) {
                continue;
            }

            StringBuilder injection = new StringBuilder();
            injection.append("protected ");
            injection.append(getFieldTypeName(element));
            injection.append(" ");
            injection.append(element.fieldName);
            injection.append(";");

            mClass.add(mFactory.createFieldFromText(injection.toString(), mClass));
        }
    }

    protected String getFieldTypeName(Element element) {
        if (element.typeName.equals("")) {
            if (element.nameFull != null && element.nameFull.length() > 0) { // custom package+class
                element.typeName = element.nameFull;
            } else if (Definitions.paths.containsKey(element.name)) { // listed class
                element.typeName = Definitions.paths.get(element.name);
            } else { // android.widget
                element.typeName = "android.widget." + element.name;
            }
        }

        return element.typeName;
    }

    StringBuilder stringBuilder = new StringBuilder();

    protected String generateFindViewByIdText(Element element) {
        stringBuilder.delete(0, stringBuilder.length());
        stringBuilder.append(element.fieldName)
                .append("=(")
                .append(element.typeName)
                .append(")findViewById(")
                .append(element.getFullID())
                .append(");");

        return stringBuilder.toString();
    }

    protected void generateInitMethods() {
        if (mClass.findMethodsByName("initView()", false).length == 0) {//不存在该方法
            // Add an empty stub of onCreate()
            StringBuilder method = new StringBuilder();
            method.append("private void initView() {\n");
            for (Element element : mElements) {
                method.append(generateFindViewByIdText(element)).append("\n");
            }
            method.append("}");
            mClass.add(mFactory.createMethodFromText(method.toString(), mClass));
        }
    }
}