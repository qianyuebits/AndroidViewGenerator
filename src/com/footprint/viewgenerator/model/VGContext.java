package com.footprint.viewgenerator.model;

import com.footprint.viewgenerator.common.Definitions;
import com.footprint.viewgenerator.common.Utils;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.EverythingGlobalScope;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by liquanmin on 16/2/27.
 */
public class VGContext {
    //ID->Element: 从Layout中获取
    private HashMap<String, Element> elementsIdMap = new HashMap<String, Element>();

    //ID->Name: 从initView中获取
    private HashMap<String, String> elementsIdNameMap = new HashMap<String, String>();

    //已经声明的属性变量名字
    private List<String> fieldNameList = new ArrayList<String>(8);

    //已经在onClick方法中有Id的变量，等效于添加了监听的变量
    private List<String> clickIdsList = new ArrayList<String>(8);

    //所有添加监听的View属性名字到Statement的映射
    private List<String> clickViewNameList = new ArrayList<String>(8);

    //所有import的包.类名
    protected List<String> importStrList = new ArrayList<String>();

    protected PsiImportList importList;

    //是否已经添加OnClickListener监听
    protected boolean isClickClass = false;

    //是否需要创建ViewHolder
    protected boolean ifCreateViewHolder = false;

    //是否是Adapter类
    private boolean isAdapter = false;

    private PsiClass activityClass;
    private PsiClass fragmentClass;
    private PsiClass supportFragmentClass;

    private PsiClass mClass;
    private PsiFile mFile;
    private PsiFile mLayoutFile;
    private Project mProject;

    public VGContext(Project mProject, PsiFile psiFile, PsiFile layoutFile, PsiClass mClass) {
        this.mClass = mClass;
        this.mProject = mProject;
        this.mFile = psiFile;
        this.mLayoutFile = layoutFile;
    }

    /**
     * 预处理Element列表
     */
    public void preDealWithElements(List<Element> mElements) {
        //解析实例化变量
        PsiMethod[] initViews = mClass.findMethodsByName("initView", false);
        elementsIdNameMap.clear();
        clickViewNameList.clear();
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

                    if (statementAsString.contains(Definitions.SetOnClickListener)) {//监听语句
                        statementAsString = Utils.replaceBlank(statementAsString);
                        String fieldName = statementAsString.substring(0, statementAsString.indexOf(Definitions.SetOnClickListener));
                        clickViewNameList.add(fieldName);
                    }
                }
            }
        }

        //解析click变量
        PsiMethod[] onClicks = mClass.findMethodsByName("onClick", false);
        clickIdsList.clear();
        if (onClicks.length > 0) {
            PsiMethod method = onClicks[0];
            final PsiCodeBlock body = method.getBody();
            if (body != null) {
                PsiStatement[] statements = body.getStatements();
                for (PsiStatement psiStatement : statements) {
                    if (psiStatement instanceof PsiIfStatement) {
                        String ifText = Utils.replaceBlank(psiStatement.getText());
                        StringBuilder stringBuilder = new StringBuilder();
                        for (Element element : mElements) {
                            Utils.clearStringBuilder(stringBuilder);
                            stringBuilder.append("if(view.getId()==")
                                    .append(element.getFullID())
                                    .append(")");

                            if (ifText.contains(stringBuilder.toString())) {
                                clickIdsList.add(element.getFullID());
                                element.isClick = true;
                            }
                        }
                        break;
                    }
                }
            }
        }

        //获取声明的所有变量名字
        fieldNameList.clear();
        for (PsiField psiField : mClass.getAllFields()) {
            fieldNameList.add(psiField.getName());
        }

        elementsIdMap.clear();
        for (Element element : mElements) {
            elementsIdMap.put(element.getFullID(), element);
            //ID映射到的变量名称换成代码中的
            if (elementsIdNameMap.containsKey(element.getFullID())) {
                element.fieldName = elementsIdNameMap.get(element.getFullID());//这个名字可能被修改过，以修改过的为标准
                element.isInit = true;
            }

            //有同样名字的变量就认为是声明过的
            if (fieldNameList.contains(element.fieldName)) {
                element.isDeclared = true;
            }
        }
    }

    public void parseClass() {
        PsiReferenceList extendsList = mClass.getExtendsList();
        if (extendsList != null) {
            for (PsiJavaCodeReferenceElement element : extendsList.getReferenceElements()) {
                if (Definitions.adapters.contains(element.getQualifiedName())) {
                    isAdapter = true;
                    ifCreateViewHolder = true;
                    break;
                }
            }
        }

        PsiReferenceList implementsList = mClass.getImplementsList();
        if (implementsList != null) {
            for (PsiJavaCodeReferenceElement element : implementsList.getReferenceElements()) {
                if (Definitions.ViewClickListener.equals(element.getQualifiedName())) {
                    isClickClass = true;
                    break;
                }
            }
        }

        importList = ((PsiJavaFile) mClass.getContainingFile()).getImportList();
        PsiImportStatement[] importStatements = importList.getImportStatements();
        importStrList.clear();
        StringBuilder stringBuilder = new StringBuilder();
        for (PsiImportStatement statement : importStatements) {
            stringBuilder.delete(0, stringBuilder.length());
            stringBuilder.append(statement.getText());
            stringBuilder.delete(0, Definitions.IMPORT.length());
            stringBuilder.delete(stringBuilder.length() - 1, stringBuilder.length());
            importStrList.add(stringBuilder.toString());
        }

        activityClass = JavaPsiFacade.getInstance(mProject).findClass(
                "android.app.Activity", new EverythingGlobalScope(mProject));
        fragmentClass = JavaPsiFacade.getInstance(mProject).findClass(
                "android.app.Fragment", new EverythingGlobalScope(mProject));
        supportFragmentClass = JavaPsiFacade.getInstance(mProject).findClass(
                "android.support.v4.app.Fragment", new EverythingGlobalScope(mProject));
    }

    public HashMap<String, Element> getElementsIdMap() {
        return elementsIdMap;
    }

    public HashMap<String, String> getElementsIdNameMap() {
        return elementsIdNameMap;
    }

    public List<String> getFieldNameList() {
        return fieldNameList;
    }

    public List<String> getClickIdsList() {
        return clickIdsList;
    }

    public boolean isAdapter() {
        return isAdapter;
    }

    public boolean ifCreateViewHolder() {
        return ifCreateViewHolder;
    }

    public void setIfCreateViewHolder(boolean ifCreateViewHolder) {
        this.ifCreateViewHolder = ifCreateViewHolder;
    }

    public List<String> getImportStrList() {
        return importStrList;
    }

    public PsiImportList getImportList() {
        return importList;
    }

    public List<String> getClickViewNameList() {
        return clickViewNameList;
    }

    public PsiFile getmFile() {
        return mFile;
    }

    public PsiFile getmLayoutFile() {
        return mLayoutFile;
    }

    public boolean isClickClass() {
        return isClickClass;
    }

    public PsiClass getmClass() {
        return mClass;
    }

    public Project getProject() {
        return mProject;
    }

    public boolean isActivity() {
        return activityClass != null && mClass.isInheritor(activityClass, true);
    }

    public boolean isFragment() {
        return (fragmentClass != null && mClass.isInheritor(fragmentClass, true)) || (supportFragmentClass != null && mClass.isInheritor(supportFragmentClass, true));
    }

    public boolean containsMethod(String methodName) {
        return Utils.ifClassContainsMethod(mClass, methodName);
    }
}
