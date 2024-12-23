package prog8.code.target.cbm

import prog8.code.core.BaseDataType
import prog8.code.core.DataType
import prog8.code.core.IMemSizer


internal object CbmMemorySizer: IMemSizer {
    override fun memorySize(dt: DataType, numElements: Int?): Int {
        if(dt.isArray) {
            if(numElements==null) return 2      // treat it as a pointer size
            return when(dt.sub) {
                BaseDataType.BOOL, BaseDataType.UBYTE, BaseDataType.BYTE -> numElements
                BaseDataType.UWORD, BaseDataType.WORD, BaseDataType.STR -> numElements * 2
                BaseDataType.FLOAT-> numElements * Mflpt5.FLOAT_MEM_SIZE
                BaseDataType.UNDEFINED -> throw IllegalArgumentException("undefined has no memory size")
                else -> throw IllegalArgumentException("invalid sub type")
            }
        }
        else if (dt.isString) {
            if(numElements!=null) return numElements        // treat it as the size of the given string with the length
            else return 2    // treat it as the size to store a string pointer
        }

        return when {
            dt.isByteOrBool -> 1 * (numElements ?: 1)
            dt.isFloat -> Mflpt5.FLOAT_MEM_SIZE * (numElements ?: 1)
            dt.isLong -> throw IllegalArgumentException("long can not yet be put into memory")
            dt.isUndefined -> throw IllegalArgumentException("undefined has no memory size")
            else -> 2 * (numElements ?: 1)
        }
    }
}