package com.footprint.viewgenerator.common;

import com.footprint.viewgenerator.model.Element;
import com.footprint.viewgenerator.model.VGContext;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.ArrayList;

public class InjectWriter extends WriteCommandAction.Simple {
    protected ArrayList<Element> mElements;
    protected PsiElementFactory mFactory;
    protected String mFieldNamePrefix;

    protected VGContext mContext;

    public InjectWriter(VGContext context, String command, ArrayList<Element> elements, String fieldNamePrefix) {
        super(context.getProject(), command);

        mElements = elements;
        mFactory = JavaPsiFacade.getElementFactory(context.getProject());
        mFieldNamePrefix = fieldNamePrefix;
        mContext = context;
    }

    @Override
    public void run() throws Throwable {
        if (mContext.ifCreateViewHolder()) {
            generateAdapter();
        } else {
            if (Utils.getInjectCount(mContext, mElements) > 0) {
                generateFields();
            }

            if (Utils.getClickCount(mContext, mElements) > 0) {
                generateClick();
            }

            generateInitMethods(getPsiClass());
            Utils.showInfoNotification(mContext.getProject(), "Generation Done");
        }

        // reformat class
        JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(mContext.getProject());
        styleManager.optimizeImports(mContext.getmFile());
        styleManager.shortenClassReferences(getPsiClass());
        new ReformatCodeProcessor(mContext.getProject(), getPsiClass().getContainingFile(), null, false).runWithoutProgress();
    }

    protected void generateInitMethods(PsiClass parentClass) {
        if (!Utils.ifClassContainsMethod(parentClass, "initView")) {//不存在该方法
            // 添加initView()方法
            StringBuilder method = new StringBuilder();
            if (mContext.isActivity()) {
                method.append("private void initView() {\n");
            } else {
                method.append("private void initView(View rootView) {\n");
            }
            for (Element element : mElements) {
                method.append(generateFindViewByIdText(element)).append("\n");
                if (element.isClick && !mContext.ifCreateViewHolder()) {//添加监听
                    method.append(element.fieldName + ".setOnClickListener(" + getPsiClass().getName() + ".this);");
                }
            }
            method.append("}");
            parentClass.add(mFactory.createMethodFromText(method.toString(), getPsiClass()));

            addInitViewMethodInvoked();
        } else {//已经有该方法，只需要在init后面插入即可
            PsiMethod initView = parentClass.findMethodsByName("initView", false)[0];
            PsiCodeBlock initViewBody = initView.getBody();
            for (Element element : mElements) {
                if (element.isInit) {//已经初始化了
                    if (element.isClick && !mContext.getClickViewNameList().contains(element.fieldName)) {
                        //重新添加的Click事件，遍历Body
                        for (PsiStatement psiStatement : initViewBody.getStatements()) {
                            if (Utils.replaceBlank(psiStatement.getText())
                                    .contains(element.fieldName + "=(")) {
                                initViewBody.addAfter(mFactory.createStatementFromText(element.fieldName + ".setOnClickListener(" + getPsiClass().getName() + ".this);", getPsiClass()), psiStatement);
                                break;
                            }
                        }
                    }
                } else {
                    initViewBody.addBefore(mFactory.createStatementFromText(generateFindViewByIdText(element), getPsiClass()), initViewBody.getLastBodyElement());
                    if (element.isClick) {
                        initViewBody.addBefore(mFactory.createStatementFromText(element.fieldName + ".setOnClickListener(" + getPsiClass().getName() + ".this);", getPsiClass()), initViewBody.getLastBodyElement());
                    }
                }
            }
        }
    }

    private PsiClass getPsiClass() {
        return mContext.getmClass();
    }

    protected void generateClick() {
        StringBuilder method = new StringBuilder();
        addViewClickListenerInterface();

        //没有onClick方法
        if (getPsiClass().findMethodsByName("onClick", false).length == 0) {
            method.append("@Override \n");
            method.append("public void onClick(android.view.View view) {\n");
            boolean isFirst = true;
            for (Element element : mElements) {
                if (element.isClick) {
                    if (isFirst) {
                        method.append("if(view.getId() == " + element.getFullID() + "){\n\n");
                        isFirst = false;
                    } else {
                        method.append("}else if(view.getId() == " + element.getFullID() + "){\n\n");
                    }
                }
            }
            method.append("}}");
            getPsiClass().add(mFactory.createMethodFromText(method.toString(), getPsiClass()));
        } else {
            PsiMethod onClick = getPsiClass().findMethodsByName("onClick", false)[0];
            PsiIfStatement psiIfStatement = null;
            for (PsiStatement statement : onClick.getBody().getStatements()) {
                if (statement instanceof PsiIfStatement) {
                    psiIfStatement = (PsiIfStatement) statement;
                    break;
                }
            }

            boolean isFirst = true;
            for (Element element : mElements) {
                if (element.isClick && !mContext.getClickIdsList().contains(element.getFullID())) {
                    if (isFirst) {
                        method.append("if(view.getId() == " + element.getFullID() + "){\n\n");
                        isFirst = false;
                    } else {
                        method.append("}else if(view.getId() == " + element.getFullID() + "){\n\n");
                    }
                }
            }

            if (method.length() > 0)
                method.append("}");

            if (psiIfStatement == null) {//有onClick方法，但是没有if语句
                onClick.getBody().addAfter(mFactory.createStatementFromText(method.toString(), getPsiClass()), onClick.getBody().getFirstBodyElement());
            } else {
                method.insert(0, psiIfStatement.getText() + " else ");
                psiIfStatement.replace(mFactory.createStatementFromText(method.toString(), getPsiClass()));
            }
        }
    }

    /**
     * Create ViewHolder for adapters with injections
     */
    protected void generateAdapter() {
        PsiClass holderClass = getPsiClass().findInnerClassByName(Utils.getViewHolderClassName(), true);
        if (holderClass != null) {
            holderClass.delete();
        }

        // view holder class
        StringBuilder holderBuilder = new StringBuilder();
        holderBuilder.append(Utils.getViewHolderClassName());
        holderBuilder.append("(android.view.View rootView) {");
        holderBuilder.append(Definitions.Other_InitViewMethodInvoked);
        holderBuilder.append("}");

        PsiClass viewHolder = mFactory.createClassFromText(holderBuilder.toString(), getPsiClass());
        viewHolder.setName(Utils.getViewHolderClassName());

        // add injections into main class
        StringBuilder injection = new StringBuilder();
        for (Element element : mElements) {
            if (mContext.getFieldNameList().contains(element.fieldName)) {//没有勾选，或者同样名字的变量已经声明过了
                continue;
            }

            injection.delete(0, injection.length());
            injection.append("protected ");
            injection.append(getFieldTypeName(element));
            injection.append(" ");
            injection.append(element.fieldName);
            injection.append(";");

            viewHolder.add(mFactory.createFieldFromText(injection.toString(), getPsiClass()));
        }

        generateInitMethods(viewHolder);
        getPsiClass().add(viewHolder);
        //添加static
        getPsiClass().addBefore(mFactory.createKeyword("static", getPsiClass()), getPsiClass().findInnerClassByName(Utils.getViewHolderClassName(), true));

        processAdapterGetViewMethod();
    }

    private void processAdapterGetViewMethod() {
        PsiMethod getView = getPsiClass().findMethodsByName("getView", false)[0];
        //已经生成过了
        if (Utils.replaceBlank(getView.getBody().getText()).contains("ViewHolderviewHolder=null;")) {
            return;
        }

        String layoutStatement = null;
        for (PsiStatement statement : getView.getBody().getStatements()) {
            if (Utils.isLayoutStatement(statement)) {
                layoutStatement = statement.getText();
                statement.replace(mFactory.createStatementFromText("View view = convertView;", getPsiClass()));
            }
            if (statement instanceof PsiReturnStatement) {
                //设置语句
                getView.getBody().addBefore(mFactory.createStatementFromText("ViewHolder viewHolder = null;", getPsiClass()), statement);
                getView.getBody().addBefore(mFactory.createStatementFromText(getViewHolderCreateStr(layoutStatement), getPsiClass()), statement);
                statement.replace(mFactory.createStatementFromText("return view;", getPsiClass()));
            }
        }
    }

    private String getViewHolderCreateStr(String layoutStatement) {
        return "if(view == null || !(view.getTag() instanceof ViewHolder)){view = LayoutInflater.from(parent.getContext()).inflate(" +
                Utils.getIdFromLayoutStatement(layoutStatement) +
                ", null);viewHolder = new ViewHolder(view);view.setTag(viewHolder);}else{viewHolder = (ViewHolder)view.getTag();}";
    }

    //添加接口实现
    private void addViewClickListenerInterface() {
        if (!mContext.isClickClass()) {
            PsiClass clickClass = getPsiClassByName(Definitions.ViewClickListener);
            if (clickClass != null) {
                PsiJavaCodeReferenceElement ref = mFactory.createClassReferenceElement(clickClass);
                getPsiClass().getImplementsList().add(ref);
            } else {
                Utils.showErrorNotification(mContext.getProject(), "Can't find View.OnClickListener!");
            }
        }
    }

    private PsiClass getPsiClassByName(String cls) {
        GlobalSearchScope searchScope = GlobalSearchScope.allScope(mContext.getProject());
        JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(mContext.getProject());
        return javaPsiFacade.findClass(cls, searchScope);
    }

    /**
     * Create fields for injections inside main class
     */
    protected void generateFields() {
        // add injections into main class
        StringBuilder injection = new StringBuilder();

        if (!mContext.isActivity() && !mContext.getFieldNameList().contains("rootView")) {
            injection.delete(0, injection.length());
            injection.append("protected View rootView;");
            getPsiClass().add(mFactory.createFieldFromText(injection.toString(), getPsiClass()));
        }

        for (Element element : mElements) {
            if (element.isDeclared) {//没有勾选，或者同样名字的变量已经声明过了
                continue;
            }

            injection.delete(0, injection.length());
            injection.append("protected ");
            injection.append(getFieldTypeName(element));
            injection.append(" ");
            injection.append(element.fieldName);
            injection.append(";");

            getPsiClass().add(mFactory.createFieldFromText(injection.toString(), getPsiClass()));
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
            if (!mContext.getImportStrList().contains(element.typeName)) {
                PsiClass importClass = getPsiClassByName(element.typeName);
                if (importClass != null) {
                    mContext.getImportList().add(mFactory.createImportStatement(importClass));
                    mContext.getImportStrList().add(element.typeName);
                }
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
                .append(getFieldTypeName(element));

        if (mContext.isActivity()) {
            stringBuilder.append(")findViewById(");
        } else {
            stringBuilder.append(")rootView.findViewById(");
        }

        stringBuilder.append(element.getFullID())
                .append(");");

        return stringBuilder.toString();
    }

    //添加InitView方法的调用
    private void addInitViewMethodInvoked() {
        //Activity处理
        if (mContext.isActivity()) {
            PsiMethod onCreate = getPsiClass().findMethodsByName("onCreate", false)[0];
            if (!containsInitViewMethodInvokedLine(onCreate, Definitions.Activity_InitViewMethodInvoked)) {
                for (PsiStatement statement : onCreate.getBody().getStatements()) {
                    if (Utils.isLayoutStatement(statement)) {//
                        statement.replace(mFactory.createStatementFromText("super.setContentView(" + Utils.getIdFromLayoutStatement(statement.getText()) + ");", getPsiClass()));
                        onCreate.getBody().addBefore(mFactory.createStatementFromText(
                                Definitions.Activity_InitViewMethodInvoked, getPsiClass()), onCreate.getBody().getLastBodyElement());
                    }
                }
            }
        } else if (mContext.isFragment()) {
            PsiMethod onCreateView = getPsiClass().findMethodsByName("onCreateView", false)[0];
            if (!containsInitViewMethodInvokedLine(onCreateView, Definitions.Other_InitViewMethodInvoked)) {
                boolean isReturnMode = false;
                for (PsiStatement statement : onCreateView.getBody().getStatements()) {
                    //解析 return R.layout.activity.main
                    if (statement instanceof PsiReturnStatement) {
                        String returnValue = ((PsiReturnStatement) statement).getReturnValue().getText();
                        if (returnValue.contains("R.layout")) {
                            onCreateView.getBody().addBefore(mFactory.createStatementFromText("rootView = inflater.inflate(" + Utils.getIdFromLayoutStatement(returnValue) + ", null);", getPsiClass()), statement);
                            onCreateView.getBody().addBefore(mFactory.createStatementFromText(Definitions.Other_InitViewMethodInvoked, getPsiClass()), statement);
                            statement.replace(mFactory.createStatementFromText("return rootView;", getPsiClass()));
                            isReturnMode = true;
                        }
                        break;
                    }
                }

                if (!isReturnMode) {
                    for (PsiStatement statement : onCreateView.getBody().getStatements()) {
                        /*
                         * 解析
                         *
                         * R.layout.activity.main
                         * return null
                         * */
                        if (Utils.isLayoutStatement(statement)) {
                            statement.replace(mFactory.createStatementFromText("rootView = inflater.inflate("
                                    + Utils.getIdFromLayoutStatement(statement.getText()) + ", null);", getPsiClass()));
                        }

                        if (statement instanceof PsiReturnStatement) {
                            //设置语句
                            onCreateView.getBody().addBefore(mFactory.createStatementFromText("initView(rootView);", getPsiClass()), statement);
                            statement.replace(mFactory.createStatementFromText("return rootView;", getPsiClass()));
                        }
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