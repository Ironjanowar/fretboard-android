package dev.ironjanowar.fretboard.session

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipFile

/**
 * A binding port must call the engine, never itself.
 *
 * Every production port is an object whose method runs on a dispatcher and calls
 * the *imported* engine function of the same name — and that is exactly the shape
 * that hides a resolution mistake:
 *
 * ```kotlin
 * override suspend fun keySuggestions(state: PageStateDto) =
 *     withContext(Dispatchers.Default) { keySuggestions(state) }  // ← itself, not the binding
 * ```
 *
 * Kotlin resolves that call to the **member**, which is nearer than the import, so
 * the binding is never reached: the port recurses. On a device this is the whole
 * of the "keys panel does not work" report — one run overflowed the stack Android
 * reports as `stack size 4109KB`, and another left the panel on *Ranking keys…*
 * forever, because a recursion that re-dispatches never finishes and never throws.
 *
 * No test that scripts the port can catch it: a scripted port replaces precisely the
 * object that is wrong, and A16's suite was green throughout. A test that *calls*
 * the port cannot catch it either — the recursion does not terminate, so the run
 * hangs instead of failing (measured: it wedged the test runner). What is checked
 * here is therefore the compiled result: the constant pool of each port class and
 * of its nested dispatcher lambdas, wherever the class path serves them from.
 *
 * Two rules, both read from the bytecode:
 *
 * * a nested class of a port must **not** reference its own outer port (that
 *   reference *is* the recursion), and
 * * it must reference the generated binding (`dev/ironjanowar/fretboard/core/…`),
 *   so a class that reaches nothing cannot pass by accident.
 */
class EnginePortWiringTest {

    /** A method reference one class file makes: who owns it, and its name. */
    private data class MethodReference(val owner: String, val name: String)

    /** One analysed port class: its own name and the references it makes. */
    private data class PortClass(val name: String, val references: List<MethodReference>) {
        /** This class's own path, as a constant pool spells it. */
        val ownPath: String = name.replace('.', '/')

        /** The path of the outermost class this one belongs to (a port, or itself). */
        val outerPath: String = ownPath.substringBefore('$')

        /** True for a class nested inside a port: a companion, a lambda, a branch. */
        val isNested: Boolean = name.contains('$')

        /** The port's own methods this class calls: for a port's lambda, the recursion. */
        val ownOuterReferences: List<MethodReference> = references.filter { reference ->
            reference.owner == outerPath
        }

        /**
         * True when this class calls something other than itself, its own port and
         * the language/runtime: the engine, or a helper of this application.
         */
        val delegatesAway: Boolean = references.any { reference ->
            !reference.owner.startsWith("kotlin") &&
                !reference.owner.startsWith("java") &&
                reference.owner != ownPath &&
                reference.owner != outerPath
        }
    }

    private companion object {
        const val BINDING_PACKAGE = "dev/ironjanowar/fretboard/core/"
        const val ANCHOR = "dev/ironjanowar/fretboard/session/BindingKeySuggestionEngine.class"

        /** The packages that hold ports: the session's and A17's storage. */
        val PORT_PACKAGES = listOf(
            "dev.ironjanowar.fretboard.session",
            "dev.ironjanowar.fretboard.storage",
        )

        /** The class loader the app's compiled classes were served from, for messages. */
        fun classPathSource(): String = EnginePortWiringTest::class.java.classLoader
            ?.getResource(ANCHOR)?.toString() ?: "none"

        /**
         * The compiled `Binding*` classes of the port packages.
         *
         * The application's classes are served either from a directory (a plain
         * class path) or from a jar (Gradle's unit-test runtime class path), so both
         * are enumerated; the bytes are then read through the class loader, which
         * works for either.
         */
        fun portClassNames(): List<String> {
            val anchor = requireNotNull(
                EnginePortWiringTest::class.java.classLoader?.getResource(ANCHOR),
            ) { "$ANCHOR is not on the test class path" }
            return if (anchor.toString().startsWith("jar:")) {
                namesInJar(anchor)
            } else {
                namesInDirectory(anchor)
            }
        }

        fun namesInJar(anchor: java.net.URL): List<String> {
            val archive = URI(anchor.toString().removePrefix("jar:").substringBefore('!'))
            return ZipFile(Paths.get(archive).toFile()).use { zip ->
                zip.entries().asSequence()
                    .map { entry -> entry.name }
                    .filter { name -> name.endsWith(".class") }
                    .filter { name -> PORT_PACKAGES.any { pack -> owns(pack, name) } }
                    .map { name -> name.removeSuffix(".class").replace('/', '.') }
                    .toList()
            }
        }

        fun namesInDirectory(anchor: java.net.URL): List<String> {
            val file = Paths.get(anchor.toURI())
            val root = (1..ANCHOR.count { character -> character == '/' })
                .fold(file) { path, _ -> requireNotNull(path.parent) }
            return PORT_PACKAGES.flatMap { pack ->
                val directory = root.resolve(pack.replace('.', '/'))
                if (!Files.isDirectory(directory)) {
                    emptyList()
                } else {
                    Files.list(directory).use { entries ->
                        entries.map { entry -> entry.fileName.toString() }
                            .filter { name -> name.startsWith("Binding") && name.endsWith(".class") }
                            .map { name -> "$pack.${name.removeSuffix(".class")}" }
                            .toList()
                    }
                }
            }
        }

        fun owns(packageName: String, className: String): Boolean {
            val prefix = packageName.replace('.', '/') + "/Binding"
            return className.startsWith(prefix)
        }

        fun classBytes(name: String): ByteArray = requireNotNull(
            EnginePortWiringTest::class.java.classLoader?.getResourceAsStream(
                name.replace('.', '/') + ".class",
            ),
        ) { "$name could not be read back from the class path" }.use { stream -> stream.readBytes() }

        /** The method references of one class file, read from its constant pool. */
        fun methodReferences(classFile: String): List<MethodReference> {
            val reader = DataInputStream(ByteArrayInputStream(classBytes(classFile)))
            fun u1() = reader.readUnsignedByte()
            fun u2() = reader.readUnsignedShort()
            fun u4() = reader.readInt()

            check(u4() == MAGIC) { "$classFile is not a class file" }
            u2()
            u2()
            val count = u2()
            val utf8 = mutableMapOf<Int, String>()
            val classNames = mutableMapOf<Int, Int>()
            val namesAndTypes = mutableMapOf<Int, Pair<Int, Int>>()
            val ownerAndType = mutableListOf<Pair<Int, Int>>()

            var index = 1
            while (index < count) {
                when (val tag = u1()) {
                    CONSTANT_UTF8 -> utf8[index] = reader.readUTF()
                    CONSTANT_INTEGER, CONSTANT_FLOAT -> reader.skipBytes(4)
                    CONSTANT_LONG, CONSTANT_DOUBLE -> {
                        reader.skipBytes(8)
                        index += 1 // eight-byte constants take two pool slots
                    }
                    CONSTANT_CLASS -> classNames[index] = u2()
                    CONSTANT_STRING, CONSTANT_METHOD_TYPE,
                    CONSTANT_MODULE, CONSTANT_PACKAGE,
                    -> reader.skipBytes(2)
                    CONSTANT_NAME_AND_TYPE -> namesAndTypes[index] = u2() to u2()
                    CONSTANT_METHOD_REF, CONSTANT_INTERFACE_METHOD_REF ->
                        ownerAndType += u2() to u2()
                    CONSTANT_FIELD_REF, CONSTANT_DYNAMIC, CONSTANT_INVOKE_DYNAMIC -> reader.skipBytes(4)
                    CONSTANT_METHOD_HANDLE -> reader.skipBytes(3)
                    else -> error("unknown constant pool tag $tag in $classFile")
                }
                index += 1
            }

            return ownerAndType.mapNotNull { (ownerIndex, typeIndex) ->
                val owner = classNames[ownerIndex]?.let(utf8::get) ?: return@mapNotNull null
                val name = namesAndTypes[typeIndex]?.let { (nameIndex, _) -> utf8[nameIndex] }
                    ?: return@mapNotNull null
                MethodReference(owner = owner, name = name)
            }
        }

        const val MAGIC = -0x35014542 // 0xCAFEBABE, as a signed Int
        const val CONSTANT_UTF8 = 1
        const val CONSTANT_INTEGER = 3
        const val CONSTANT_FLOAT = 4
        const val CONSTANT_LONG = 5
        const val CONSTANT_DOUBLE = 6
        const val CONSTANT_CLASS = 7
        const val CONSTANT_STRING = 8
        const val CONSTANT_FIELD_REF = 9
        const val CONSTANT_METHOD_REF = 10
        const val CONSTANT_INTERFACE_METHOD_REF = 11
        const val CONSTANT_NAME_AND_TYPE = 12
        const val CONSTANT_METHOD_HANDLE = 15
        const val CONSTANT_METHOD_TYPE = 16
        const val CONSTANT_DYNAMIC = 17
        const val CONSTANT_INVOKE_DYNAMIC = 18
        const val CONSTANT_MODULE = 19
        const val CONSTANT_PACKAGE = 20
    }

    private fun analysed(): List<PortClass> = portClassNames()
        .sorted()
        .map { name -> PortClass(name = name, references = methodReferences(name)) }

    @Test
    fun `the ports and their lambdas were found`() {
        val found = analysed()
        val nested = found.count { port -> port.isNested }

        assertTrue(
            "no port class was analysed, so this test would prove nothing " +
                "(class path: ${classPathSource()})",
            found.isNotEmpty(),
        )
        assertTrue(
            "the dispatcher lambdas were not among the analysed classes: $found",
            nested > 0,
        )
    }

    @Test
    fun `no port class calls its own outer port`() {
        val offenders = analysed()
            .filter { port -> port.isNested && port.ownOuterReferences.isNotEmpty() }
            .map { port ->
                "${port.name} -> ${port.ownOuterReferences.joinToString { reference -> reference.name }}"
            }

        assertTrue(
            "a nested port class that calls its own port recurses instead of reaching " +
                "the engine (on a device: StackOverflowError or a panel that never " +
                "finishes): $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `every port class reaches the generated binding`() {
        val silent = analysed()
            .filter { port -> port.isNested && !port.delegatesAway }
            .map { port -> port.name }

        assertTrue(
            "a nested port class that delegates to nothing but itself has no engine " +
                "behind it: $silent",
            silent.isEmpty(),
        )
    }
}
