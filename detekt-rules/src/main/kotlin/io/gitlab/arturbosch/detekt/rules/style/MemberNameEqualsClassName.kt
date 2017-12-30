package io.gitlab.arturbosch.detekt.rules.style

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.rules.isOverridden
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.util.collectionUtils.concat

/**
 *
 * <noncompliant>
 * class MethodNameEqualsClassName {
 *
 *     fun methodNameEqualsClassName() { }
 * }
 *
 * class PropertyNameEqualsClassName {
 *
 *     val propertyEqualsClassName = 0
 * }
 * </noncompliant>
 *
 * @configuration ignoreOverriddenFunction - if overridden functions should be ignored (default: true)
 *
 * @author schalkms
 * @author Marvin Ramin
 */
class MemberNameEqualsClassName(config: Config = Config.empty) : Rule(config) {

	override val issue = Issue(javaClass.simpleName, Severity.Style,
			"A method should not given the same name as its parent class or object.",
			Debt.FIVE_MINS)

	private val classMessage = "A method is named after the class. This might result in confusion. " +
			"Either rename the method or change it to a constructor."
	private val objectMessage = "A method is named after the class object. " +
					"This might result in confusion. Please rename the method."

	private val ignoreOverriddenFunction = valueOrDefault(IGNORE_OVERRIDDEN_FUNCTION, true)

	override fun visitClass(klass: KtClass) {
		if (!klass.isInterface()) {
			getMisnamedMembers(klass, klass.name)
					.concat(getMisnamedCompanionObjectMembers(klass))
					?.forEach { report(CodeSmell(issue, Entity.from(it), classMessage)) }
		}
		super.visitClass(klass)
	}

	override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {
		if (!declaration.isCompanion()) {
			getMisnamedMembers(declaration, declaration.name)
					.forEach { report(CodeSmell(issue, Entity.from(it), objectMessage)) }
		}
		super.visitObjectDeclaration(declaration)
	}

	private fun getMisnamedMembers(klassOrObject: KtClassOrObject, name: String?): List<KtNamedDeclaration> {
		val body = klassOrObject.getBody() ?: return emptyList()
		val declarations = getFunctions(body).concat(body.properties)
		return declarations?.filter { it.name?.equals(name, ignoreCase = true) == true } ?: emptyList()
	}

	private fun getFunctions(body: KtClassBody): List<KtNamedDeclaration> {
		var functions = body.children.filterIsInstance<KtNamedFunction>()
		if (ignoreOverriddenFunction) {
			functions = functions.filter { !it.isOverridden() }
		}
		return functions
	}

	private fun getMisnamedCompanionObjectMembers(klass: KtClass): List<KtNamedDeclaration> {
		val list = mutableListOf<KtNamedDeclaration>()
		klass.companionObjects.forEach { list.addAll(getMisnamedMembers(it, klass.name)) }
		list
				.filterIsInstance<KtNamedFunction>()
				.filter { isFactoryMethod(it, klass) }
				.forEach { it -> list.remove(it) }
		return list
	}

	private fun isFactoryMethod(function: KtNamedFunction, klass: KtClass): Boolean {
		val typeReference = function.typeReference
		return typeReference == null && function.bodyExpression !is KtBlockExpression
				|| typeReference?.text?.equals(klass.name) == true
	}

	companion object {
		const val IGNORE_OVERRIDDEN_FUNCTION = "ignoreOverriddenFunction"
	}
}
