package com.android.example.lintjat;

import com.android.example.lintjat.Utils.FilleUtils;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UTryExpression;
import org.jetbrains.uast.UastUtils;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 定义代码检查规则
 * 这个是针对try和catch中的finally是否包涵close方法进行一个判断
 * 由于要对java代码进行扫描,因此继承的是JavaScanner的接口
 * </p>
 * created by OuyangPeng at 2017/8/31 9:42
 */
public class XTCCloseDetector extends Detector implements Detector.UastScanner {
    private  static FilleUtils filleUtils;
    private static final String ISSUE_ID = "XTC_CloseError";
    private static final String ISSUE_DESCRIPTION = "错误:close方法应该在finally中调用";
    private static final String ISSUE_EXPLANATION = "错误:为了防止导致内存泄漏，close方法应该在finally中调用";

    private static final Category ISSUE_CATEGORY = Category.CORRECTNESS;
    private static final int ISSUE_PRIORITY = 6;
    private static final Severity ISSUE_SEVERITY = Severity.ERROR;

    private static final Class<? extends Detector> DETECTOR_CLASS = XTCCloseDetector.class;
    private static final EnumSet<Scope> DETECTOR_SCOPE = Scope.JAVA_FILE_SCOPE;
    private static final Implementation IMPLEMENTATION = new Implementation(
            DETECTOR_CLASS,
            DETECTOR_SCOPE
    );

    public static final Issue ISSUE = Issue.create(
            ISSUE_ID,
            ISSUE_DESCRIPTION,
            ISSUE_EXPLANATION,
            ISSUE_CATEGORY,
            ISSUE_PRIORITY,
            ISSUE_SEVERITY,
            IMPLEMENTATION
    );

    /**
     *  限定关心的方法的调用类
     */
    public static final String[] mSupportSuperType = new String[]{
            "java.io.InputStream", "java.io.OutputStream", "android.database.Cursor"
    };

    /**
     * 只关心 名为 close 的方法
     */
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("close");
    }

    /**
     * 该方法调用时，会传入代表close方法被调用的节点MethodInvocation,以及所在java文件的上下文JavaContext，
     * 还有AstVisitor。由于我们没有重写createJavaVisitor方法，所以不用管AstVisitor。
     * MethodInvocation封装了close被调用处的代码，而结合JavaContext对象，即可寻找对应的上下文，来帮助我们判断条件。
     */
    @Override
    public void visitMethodCall(@NotNull JavaContext context, @NotNull UCallExpression node, @NotNull PsiMethod method) {
        // 判断类型,看下所监测的资源是否是我们定义的相关资源
        // 通过JavaContext的resolve的方法,传入node节点,由于所有的AST树上的节点都继承自NODE,所以可以通过node去找到class
        if (filleUtils==null){
            filleUtils=new FilleUtils(context.getMainProject());
        }
        //containingClass:java.io.InputStream  获取父类
        PsiClass containingClass = method.getContainingClass();
        hashMap.put("containingClass",containingClass.getQualifiedName());
        // 如下方法也可以获取所在的类
        //PsiElement parent = method.getParent();
        // PsiClass psiClass= (PsiClass) parent;
        // 可以直接获取的所在的类，不需要像上面
        if (containingClass!=null){
//            PsiClass superClass = containingClass.getSuperClass();
            boolean isSubClass = false;
            for (int i = 0; i < mSupportSuperType.length; i++) {
                //是不是这几个类的成员方法
                if (context.getEvaluator().isMemberInSubClassOf(method,mSupportSuperType[i],false)) {
                    hashMap.put("member",mSupportSuperType[i]);
                    isSubClass = true;
                    break;
                }
            }

            if (isSubClass){
                // 查找try和block的信息
                // 在AST中，close 代码节点应该是try的一个子孙节点（try是语法上的block），所以从close代码节点向上追溯，
                // 可以找到对应的try，而Node对象本来就有getParent方法，所以可以递归调用该方法来找到Try节点（这也是一个节点），
                // 或者调用JavaContext的查找限定parent类型的方法:
                UTryExpression tryExpression = UastUtils.getParentOfType(node, UTryExpression.class);
                if (tryExpression!=null){
                    //获取到try 节点的位置
                    int fLineNum = context.getLocation(tryExpression).getStart().getLine();
                    UTryExpression try2= UastUtils.getParentOfType(tryExpression, UTryExpression.class);
                    if (try2!=null){
                        UExpression finallyClause = try2.getFinallyClause();
                        if (finallyClause!=null){
                            //获取的finall的节点的位置
                            int sLineNum = context.getLocation(finallyClause).getStart().getLine();
                            // flineNum 代表的是什么鬼
                            if (fLineNum > sLineNum) {
                                hashMap.put("sLineNum",sLineNum+"");
                                hashMap.put("fLineNum",fLineNum+"");
                                return;
                            } else {
                                hashMap.put("sLineNum"+node,sLineNum+"");
                                hashMap.put("fLineNum"+node,fLineNum+"");
                                String message = ISSUE_DESCRIPTION + " ,请速度修改";
                                context.report(ISSUE, node, context.getLocation(node), message);
                            }
                        }
                    }
                }
            }
        }

//        Try fTryBlock = JavaContext.getParentOfType(node, Try.class);
//        int fLineNum = context.getLocation(fTryBlock).getStart().getLine();
////        System.out.println("XTCCloseDetector   fLineNum=" + fLineNum);
//
//        //如果close在try模块中,接着就要对try进行向上查找,看try是否被包裹在try中,同时,是否处于finally中,try节点
//        //有一个astFinally的方法,可以得到finally的节点,只要判断节点的位置,既可以实现判断close是否在finally中
//        Try sTryBlock = JavaContext.getParentOfType(fTryBlock, Try.class);
//        if (sTryBlock != null) {
//            Block finaBlock = sTryBlock.astFinally();
//            int sLineNum = context.getLocation(finaBlock).getStart().getLine();
//            //        System.out.println("XTCCloseDetector   sLineNum=" + sLineNum);
//
//            // 若我们确定了close是在try 块中，且try块不在finally里，那么就需要触发Issue，这样在html报告中就可以找到对应的信息了
//
//            // 一个莫名的bug,不能再这里写成
//            // if (fLineNum < sLineNum){
//            //     context.report(ISSUE, node, context.getLocation(node), "请在finally中调用close");
//            // }
//
//            // 否则再直接运行Analyze下Inspect选项后,在inspection窗口中没有办法看到Error from custom lint Check 的错误信息
//            if (fLineNum > sLineNum) {
//                return;
//            } else {
////                System.out.println("XTCCloseDetector 出现lint检测项，对应的责任人为： " + relativePersonName);
//                String message = ISSUE_DESCRIPTION + " ,请速度修改";
//                context.report(ISSUE, node, context.getLocation(node), message);
//            }
//        }
    }
    private HashMap<String,String> hashMap=new LinkedHashMap<>(8);
    @Override
    public void afterCheckRootProject(@NotNull Context context) {
        super.afterCheckRootProject(context);
        if (filleUtils!=null){
            filleUtils.intput(hashMap);
        }
    }
}