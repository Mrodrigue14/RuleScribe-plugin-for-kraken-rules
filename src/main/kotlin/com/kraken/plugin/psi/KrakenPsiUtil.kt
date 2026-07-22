package com.kraken.plugin.psi

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.lang.KrakenFileType
import com.kraken.plugin.parser.KrakenTypes
import com.kraken.plugin.psi.stubs.KrakenRuleNameIndex

object KrakenPsiUtil {

    /** Tokens acceptés comme identifiants (miroir de la règle `id` du BNF). */
    @JvmField
    val ID_TOKENS: TokenSet = TokenSet.create(
        KrakenTypes.IDENTIFIER,
        KrakenTypes.ON_KW, KrakenTypes.FROM_KW, KrakenTypes.TO_KW,
        KrakenTypes.MIN_KW, KrakenTypes.MAX_KW, KrakenTypes.STEP_KW,
        KrakenTypes.SIZE_KW, KrakenTypes.LENGTH_KW, KrakenTypes.NUMBER_KW,
        KrakenTypes.EMPTY_KW, KrakenTypes.MANDATORY_KW, KrakenTypes.DISABLED_KW,
        KrakenTypes.HIDDEN_KW, KrakenTypes.DESCRIPTION_KW, KrakenTypes.PRIORITY_KW,
        KrakenTypes.EXTERNAL_KW, KrakenTypes.CHILD_KW, KrakenTypes.ROOT_KW,
        KrakenTypes.SYSTEM_KW, KrakenTypes.CONTEXT_KW, KrakenTypes.DIMENSION_KW,
        KrakenTypes.FUNCTION_KW, KrakenTypes.MATCHES_KW, KrakenTypes.INCLUDE_KW,
        KrakenTypes.NAMESPACE_KW, KrakenTypes.ERROR_KW, KrakenTypes.WARN_KW,
        KrakenTypes.INFO_KW
    )

    fun unquote(text: String): String {
        if (text.length >= 2) {
            val first = text.first()
            if ((first == '"' || first == '\'') && text.last() == first) {
                return text.substring(1, text.length - 1)
            }
        }
        return text
    }

    fun krakenFiles(project: Project): List<KrakenFile> =
        FileTypeIndex.getFiles(KrakenFileType, GlobalSearchScope.projectScope(project))
            .mapNotNull { PsiManager.getInstance(project).findFile(it) as? KrakenFile }

    // ------------------------------------------------------------------
    // Espaces de noms : Namespace X + Include Y
    // ------------------------------------------------------------------

    fun namespaceOf(file: KrakenFile): String? =
        file.node.findChildByType(KrakenTypes.NAMESPACE_DECL)
            ?.findChildByType(KrakenTypes.QUALIFIED_NAME)?.text?.trim()

    fun includesOf(file: KrakenFile): List<String> =
        file.node.getChildren(TokenSet.create(KrakenTypes.INCLUDE_DECL))
            .mapNotNull { it.findChildByType(KrakenTypes.QUALIFIED_NAME)?.text?.trim() }

    /**
     * Fichiers visibles depuis [from] : même namespace, namespaces inclus
     * (transitivement) et fichiers sans namespace. Un fichier sans namespace
     * voit tout le projet.
     */
    fun visibleFiles(from: PsiFile?): List<KrakenFile> {
        val fromKraken = from as? KrakenFile ?: return emptyList()
        val all = krakenFiles(fromKraken.project)
        val result: List<KrakenFile>
        val ns = namespaceOf(fromKraken)
        if (ns == null) {
            result = all
        } else {
            val filesByNs = HashMap<String?, MutableList<KrakenFile>>()
            val nsIncludes = HashMap<String, MutableSet<String>>()
            for (f in all) {
                val n = namespaceOf(f)
                filesByNs.getOrPut(n) { mutableListOf() }.add(f)
                if (n != null) {
                    nsIncludes.getOrPut(n) { mutableSetOf() }.addAll(includesOf(f))
                }
            }
            nsIncludes.getOrPut(ns) { mutableSetOf() }.addAll(includesOf(fromKraken))
            val visited = linkedSetOf(ns)
            val queue = ArrayDeque(listOf(ns))
            while (queue.isNotEmpty()) {
                for (inc in nsIncludes[queue.removeFirst()].orEmpty()) {
                    if (visited.add(inc)) queue.add(inc)
                }
            }
            val collected = mutableListOf<KrakenFile>()
            for (n in visited) collected.addAll(filesByNs[n].orEmpty())
            collected.addAll(filesByNs[null].orEmpty())
            result = collected
        }
        // Le fichier courant peut ne pas être indexé (éditeur léger, tests)
        return if (result.any { it.isEquivalentTo(fromKraken) }) result else result + fromKraken
    }

    // ------------------------------------------------------------------
    // Imports de règles : Import Rule "X" [, "Y"] From Ns
    //
    // Sémantique du moteur (ResourceKrakenProjectBuilder.importRules) : la
    // règle importée est copiée dans le namespace importateur comme si elle
    // y était déclarée — indépendamment de tout Include. Les imports déclarés
    // par n'importe quel fichier d'un namespace valent pour tout le namespace.
    // ------------------------------------------------------------------

    /** Un import déclaré : nom de règle + namespace source + éléments PSI à surligner. */
    data class RuleImport(
        val ruleName: String,
        val sourceNamespace: String,
        val nameElement: PsiElement,
        val namespaceElement: PsiElement,
    )

    /** Imports déclarés par une déclaration `Import Rule … From …` précise. */
    fun ruleImportsIn(decl: PsiElement): List<RuleImport> {
        val node = decl.node ?: return emptyList()
        if (node.elementType != KrakenTypes.RULE_IMPORT_DECL) return emptyList()
        val nsNode = node.findChildByType(KrakenTypes.QUALIFIED_NAME) ?: return emptyList()
        val ns = nsNode.text.trim()
        val namesNode = node.findChildByType(KrakenTypes.IMPORT_RULE_NAMES) ?: return emptyList()
        val result = mutableListOf<RuleImport>()
        var child = namesNode.firstChildNode
        while (child != null) {
            if (child.elementType == KrakenTypes.STRING) {
                result.add(RuleImport(unquote(child.text), ns, child.psi, nsNode.psi))
            }
            child = child.treeNext
        }
        return result
    }

    /** Imports déclarés dans un fichier. */
    fun ruleImportsOf(file: KrakenFile): List<RuleImport> =
        file.node.getChildren(TokenSet.create(KrakenTypes.RULE_IMPORT_DECL))
            .flatMap { ruleImportsIn(it.psi) }

    /**
     * Imports valant pour le namespace de [from] : ceux déclarés par tous les
     * fichiers de ce namespace (le moteur fusionne les imports par namespace).
     */
    fun ruleImportsForNamespaceOf(from: PsiFile?): List<RuleImport> {
        val fromKraken = from as? KrakenFile ?: return emptyList()
        val ns = namespaceOf(fromKraken)
        val files = krakenFiles(fromKraken.project).filter { namespaceOf(it) == ns }
        val importing =
            if (files.any { it.isEquivalentTo(fromKraken) }) files else files + fromKraken
        return importing.flatMap { ruleImportsOf(it) }
    }

    /** Vrai si un fichier du projet déclare `Namespace [ns]`. */
    fun namespaceExists(project: Project, ns: String): Boolean =
        krakenFiles(project).any { namespaceOf(it) == ns }

    /** Fichiers appartenant à un namespace donné (null = fichiers sans namespace). */
    fun filesOfNamespace(project: Project, ns: String?): List<KrakenFile> =
        krakenFiles(project).filter { namespaceOf(it) == ns }

    /**
     * Résout une règle déclarée dans un namespace précis, sans passer par la
     * visibilité Include (c'est le propre d'un Import Rule).
     */
    fun findRuleInNamespace(project: Project, ns: String?, name: String): KrakenRuleDecl? {
        val files = filesOfNamespace(project, ns)
        val virtualFiles = files.mapNotNull { it.virtualFile }
        if (virtualFiles.isNotEmpty()) {
            val scope = GlobalSearchScope.filesScope(project, virtualFiles)
            StubIndex.getElements(
                KrakenRuleNameIndex.KEY, name, project, scope, KrakenRuleDecl::class.java
            ).firstOrNull()?.let { return it }
        }
        return files
            .flatMap { PsiTreeUtil.findChildrenOfType(it, KrakenRuleDecl::class.java) }
            .firstOrNull { it.name == name }
    }

    /** Déclaration ciblée par un `Import Rule` du namespace de [from], le cas échéant. */
    fun findImportedRule(from: PsiElement, name: String): KrakenRuleDecl? {
        val import = ruleImportsForNamespaceOf(from.containingFile)
            .firstOrNull { it.ruleName == name } ?: return null
        return findRuleInNamespace(from.project, import.sourceNamespace, name)
    }

    /** Vrai si le namespace de [ref] importe explicitement [name] depuis [declNs]. */
    private fun refImportsRule(ref: PsiElement, name: String, declNs: String?): Boolean {
        if (declNs == null) return false
        return ruleImportsForNamespaceOf(ref.containingFile)
            .any { it.ruleName == name && it.sourceNamespace == declNs }
    }

    // ------------------------------------------------------------------
    // Règles
    // ------------------------------------------------------------------

    fun findRules(project: Project): List<KrakenRuleDecl> =
        krakenFiles(project).flatMap { PsiTreeUtil.findChildrenOfType(it, KrakenRuleDecl::class.java) }

    fun findRule(project: Project, name: String): KrakenRuleDecl? =
        findRules(project).firstOrNull { it.name == name }

    fun findRulesVisible(from: PsiElement): List<KrakenRuleDecl> {
        val direct = visibleFiles(from.containingFile)
            .flatMap { PsiTreeUtil.findChildrenOfType(it, KrakenRuleDecl::class.java) }
        // Règles importées via Import Rule … From … (hors visibilité Include)
        val imported = ruleImportsForNamespaceOf(from.containingFile)
            .mapNotNull { findRuleInNamespace(from.project, it.sourceNamespace, it.ruleName) }
        return if (imported.isEmpty()) direct else (direct + imported).distinct()
    }

    fun findRuleVisible(from: PsiElement, name: String): KrakenRuleDecl? {
        // 1. Chemin rapide : stub index (O(1), sans charger les AST)
        val project = from.project
        val virtualFiles = visibleFiles(from.containingFile).mapNotNull { it.virtualFile }
        if (virtualFiles.isNotEmpty()) {
            val scope = GlobalSearchScope.filesScope(project, virtualFiles)
            val indexed = StubIndex.getElements(
                KrakenRuleNameIndex.KEY, name, project, scope, KrakenRuleDecl::class.java
            )
            indexed.firstOrNull()?.let { return it }
        }
        // 2. Règle importée explicitement (indépendant d'Include)
        findImportedRule(from, name)?.let { return it }
        // 3. Repli : fichiers non indexés (éditeur léger, fragments, tests)
        return findRulesVisible(from).firstOrNull { it.name == name }
    }

    /** Tous les noms de règles connus de l'index (projet entier). */
    fun allIndexedRuleNames(project: Project): Collection<String> =
        StubIndex.getInstance().getAllKeys(KrakenRuleNameIndex.KEY, project)

    // ------------------------------------------------------------------
    // Entry points
    // ------------------------------------------------------------------

    fun findEntryPointsVisible(from: PsiElement): List<KrakenEntryPointDecl> =
        visibleFiles(from.containingFile)
            .flatMap { PsiTreeUtil.findChildrenOfType(it, KrakenEntryPointDecl::class.java) }

    fun findEntryPointVisible(from: PsiElement, name: String): KrakenEntryPointDecl? =
        findEntryPointsVisible(from).firstOrNull { it.name == name }

    /** Vrai si le fichier de [refElement] peut voir [declarationFile] (namespaces). */
    private fun refSees(refElement: PsiElement, declarationFile: PsiFile?): Boolean {
        if (declarationFile == null) return false
        return visibleFiles(refElement.containingFile).any { it.isEquivalentTo(declarationFile) }
    }

    /**
     * Références de règles qui peuvent effectivement voir [declaration] :
     * une référence située dans un namespace qui n'inclut pas celui de la
     * déclaration ne compte pas (cohérent avec la résolution du moteur) —
     * sauf si son namespace importe explicitement la règle (Import Rule).
     */
    fun findRuleRefsVisibleTo(declaration: KrakenRuleDecl): List<KrakenRuleRef> {
        val name = declaration.name ?: return emptyList()
        val declarationFile = declaration.containingFile
        val declNs = (declarationFile as? KrakenFile)?.let { namespaceOf(it) }
        return findRuleRefs(declaration.project, name).filter {
            refSees(it, declarationFile) || refImportsRule(it, name, declNs)
        }
    }

    /** Idem pour les références d'entry points imbriquées. */
    fun findEpRefsVisibleTo(declaration: KrakenEntryPointDecl): List<KrakenEpRef> {
        val name = declaration.name ?: return emptyList()
        val declarationFile = declaration.containingFile
        return findEpRefs(declaration.project, name).filter { refSees(it, declarationFile) }
    }

    /** Références `EntryPoint "nom"` imbriquées portant ce nom, projet entier. */
    fun findEpRefs(project: Project, name: String): List<KrakenEpRef> =
        krakenFiles(project)
            .flatMap { PsiTreeUtil.findChildrenOfType(it, KrakenEpRef::class.java) }
            .filter { it.entryPointName == name }

    /** Toutes les références (items d'EntryPoint) portant ce nom, projet entier. */
    fun findRuleRefs(project: Project, name: String): List<KrakenRuleRef> =
        krakenFiles(project)
            .flatMap { PsiTreeUtil.findChildrenOfType(it, KrakenRuleRef::class.java) }
            .filter { it.ruleName == name }

    // ------------------------------------------------------------------
    // Dimensions
    // ------------------------------------------------------------------

    fun findDimensionNames(project: Project): List<String> =
        krakenFiles(project)
            .flatMap { PsiTreeUtil.findChildrenOfType(it, KrakenDimensionDecl::class.java) }
            .mapNotNull { it.dimensionName }
            .distinct()

    fun findDimensionNamesVisible(from: PsiFile?): List<String> =
        visibleFiles(from)
            .flatMap { PsiTreeUtil.findChildrenOfType(it, KrakenDimensionDecl::class.java) }
            .mapNotNull { it.dimensionName }
            .distinct()

    // ------------------------------------------------------------------
    // Contextes et champs
    // ------------------------------------------------------------------

    fun findContextNames(project: Project): List<String> =
        krakenFiles(project).flatMap { contextDecls(it).mapNotNull { decl -> contextName(decl) } }.distinct()

    fun findContextNamesVisible(from: PsiFile?): List<String> =
        visibleFiles(from).flatMap { contextDecls(it).mapNotNull { decl -> contextName(decl) } }.distinct()

    fun findContextDecl(from: PsiFile?, name: String): PsiElement? =
        visibleFiles(from).asSequence()
            .flatMap { contextDecls(it).asSequence() }
            .firstOrNull { contextName(it) == name }

    /**
     * Noms des champs et enfants d'un contexte, héritage (`Is Parent`) compris.
     */
    fun contextFieldNames(from: PsiFile?, contextName: String, depth: Int = 0): List<String> {
        if (depth > 4) return emptyList()
        val decl = findContextDecl(from, contextName) ?: return emptyList()
        val names = LinkedHashSet<String>()
        var child = decl.node.firstChildNode
        while (child != null) {
            when (child.elementType) {
                KrakenTypes.FIELD_DECL -> fieldName(child)?.let { names.add(it) }
                KrakenTypes.CHILD_DECL -> childContextName(child)?.let { names.add(it) }
            }
            child = child.treeNext
        }
        val inherited = decl.node.findChildByType(KrakenTypes.INHERITED_CONTEXTS)
        if (inherited != null) {
            for (parent in idLeafTexts(inherited)) {
                names.addAll(contextFieldNames(from, parent, depth + 1))
            }
        }
        return names.toList()
    }

    fun contextDecls(file: KrakenFile): List<PsiElement> =
        PsiTreeUtil.collectElements(file) { it.node?.elementType == KrakenTypes.CONTEXT_DECL }.toList()

    fun contextName(contextDecl: PsiElement): String? {
        val keyword = contextDecl.node.findChildByType(KrakenTypes.CONTEXT_KW) ?: return null
        var node: ASTNode? = keyword.treeNext
        while (node != null && node.psi is PsiWhiteSpace) {
            node = node.treeNext
        }
        return if (node != null && node.elementType in ID_TOKENS) node.text else null
    }

    /** Nom d'un champ : dernier identifiant avant `:` ([External?] Type [*] nom). */
    private fun fieldName(fieldDecl: ASTNode): String? {
        var last: String? = null
        var child = fieldDecl.firstChildNode
        while (child != null) {
            if (child.elementType == KrakenTypes.COLON) break
            if (child.elementType in ID_TOKENS) last = child.text
            child = child.treeNext
        }
        return last
    }

    /** Nom du contexte enfant : premier identifiant après `Child` [*]. */
    private fun childContextName(childDecl: ASTNode): String? {
        var seenChildKw = false
        var child = childDecl.firstChildNode
        while (child != null) {
            if (child.elementType == KrakenTypes.CHILD_KW) seenChildKw = true
            else if (seenChildKw && child.elementType in ID_TOKENS) return child.text
            child = child.treeNext
        }
        return null
    }

    private fun idLeafTexts(node: ASTNode): List<String> {
        val texts = mutableListOf<String>()
        var child = node.firstChildNode
        while (child != null) {
            if (child.elementType in ID_TOKENS) texts.add(child.text)
            child = child.treeNext
        }
        return texts
    }
}
