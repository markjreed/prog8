import prog8.code.core.DataType
import prog8.code.core.Encoding
import prog8.code.core.IMemSizer
import prog8.code.core.IStringEncoding


internal object DummyMemsizer : IMemSizer {
    override fun memorySize(dt: DataType) = 0
    override fun memorySize(arrayDt: DataType, numElements: Int) = 0
}

internal object DummyStringEncoder : IStringEncoding {
    override fun encodeString(str: String, encoding: Encoding): List<UByte> {
        return emptyList()
    }

    override fun decodeString(bytes: List<UByte>, encoding: Encoding): String {
        return ""
    }
}