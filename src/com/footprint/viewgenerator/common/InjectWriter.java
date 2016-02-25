package com.footprint.viewgenerator.common;

import com.footprint.viewgenerator.model.Element;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class InjectWriter extends WriteCommandAction.Simple {

    protected PsiFile mFile;
    protected Project mProject;
    protected PsiClass mClass;
    protected ArrayList<Element> mElements;
    protected PsiElementFactory mFactory;
    protected String mLayoutFileName;
    protected String mFieldNamePrefix;
    protected boolean mCreateHolder;

    protected HashMap<String, Element> elementsIdMap;
    protected List<String> fieldNameList;
    protected List<String> importStrList = new ArrayList<String>();
    protected PsiImportList importList;

    //是否已经添加OnClickListener监听
    protected boolean isClickClass = false;

    public InjectWriter(PsiFile file, PsiClass clazz, String command, List<String> fieldNameList, ArrayList<Element> elements, HashMap<String, Element> elementsIdMap, String layoutFileName, String fieldNamePrefix, boolean createHolder) {
        super(clazz.getProject(), command);

        mFile = file;
        mProject = clazz.getProject();
        mClass = clazz;
        mElements = elements;
        mFactory = JavaPsiFacade.getElementFactory(mProject);
        mLayoutFileName = layoutFileName;
        mFieldNamePrefix = fieldNamePrefix;
        mCreateHolder = createHolder;
        this.elementsIdMap = elementsIdMap;
        this.fieldNameList = fieldNameList;
        parsePsiClass();
    }

    /* 解析类相关数据 */
    private void parsePsiClass() {
        PsiReferenceList list = mClass.getImplementsList();
        if (list != null) {
            for (PsiJavaCodeReferenceElement element : list.getReferenceElements()) {
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

    protected void generateInitMethods() {
        if (mClass.findMethodsByName("initView", false).length == 0) {//不存在该方法
            // 添加initView()方法
            StringBuilder method = new StringBuilder();
            method.append("private void initView() {\n");
            for (Element element : mElements) {
                method.append(generateFindViewByIdText(element)).append("\n");
                if (element.isClick) {//添加监听
                    method.append(element.fieldName + ".setOnClickListener(" + mClass.getName() + ".this);");
                }
            }
            method.append("}");
            mClass.add(mFactory.createMethodFromText(method.toString(), mClass));

            addInitViewMethodInvoked();
        } else {
            System.out.println("initView() method has existed!");
        }

        System.out.println("InitView has finished!");
    }

    protected void generateClick() {
        StringBuilder method = new StringBuilder();
        if (Utils.getClickCount(mElements) > 0) {
            addViewClickListenerInterface();

            if (!isClickClass) {
                method.append("@Override \n");
                method.append("public void onClick(android.view.View view) {switch (view.getId()){\n");
                for (Element element : mElements) {
                    if (element.isClick) {
                        method.append("case " + element.getFullID() + ": \nbreak;");
                    }
                }
                method.append("}}");
                mClass.add(mFactory.createMethodFromText(method.toString(), mClass));
            } else {//TODO 之前已经添加过——这个处理起来太尼玛麻烦了
//                PsiMethod onClick = mClass.findMethodsByName("onClick", false)[0];
//                PsiStatement firstStatement = null;
//
//                List<Element> elements = getElementsListCopy();
//                for (PsiStatement statement : onClick.getBody().getStatements()) {
//                    if (statement instanceof PsiBreakStatement && firstStatement == null) {
//                        firstStatement = statement;
//                    }
//
//                    if (statement.getText().contains("R.id.")) {//TODO 这里判断有误
//                        String fullId = getIdFromCaseStatement(statement.getText());
//                        elements.remove(elementsIdMap.get(fullId));
//                    }
//                }
//
//                for (Element element : elements) {//补足缺失的Id
//                    if (element.isClick) {//TODO 这里创建也有问题
//                        onClick.getBody().addAfter(mFactory.createStatementFromText(
//                                "case " + element.getFullID() + ": break;", mClass), firstStatement);
//                    }
//                }
            }
        }
    }

    private StringBuilder statementBuilder = new StringBuilder();

    private String getIdFromCaseStatement(String text) {
        statementBuilder.delete(0, statementBuilder.length());
        StringBuilder stringBuilder = new StringBuilder(text);
        stringBuilder.delete(0, stringBuilder.indexOf(" ") + 1)
                .deleteCharAt(stringBuilder.length() - 1);
        return stringBuilder.toString();
    }

    /**
     * 只是放到另外一个List中去
     *
     * @return
     */
    private List<Element> getElementsListCopy() {
        List<Element> elementList = new ArrayList<Element>();
        elementList.addAll(mElements);
        return elementList;
    }

    //添加接口实现
    private void addViewClickListenerInterface() {
        if (!isClickClass) {
            PsiClass clickClass = getPsiClassByName(Definitions.ViewClickListener);
            if (clickClass != null) {
                PsiJavaCodeReferenceElement ref = mFactory.createClassReferenceElement(clickClass);
                mClass.getImplementsList().add(ref);
            } else {
                System.out.println("Can't find View.OnClickListener!");
            }
        }
    }

    private PsiClass getPsiClassByName(String cls) {
        GlobalSearchScope searchScope = GlobalSearchScope.allScope(mProject);
        JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(mProject);
        return javaPsiFacade.findClass(cls, searchScope);
    }

    /**
     * Create fields for injections inside main class
     */
    protected void generateFields() {
        // add injections into main class
        for (Element element : mElements) {
            if (!element.needDeal || fieldNameList.contains(element.fieldName)) {//没有勾选，或者同样名字的变量已经声明过了
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

        if (element.typeName.contains(".")) {
            if (!importStrList.contains(element.typeName)) {
                importList.add(mFactory.createImportStatement(getPsiClassByName(element.typeName)));
                importStrList.add(element.typeName);
            }
            element.typeName = element.typeName.substring(element.typeName.lastIndexOf(".") + 1, element.typeName.length());
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

    //添加InitView方法的调用
    private void addInitViewMethodInvoked() {
        PsiMethod onCreate = mClass.findMethodsByName("onCreate", false)[0];
        if (!containsInitViewMethodInvokedLine(onCreate, Definitions.InitViewMethodInvoked)) {
            for (PsiStatement statement : onCreate.getBody().getStatements()) {
                // Search for setContentView()
                if (statement.getFirstChild() instanceof PsiMethodCallExpression) {
                    PsiReferenceExpression methodExpression
                            = ((PsiMethodCallExpression) statement.getFirstChild())
                            .getMethodExpression();
                    // Insert ButterKnife.inject()/ButterKnife.bind() after setContentView()
                    if (methodExpression.getText().equals("setContentView")) {
                        onCreate.getBody().addAfter(mFactory.createStatementFromText(
                                Definitions.InitViewMethodInvoked, mClass), statement);
                        break;
                    }
                }
            }
        }
    }

    private boolean containsInitViewMethodInvokedLine(PsiMethod method, String line) {
        final PsiCodeBlock body = method.getBody();
        if (body == null) {
            return false;
        }
        PsiStatement[] statements = body.getStatements();
        for (PsiStatement psiStatement : statements) {
            String statementAsString = psiStatement.getText();
            if (psiStatement instanceof PsiExpressionStatement && (statementAsString.contains(line))) {
                return true;
            }
        }
        return false;
    }
}