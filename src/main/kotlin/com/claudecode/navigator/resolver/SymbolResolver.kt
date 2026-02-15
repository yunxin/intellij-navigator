package com.claudecode.navigator.resolver

import com.claudecode.navigator.model.NavigationTarget
import com.claudecode.navigator.util.PathMatcher
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.stubs.PyClassNameIndex
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex

class SymbolResolver(private val project: Project) {
    private val logger = Logger.getInstance(SymbolResolver::class.java)

    /**
     * Resolves a qualified symbol name to navigation targets.
     *
     * Uses rightmost part for index lookup, then filters by qualifiers.
     * If fileHint is provided, results are further filtered by path matching.
     *
     * Examples:
     * - "MyClass" - finds class
     * - "my_function" - finds function
     * - "MyClass.method" - finds method in MyClass
     * - "module.submodule.MyClass" - finds MyClass in that module path
     *
     * @param qualifiedName The qualified symbol name to find
     * @param fileHint Optional file path hint for filtering results
     * @return List of matching navigation targets
     */
    fun resolve(qualifiedName: String, fileHint: String? = null): List<NavigationTarget> {
        return ReadAction.compute<List<NavigationTarget>, Exception> {
            val parts = qualifiedName.split(".")
            val shortName = parts.last()
            val qualifiers = parts.dropLast(1)

            logger.debug("Resolving symbol: '$qualifiedName' (shortName: $shortName, qualifiers: $qualifiers, fileHint: $fileHint)")

            val scope = GlobalSearchScope.projectScope(project)
            val targets = mutableListOf<NavigationTarget>()

            // Search for classes with this short name
            val classes = PyClassNameIndex.find(shortName, project, scope)
            for (pyClass in classes) {
                if (matchesQualifiers(pyClass, qualifiers)) {
                    pyClass.toNavigationTarget()?.let { targets.add(it) }
                }
            }

            // Search for functions with this short name
            val functions = PyFunctionNameIndex.find(shortName, project, scope)
            for (pyFunction in functions) {
                if (matchesQualifiers(pyFunction, qualifiers)) {
                    pyFunction.toNavigationTarget()?.let { targets.add(it) }
                }
            }

            logger.debug("Found ${targets.size} targets for '$qualifiedName'")

            // Apply fileHint as secondary filter
            applyFileHintFilter(targets, fileHint)
        }
    }

    /**
     * Applies file hint filtering to targets.
     * If hint matches some targets, returns only those.
     * If hint matches nothing (soft matching), returns all targets unchanged.
     */
    private fun applyFileHintFilter(
        targets: List<NavigationTarget>,
        fileHint: String?
    ): List<NavigationTarget> {
        if (fileHint.isNullOrBlank() || targets.isEmpty()) {
            return targets
        }

        val filtered = targets.filter {
            PathMatcher.pathMatches(it.file.path, fileHint)
        }

        logger.debug("FileHint filter: ${targets.size} -> ${filtered.size} (hint: $fileHint)")

        // Soft matching: if hint matches nothing, ignore it
        return if (filtered.isNotEmpty()) filtered else targets
    }

    /**
     * Checks if an element's qualifier chain matches the expected qualifiers.
     * Qualifiers are matched from the end (suffix matching).
     */
    private fun matchesQualifiers(element: PsiElement, qualifiers: List<String>): Boolean {
        if (qualifiers.isEmpty()) {
            return true
        }

        val elementQualifiers = getQualifierChain(element)
        if (elementQualifiers.isEmpty()) {
            // No class-level context to verify against; accept the match
            return true
        }

        if (elementQualifiers.size > qualifiers.size) {
            // Element has more class qualifiers than provided — mismatch
            return false
        }

        // Verify that element qualifiers match the rightmost (closest-to-symbol) parts
        // of the provided qualifiers. Leftmost parts (module/file) are unverifiable here.
        val qualifierSuffix = qualifiers.takeLast(elementQualifiers.size)
        return qualifierSuffix == elementQualifiers
    }

    /**
     * Gets the qualifier chain for an element (containing class, module path).
     */
    private fun getQualifierChain(element: PsiElement): List<String> {
        val chain = mutableListOf<String>()

        when (element) {
            is PyFunction -> {
                // Add containing class if exists
                element.containingClass?.name?.let { chain.add(it) }
            }
            is PyClass -> {
                // Add containing class if nested
                (element.parent as? PyClass)?.name?.let { chain.add(it) }
            }
        }

        // Add module path components
        val file = element.containingFile
        val moduleName = file?.virtualFile?.nameWithoutExtension
        if (moduleName != null && moduleName != "__init__") {
            // Could add package path here if needed
        }

        return chain
    }

    private fun PyClass.toNavigationTarget(): NavigationTarget? {
        val file = containingFile?.virtualFile ?: return null
        val line = getLineNumber(this)
        return NavigationTarget(
            file = file,
            line = line,
            description = "class $name"
        )
    }

    private fun PyFunction.toNavigationTarget(): NavigationTarget? {
        val file = containingFile?.virtualFile ?: return null
        val line = getLineNumber(this)
        val desc = if (containingClass != null) {
            "${containingClass?.name}.$name()"
        } else {
            "$name()"
        }
        return NavigationTarget(
            file = file,
            line = line,
            description = desc
        )
    }

    private fun getLineNumber(element: PsiElement): Int {
        val document = element.containingFile?.viewProvider?.document ?: return 0
        return document.getLineNumber(element.textOffset)
    }
}
