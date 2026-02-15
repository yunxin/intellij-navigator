package com.claudecode.navigator.resolver

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SymbolResolverTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    private lateinit var resolver: SymbolResolver

    override fun setUp() {
        super.setUp()
        // Copy test project files into the test fixture
        myFixture.copyDirectoryToProject("project", "")
        resolver = SymbolResolver(project)
    }

    fun `test resolve class by name`() {
        val targets = resolver.resolve("UserModel")

        // Should find UserModel in both user.py and product.py
        assertEquals(2, targets.size)
        assertTrue(targets.any { it.file.path.contains("user.py") })
        assertTrue(targets.any { it.file.path.contains("product.py") })
    }

    fun `test resolve class with fileHint filters to matching file`() {
        val targets = resolver.resolve("UserModel", "models/user.py")

        // Should only find UserModel in user.py
        assertEquals(1, targets.size)
        assertTrue(targets[0].file.path.contains("user.py"))
    }

    fun `test resolve class with fileHint using filename only`() {
        val targets = resolver.resolve("UserModel", "user.py")

        // Should only find UserModel in user.py
        assertEquals(1, targets.size)
        assertTrue(targets[0].file.path.contains("user.py"))
    }

    fun `test resolve class with non-matching fileHint returns all results`() {
        // Soft matching: if hint matches nothing, return all results
        val targets = resolver.resolve("UserModel", "nonexistent.py")

        // Should return both since hint matches nothing
        assertEquals(2, targets.size)
    }

    fun `test resolve function by name`() {
        val targets = resolver.resolve("helper")

        assertEquals(1, targets.size)
        assertTrue(targets[0].file.path.contains("utils.py"))
    }

    fun `test resolve function with fileHint`() {
        val targets = resolver.resolve("process", "utils.py")

        // Should only find the process function in utils.py, not Handler.process
        assertEquals(1, targets.size)
        assertTrue(targets[0].file.path.contains("utils.py"))
    }

    fun `test resolve method with class qualifier`() {
        val targets = resolver.resolve("UserModel.save")

        // Should find save() method in UserModel classes
        assertTrue(targets.isNotEmpty())
        assertTrue(targets.all { it.description.contains("save") })
    }

    fun `test resolve method with class qualifier and fileHint`() {
        val targets = resolver.resolve("UserModel.save", "user.py")

        // Should only find save() in user.py's UserModel
        assertEquals(1, targets.size)
        assertTrue(targets[0].file.path.contains("user.py"))
    }

    fun `test resolve unique class without fileHint`() {
        val targets = resolver.resolve("ProductModel")

        assertEquals(1, targets.size)
        assertTrue(targets[0].file.path.contains("product.py"))
    }

    fun `test resolve with empty fileHint returns all results`() {
        val targets = resolver.resolve("UserModel", "")

        // Empty hint should be ignored
        assertEquals(2, targets.size)
    }

    fun `test resolve nonexistent symbol returns empty`() {
        val targets = resolver.resolve("NonExistentClass")

        assertTrue(targets.isEmpty())
    }

    fun `test resolve with partial path fileHint`() {
        val targets = resolver.resolve("Handler", "handlers/api.py")

        assertEquals(1, targets.size)
        assertTrue(targets[0].file.path.contains("handlers"))
    }

    // --- Fully qualified symbol tests ---

    fun `test resolve class with module qualifier`() {
        // "user.UserModel" should still find UserModel (module part is unverifiable but accepted)
        val targets = resolver.resolve("user.UserModel")

        assertTrue(targets.isNotEmpty())
        assertTrue(targets.any { it.description.contains("UserModel") })
    }

    fun `test resolve class with multi-part module qualifier`() {
        // "models.user.UserModel" should find UserModel
        val targets = resolver.resolve("models.user.UserModel")

        assertTrue(targets.isNotEmpty())
        assertTrue(targets.any { it.description.contains("UserModel") })
    }

    fun `test resolve method with module and class qualifier`() {
        // "models.user.UserModel.save" should find save() in UserModel
        val targets = resolver.resolve("models.user.UserModel.save")

        assertTrue(targets.isNotEmpty())
        assertTrue(targets.all { it.description.contains("save") })
    }

    fun `test resolve method with module and class qualifier and fileHint`() {
        // "models.user.UserModel.save" with fileHint narrows to one result
        val targets = resolver.resolve("models.user.UserModel.save", "user.py")

        assertEquals(1, targets.size)
        assertTrue(targets[0].file.path.contains("user.py"))
    }

    fun `test resolve class with wrong class qualifier rejects`() {
        // "WrongClass.save" should NOT match UserModel.save
        val targets = resolver.resolve("WrongClass.save")

        assertTrue(targets.none { it.description.contains("UserModel") })
    }

    fun `test resolve function with module qualifier`() {
        // "utils.helper" should find the helper function
        val targets = resolver.resolve("utils.helper")

        assertTrue(targets.isNotEmpty())
        assertTrue(targets[0].file.path.contains("utils.py"))
    }

    fun `test resolve unique class with module qualifier`() {
        // "product.ProductModel" should find ProductModel
        val targets = resolver.resolve("product.ProductModel")

        assertEquals(1, targets.size)
        assertTrue(targets[0].file.path.contains("product.py"))
    }
}
