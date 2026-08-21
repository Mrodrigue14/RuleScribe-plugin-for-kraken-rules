package com.kraken.plugin.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.kraken.plugin.functions.KrakenFunctionCatalog
import com.kraken.plugin.psi.KrakenFunctionDecl
import com.kraken.plugin.types.KrakenTypeToken

/**
 * Validation d'une déclaration `Function`, miroir de `FunctionValidator` et de
 * `FunctionSignatureValidator` côté moteur.
 *
 * **Portée délibérément syntaxique.** Le moteur valide un `KrakenProject`
 * résolu : il connaît tous les types du modèle, ce qui lui permet aussi de
 * signaler un type inconnu (`kvf006`, `kvf009`) ou un corps dont le type ne
 * correspond pas au retour (`kvf011`). RuleScribe ne reprend pas ces
 * vérifications-là : une inspection « ce type n'existe pas » se déclenche sur
 * une *absence*, et une absence n'est concluante que si l'inventaire est
 * complet. C'est très exactement ce qui a coulé la découverte de fonctions des
 * v0.10.1–v0.10.3. Les vérifications ci-dessous se déclenchent au contraire sur
 * une *présence* — un `|` et un `<T>` dans le même type, deux bornes du même
 * nom, un paramètre redéclaré — ce qui reste juste même avec une connaissance
 * partielle du projet.
 *
 * **Le corps choisit le code.** Une `Function` sans corps est une
 * `FunctionSignature` pour le moteur, validée par une autre classe avec
 * d'autres codes ; voir la KDoc de [KrakenDiagnostic].
 */
private abstract class KrakenFunctionDeclVisitor(
    private val holder: ProblemsHolder,
) : PsiElementVisitor() {

    final override fun visitElement(element: PsiElement) {
        if (element is KrakenFunctionDecl) check(element, holder)
    }

    abstract fun check(function: KrakenFunctionDecl, holder: ProblemsHolder)
}

/** Un doublon se signale à partir de la deuxième occurrence : c'est celle à supprimer. */
private fun <T> Iterable<T>.afterFirstOccurrenceOf(key: (T) -> String?): List<T> {
    val seen = mutableSetOf<String>()
    return filter { item -> key(item)?.let { !seen.add(it) } ?: false }
}

/**
 * Bornes génériques invalides : deux bornes pour le même générique
 * (`kvf004`/`kvf017`), ou une borne elle-même générique (`kvf005`/`kvf018`).
 */
class KrakenFunctionGenericBoundInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KrakenFunctionDeclVisitor(holder) {
            override fun check(function: KrakenFunctionDecl, holder: ProblemsHolder) {
                val bounds = function.genericBounds
                if (bounds.isEmpty()) return
                val signature = !function.hasBody()

                for (duplicate in bounds.afterFirstOccurrenceOf { it.generic }) {
                    val diagnostic =
                        if (signature) KrakenDiagnostic.SIGNATURE_GENERIC_BOUND_DUPLICATE
                        else KrakenDiagnostic.FUNCTION_GENERIC_BOUND_DUPLICATE
                    holder.registerProblem(
                        duplicate.nameElement,
                        diagnostic.format(duplicate.generic),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                    )
                }

                for (bound in bounds) {
                    val element = bound.boundElement ?: continue
                    val text = bound.bound ?: continue
                    // Le moteur résout la borne sans environnement de bornes
                    // (`resolveTypeOf` à un seul argument) : une borne ne peut
                    // donc pas se référer à un autre générique.
                    if (KrakenTypeToken.parse(text)?.isGeneric != true) continue
                    val diagnostic =
                        if (signature) KrakenDiagnostic.SIGNATURE_GENERIC_BOUND_IS_ITSELF_GENERIC
                        else KrakenDiagnostic.FUNCTION_GENERIC_BOUND_IS_ITSELF_GENERIC
                    holder.registerProblem(
                        element,
                        diagnostic.format(text, bound.generic),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                    )
                }
            }
        }
}

/**
 * Type mêlant union et générique — `<T> | String` — que le moteur refuse en
 * position de retour (`kvf007`/`kvf020`) comme de paramètre (`kvf010`/`kvf021`).
 */
class KrakenFunctionTypeUnionGenericMixInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KrakenFunctionDeclVisitor(holder) {
            override fun check(function: KrakenFunctionDecl, holder: ProblemsHolder) {
                val signature = !function.hasBody()

                report(
                    holder, function.returnTypeElement, function.returnType,
                    if (signature) KrakenDiagnostic.SIGNATURE_RETURN_TYPE_UNION_GENERIC_MIX
                    else KrakenDiagnostic.FUNCTION_RETURN_TYPE_UNION_GENERIC_MIX
                )
                for (parameter in function.parameterList) {
                    report(
                        holder, parameter.typeElement, parameter.type,
                        if (signature) KrakenDiagnostic.SIGNATURE_PARAMETER_TYPE_UNION_GENERIC_MIX
                        else KrakenDiagnostic.FUNCTION_PARAMETER_TYPE_UNION_GENERIC_MIX
                    )
                }
            }

            private fun report(
                holder: ProblemsHolder,
                element: PsiElement?,
                text: String?,
                diagnostic: KrakenDiagnostic,
            ) {
                if (element == null || text == null) return
                val type = KrakenTypeToken.parse(text) ?: return
                if (type.isUnion && type.isGeneric) {
                    holder.registerProblem(
                        element,
                        diagnostic.format(text),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                    )
                }
            }
        }
}

/**
 * Deux paramètres du même nom (`kvf008`).
 *
 * Réservé aux fonctions *avec* corps : une signature nue ne nomme pas ses
 * paramètres côté moteur (`functionSignatureParameter : type`), donc la
 * question ne s'y pose pas — la grammaire de RuleScribe est seulement plus
 * tolérante sur ce point.
 */
class KrakenFunctionParameterDuplicateInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KrakenFunctionDeclVisitor(holder) {
            override fun check(function: KrakenFunctionDecl, holder: ProblemsHolder) {
                if (!function.hasBody()) return
                for (duplicate in function.parameterList.afterFirstOccurrenceOf { it.name }) {
                    val element = duplicate.nameElement ?: continue
                    holder.registerProblem(
                        element,
                        KrakenDiagnostic.FUNCTION_PARAMETER_DUPLICATE.format(duplicate.name),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                    )
                }
            }
        }
}

/**
 * Une fonction implémentée en KEL qui porte le nom d'une native (`kvf003`).
 *
 * Le test est un *appariement* contre le catalogue embarqué, pas une absence :
 * il ne peut donc rien affirmer d'une fonction que RuleScribe ne connaîtrait
 * pas. Le moteur compare sur le seul nom, sans l'arité.
 *
 * Une déclaration sans corps est épargnée : c'est précisément ainsi qu'on
 * déclare au DSL qu'une fonction Java existe, et `FunctionSignatureValidator`
 * ne fait pas cette vérification.
 */
class KrakenFunctionNativeDuplicateInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KrakenFunctionDeclVisitor(holder) {
            override fun check(function: KrakenFunctionDecl, holder: ProblemsHolder) {
                if (!function.hasBody()) return
                val name = function.name ?: return
                val anchor = function.nameIdentifier ?: return
                if (KrakenFunctionCatalog.byName(name).isEmpty()) return
                holder.registerProblem(
                    anchor,
                    KrakenDiagnostic.FUNCTION_NATIVE_DUPLICATE.format(name),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                )
            }
        }
}
