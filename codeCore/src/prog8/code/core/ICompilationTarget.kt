package prog8.code.core

interface ICompilationTarget: IStringEncoding, IMemSizer {
    val name: String
    val machine: IMachineDefinition

    override fun encodeString(str: String, encoding: Encoding): List<UByte>
    override fun decodeString(bytes: Iterable<UByte>, encoding: Encoding): String
}
