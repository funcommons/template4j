package fun.commons.template4j.arch;

import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构守护测试 (ArchUnit) —— 约束 template4j-core 的依赖域与包结构。
 *
 * <ul>
 *   <li>规则 1: 禁止依赖 Spring / Jakarta / javax.servlet。core 自 RestRenderer
 *       改用 JDK java.net.http.HttpClient 起必须保持零框架依赖, 下游接入无负担。</li>
 *   <li>规则 2: components 包内只能是具体类。每个内置组件
 *       (if/each/switch/render/const/rest/parallel/try/invoke) 都是可直接注册的
 *       renderer, 出现抽象组件即破坏 {@code engine.withComponent(...)} 的约定。</li>
 *   <li>规则 3: utils 不得反向依赖 core。依赖方向必须永远是 core → utils。</li>
 * </ul>
 */
@AnalyzeClasses(packages = "fun.commons.template4j", importOptions = DoNotIncludeTests.class)
class ArchitectureGuardTest {

    @ArchTest
    static final ArchRule mustNotDependOnSpringOrJakarta =
            noClasses().should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "javax.servlet..");

    @ArchTest
    static final ArchRule componentsMustBeConcreteClasses =
            noClasses().that().resideInAPackage("..components..")
                    .should().haveModifier(JavaModifier.ABSTRACT);

    @ArchTest
    static final ArchRule utilsMustNotDependOnCore =
            noClasses().that().resideInAPackage("..fun.commons.template4j.utils..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..fun.commons.template4j.core..");
}
