package com.footprint.viewgenerator.common;

import com.footprint.viewgenerator.model.Element;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.EverythingGlobalScope;
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

    PsiClass activityClass;
    PsiClass fragmentClass;
    PsiClass supportFragmentClass;

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

        activityClass = JavaPsiFacade.getInstance(mProject).findClass(
                "android.app.Activity", new EverythingGlobalScope(mProject));
        fragmentClass = JavaPsiFacade.getInstance(mProject).findClass(
                "android.app.Fragment", new EverythingGlobalScope(mProject));
        supportFragmentClass = JavaPsiFacade.getInstance(mProject).findClass(
                "android.support.v4.app.Fragment", new EverythingGlobalScope(mProject));
    }

    @Override
    public void run() throws Throwable {
        if (mCreateHolder) {//目前永远为False
            generateAdapter();
        } else {
            if (Utils.getInjectCount(mElements) > 0) {
                generateFields();
            }
            generateInitMethods(mClass);
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

    protected void generateInitMethods(PsiClass parentClass) {
        if (mClass.findMethodsByName("initView", false).length == 0) {//不存在该方法
            // 添加initView()方法
            StringBuilder method = new StringBuilder();
            if (isActivity()) {
                method.append("private void initView() {\n");
            } else {
                method.append("private void initView(View rootView) {\n");
            }
            for (Element element : mElements) {
                if (!element.needDeal) {
                    continue;
                }

                method.append(generateFindViewByIdText(element)).append("\n");
                if (element.isClick && !mCreateHolder) {//添加监听
                    method.append(element.fieldName + ".setOnClickListener(" + mClass.getName() + ".this);");
                }
            }
            method.append("}");
            parentClass.add(mFactory.createMethodFromText(method.toString(), mClass));

            addInitViewMethodInvoked();
        } else {//已经有该方法

        }
    }

    protected void generateClick() {
        StringBuilder method = new StringBuilder();
        if (Utils.getClickCount(mElements) > 0) {
            addViewClickListenerInterface();

            if (!isClickClass) {
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
                mClass.add(mFactory.createMethodFromText(method.toString(), mClass));
            } else {//TODO 之前已经添加过——这个处理起来太尼玛麻烦了
            }
        }
    }

    /**
     * Create ViewHolder for adapters with injections
     */
    protected void generateAdapter() {
        if (mClass.findInnerClassByName(Utils.getViewHolderClassName(), true) != null) {
            Utils.showInfoNotification(mProject, "ViewHolder已经存在");
            return;
        }

        // view holder class
        StringBuilder holderBuilder = new StringBuilder();
        holderBuilder.append(Utils.getViewHolderClassName());
        holderBuilder.append("(android.view.View rootView) {");
        holderBuilder.append("initView(rootView);");
        holderBuilder.append("}");

        PsiClass viewHolder = mFactory.createClassFromText(holderBuilder.toString(), mClass);
        viewHolder.setName(Utils.getViewHolderClassName());

        // add injections into main class
        StringBuilder injection = new StringBuilder();
        for (Element element : mElements) {
            if (!element.needDeal || fieldNameList.contains(element.fieldName)) {//没有勾选，或者同样名字的变量已经声明过了
                continue;
            }

            injection.delete(0, injection.length());
            injection.append("protected ");
            injection.append(getFieldTypeName(element));
            injection.append(" ");
            injection.append(element.fieldName);
            injection.append(";");

            viewHolder.add(mFactory.createFieldFromText(injection.toString(), mClass));
        }

        generateInitMethods(viewHolder);
        mClass.add(viewHolder);
        //添加static
        mClass.addBefore(mFactory.createKeyword("static", mClass), mClass.findInnerClassByName(Utils.getViewHolderClassName(), true));

        processAdapterGetViewMethod();
    }

    private void processAdapterGetViewMethod() {
        PsiMethod getView = mClass.findMethodsByName("getView", false)[0];
        String layoutStatement = null;
        for (PsiStatement statement : getView.getBody().getStatements()) {
            if (Utils.isLayoutStatement(statement)) {
                layoutStatement = statement.getText();
                statement.replace(mFactory.createStatementFromText("View view = convertView;", mClass));
            }
            if (statement instanceof PsiReturnStatement) {
                //设置语句
                getView.getBody().addBefore(mFactory.createStatementFromText("ViewHolder viewHolder = null;", mClass), statement);
                getView.getBody().addBefore(mFactory.createStatementFromText(getViewHolderCreateStr(layoutStatement), mClass), statement);
                statement.replace(mFactory.createStatementFromText("return view;", mClass));
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
        StringBuilder injection = new StringBuilder();

        if (!isActivity() && !fieldNameList.contains("rootView")) {
            injection.delete(0, injection.length());
            injection.append("protected View rootView;");
            mClass.add(mFactory.createFieldFromText(injection.toString(), mClass));
        }

        for (Element element : mElements) {
            if (!element.needDeal || fieldNameList.contains(element.fieldName)) {//没有勾选，或者同样名字的变量已经声明过了
                continue;
            }

            injection.delete(0, injection.length());
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
                .append(getFieldTypeName(element));

        if (isActivity()) {
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
        if (isActivity()) {
            PsiMethod onCreate = mClass.findMethodsByName("onCreate", false)[0];
            if (!containsInitViewMethodInvokedLine(onCreate, Definitions.InitViewMethodInvoked)) {
                for (PsiStatement statement : onCreate.getBody().getStatements()) {
                    if (Utils.isLayoutStatement(statement)) {//
                        statement.replace(mFactory.createStatementFromText("super.setContentView(" + Utils.getIdFromLayoutStatement(statement.getText()) + ");", mClass));
                        onCreate.getBody().addBefore(mFactory.createStatementFromText(
                                Definitions.InitViewMethodInvoked, mClass), onCreate.getBody().getLastBodyElement());
                    }
                }
            }
        } else if (isFragment()) {
            PsiMethod onCreateView = mClass.findMethodsByName("onCreateView", false)[0];
            if (!containsInitViewMethodInvokedLine(onCreateView, Definitions.InitViewMethodInvoked)) {
                boolean isReturnMode = false;
                for (PsiStatement statement : onCreateView.getBody().getStatements()) {
                    //解析 return R.layout.activity.main
                    if (statement instanceof PsiReturnStatement) {
                        String returnValue = ((PsiReturnStatement) statement).getReturnValue().getText();
                        if (returnValue.contains("R.layout")) {
                            onCreateView.getBody().addBefore(mFactory.createStatementFromText("rootView = inflater.inflate(" + Utils.getIdFromLayoutStatement(returnValue) + ", null);", mClass), statement);
                            onCreateView.getBody().addBefore(mFactory.createStatementFromText("initView(rootView);", mClass), statement);
                            statement.replace(mFactory.createStatementFromText("return rootView;", mClass));
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
                                    + Utils.getIdFromLayoutStatement(statement.getText()) + ", null);", mClass));
                        }

                        if (statement instanceof PsiReturnStatement) {
                            //设置语句
                            onCreateView.getBody().addBefore(mFactory.createStatementFromText("initView(rootView);", mClass), statement);
                            statement.replace(mFactory.createStatementFromText("return rootView;", mClass));
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

    private boolean isActivity() {
        return activityClass != null && mClass.isInheritor(activityClass, true);
    }

    private boolean isFragment() {
        return (fragmentClass != null && mClass.isInheritor(fragmentClass, true)) || (supportFragmentClass != null && mClass.isInheritor(supportFragmentClass, true));
    }
}