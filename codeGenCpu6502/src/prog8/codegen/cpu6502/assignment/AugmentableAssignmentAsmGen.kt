package prog8.codegen.cpu6502.assignment

import prog8.code.ast.*
import prog8.code.core.*
import prog8.codegen.cpu6502.AsmGen6502Internal
import prog8.codegen.cpu6502.VariableAllocator


internal class AugmentableAssignmentAsmGen(private val program: PtProgram,
                                           private val assignmentAsmGen: AssignmentAsmGen,
                                           private val asmgen: AsmGen6502Internal,
                                           private val allocator: VariableAllocator
) {
    fun translate(assign: AsmAugmentedAssignment, scope: IPtSubroutine?) {

        when(assign.operator) {
            "-" -> {
                val a2 = AsmAssignment(assign.source, assign.target, assign.memsizer, assign.position)
                assignmentAsmGen.inplaceNegate(a2, false, scope)
            }
            "~", "not" -> {
                val a2 = AsmAssignment(assign.source, assign.target, assign.memsizer, assign.position)
                assignmentAsmGen.inplaceInvert(a2, scope)
            }
            "+" -> { /* is a nop */ }
            else -> {
                augmentedAssignExpr(assign)
            }
        }
    }

    private fun augmentedAssignExpr(assign: AsmAugmentedAssignment) {
        when (assign.operator) {
            "+=" -> inplaceModification(assign.target, "+", assign.source)
            "-=" -> inplaceModification(assign.target, "-", assign.source)
            "*=" -> inplaceModification(assign.target, "*", assign.source)
            "/=" -> inplaceModification(assign.target, "/", assign.source)
            "|=" -> inplaceModification(assign.target, "|", assign.source)
            "or=" -> inplaceModification(assign.target, "or", assign.source)
            "&=" -> inplaceModification(assign.target, "&", assign.source)
            "and=" -> inplaceModification(assign.target, "and", assign.source)
            "^=", "xor=" -> inplaceModification(assign.target, "^", assign.source)
            "<<=" -> inplaceModification(assign.target, "<<", assign.source)
            ">>=" -> inplaceModification(assign.target, ">>", assign.source)
            "%=" -> inplaceModification(assign.target, "%", assign.source)
            "==" -> inplaceModification(assign.target, "==", assign.source)
            "!=" -> inplaceModification(assign.target, "!=", assign.source)
            "<" -> inplaceModification(assign.target, "<", assign.source)
            ">" -> inplaceModification(assign.target, ">", assign.source)
            "<=" -> inplaceModification(assign.target, "<=", assign.source)
            ">=" -> inplaceModification(assign.target, ">=", assign.source)
            else -> throw AssemblyError("invalid augmented assign operator ${assign.operator}")
        }
    }

    private fun inplaceModification(target: AsmAssignTarget, operator: String, value: AsmAssignSource) {

        // the asm-gen code can deal with situations where you want to assign a byte into a word.
        // it will create the most optimized code to do this (so it type-extends for us).
        // But we can't deal with writing a word into a byte - explicit typeconversion should be done
        if(program.memsizer.memorySize(value.datatype) > program.memsizer.memorySize(target.datatype))
            throw AssemblyError("missing type cast: value type > target type  ${target.position}")

        fun regName(v: AsmAssignSource) = "cx16.${v.register!!.name.lowercase()}"

        when (target.kind) {
            TargetStorageKind.VARIABLE -> {
                when (target.datatype) {
                    in ByteDatatypesWithBoolean -> {
                        when(value.kind) {
                            SourceStorageKind.LITERALBOOLEAN -> inplacemodificationByteVariableWithLiteralval(target.asmVarname, target.datatype, operator, value.boolean!!.asInt())
                            SourceStorageKind.LITERALNUMBER -> inplacemodificationByteVariableWithLiteralval(target.asmVarname, target.datatype, operator, value.number!!.number.toInt())
                            SourceStorageKind.VARIABLE -> inplacemodificationByteVariableWithVariable(target.asmVarname, target.datatype, operator, value.asmVarname)
                            SourceStorageKind.REGISTER -> inplacemodificationByteVariableWithVariable(target.asmVarname, target.datatype, operator, regName(value))
                            SourceStorageKind.MEMORY -> inplacemodificationByteVariableWithValue(target.asmVarname, target.datatype, operator, value.memory!!)
                            SourceStorageKind.ARRAY -> inplacemodificationByteVariableWithValue(target.asmVarname, target.datatype, operator, value.array!!)
                            SourceStorageKind.EXPRESSION -> {
                                if(value.expression is PtTypeCast) {
                                    if (tryInplaceModifyWithRemovedRedundantCast(value.expression, target, operator)) return
                                    inplacemodificationByteVariableWithValue(target.asmVarname, target.datatype, operator, value.expression)
                                } else  {
                                    inplacemodificationByteVariableWithValue(target.asmVarname, target.datatype, operator, value.expression!!)
                                }
                            }
                        }
                    }
                    in WordDatatypes -> {
                        val block = target.origAstTarget?.definingBlock()
                        when(value.kind) {
                            SourceStorageKind.LITERALBOOLEAN -> inplacemodificationWordWithLiteralval(target.asmVarname, target.datatype, operator, value.boolean!!.asInt(), block)
                            SourceStorageKind.LITERALNUMBER -> inplacemodificationWordWithLiteralval(target.asmVarname, target.datatype, operator, value.number!!.number.toInt(), block)
                            SourceStorageKind.VARIABLE -> inplacemodificationWordWithVariable(target.asmVarname, target.datatype, operator, value.asmVarname, value.datatype, block)
                            SourceStorageKind.REGISTER -> inplacemodificationWordWithVariable(target.asmVarname, target.datatype, operator, regName(value), value.datatype, block)
                            SourceStorageKind.MEMORY -> inplacemodificationWordWithMemread(target.asmVarname, target.datatype, operator, value.memory!!)
                            SourceStorageKind.ARRAY -> inplacemodificationWordWithValue(target.asmVarname, target.datatype, operator, value.array!!, block)
                            SourceStorageKind.EXPRESSION -> {
                                if(value.expression is PtTypeCast) {
                                    if (tryInplaceModifyWithRemovedRedundantCast(value.expression, target, operator)) return
                                    inplacemodificationWordWithValue(target.asmVarname, target.datatype, operator, value.expression, block)
                                }
                                else {
                                    inplacemodificationWordWithValue(target.asmVarname, target.datatype, operator, value.expression!!, block)
                                }
                            }
                        }
                    }
                    DataType.FLOAT -> {
                        when(value.kind) {
                            SourceStorageKind.LITERALBOOLEAN -> inplacemodificationFloatWithLiteralval(target.asmVarname, operator, value.boolean!!.asInt().toDouble())
                            SourceStorageKind.LITERALNUMBER -> inplacemodificationFloatWithLiteralval(target.asmVarname, operator, value.number!!.number)
                            SourceStorageKind.VARIABLE -> inplacemodificationFloatWithVariable(target.asmVarname, operator, value.asmVarname)
                            SourceStorageKind.REGISTER -> inplacemodificationFloatWithVariable(target.asmVarname, operator, regName(value))
                            SourceStorageKind.MEMORY -> TODO("memread into float")
                            SourceStorageKind.ARRAY -> inplacemodificationFloatWithValue(target.asmVarname, operator, value.array!!)
                            SourceStorageKind.EXPRESSION -> {
                                if(value.expression is PtTypeCast) {
                                    if (tryInplaceModifyWithRemovedRedundantCast(value.expression, target, operator)) return
                                    inplacemodificationFloatWithValue(target.asmVarname, operator, value.expression)
                                } else {
                                    inplacemodificationFloatWithValue(target.asmVarname, operator, value.expression!!)
                                }
                            }
                        }
                    }
                    else -> throw AssemblyError("weird type to do in-place modification on ${target.datatype}")
                }
            }
            TargetStorageKind.MEMORY -> {
                val memory = target.memory!!
                when (memory.address) {
                    is PtNumber -> {
                        val addr = (memory.address as PtNumber).number.toInt()
                        when(value.kind) {
                            SourceStorageKind.LITERALBOOLEAN -> inplacemodificationByteVariableWithLiteralval(addr.toHex(), DataType.UBYTE, operator, value.boolean!!.asInt())
                            SourceStorageKind.LITERALNUMBER -> inplacemodificationByteVariableWithLiteralval(addr.toHex(), DataType.UBYTE, operator, value.number!!.number.toInt())
                            SourceStorageKind.VARIABLE -> inplacemodificationByteVariableWithVariable(addr.toHex(), DataType.UBYTE, operator, value.asmVarname)
                            SourceStorageKind.REGISTER -> inplacemodificationByteVariableWithVariable(addr.toHex(), DataType.UBYTE, operator, regName(value))
                            SourceStorageKind.MEMORY -> inplacemodificationByteVariableWithValue(addr.toHex(), DataType.UBYTE, operator, value.memory!!)
                            SourceStorageKind.ARRAY -> inplacemodificationByteVariableWithValue(addr.toHex(), DataType.UBYTE, operator, value.array!!)
                            SourceStorageKind.EXPRESSION -> {
                                if(value.expression is PtTypeCast) {
                                    if (tryInplaceModifyWithRemovedRedundantCast(value.expression, target, operator)) return
                                    inplacemodificationByteVariableWithValue(addr.toHex(), DataType.UBYTE, operator, value.expression)
                                } else {
                                    inplacemodificationByteVariableWithValue(addr.toHex(), DataType.UBYTE, operator, value.expression!!)
                                }
                            }
                        }
                    }
                    is PtIdentifier -> {
                        val pointer = memory.address as PtIdentifier
                        when(value.kind) {
                            SourceStorageKind.LITERALBOOLEAN -> inplacemodificationBytePointerWithLiteralval(pointer, operator, value.boolean!!.asInt())
                            SourceStorageKind.LITERALNUMBER -> inplacemodificationBytePointerWithLiteralval(pointer, operator, value.number!!.number.toInt())
                            SourceStorageKind.VARIABLE -> inplacemodificationBytePointerWithVariable(pointer, operator, value.asmVarname)
                            SourceStorageKind.REGISTER -> inplacemodificationBytePointerWithVariable(pointer, operator, regName(value))
                            SourceStorageKind.MEMORY -> TODO("memread into pointer")
                            SourceStorageKind.ARRAY -> inplacemodificationBytePointerWithValue(pointer, operator, value.array!!)
                            SourceStorageKind.EXPRESSION -> {
                                if(value.expression is PtTypeCast) {
                                    if (tryInplaceModifyWithRemovedRedundantCast(value.expression, target, operator)) return
                                    inplacemodificationBytePointerWithValue(pointer, operator, value.expression)
                                } else {
                                    inplacemodificationBytePointerWithValue(pointer, operator, value.expression!!)
                                }
                            }
                        }
                    }
                    else -> {
                        if(memory.address is PtBinaryExpression && tryOptimizedMemoryInplace(memory.address as PtBinaryExpression, operator, value))
                            return
                        asmgen.assignExpressionToRegister(memory.address, RegisterOrPair.AY, false)
                        asmgen.saveRegisterStack(CpuRegister.A, true)
                        asmgen.saveRegisterStack(CpuRegister.Y, true)
                        asmgen.out("  jsr  prog8_lib.read_byte_from_address_in_AY_into_A")
                        when(value.kind) {
                            SourceStorageKind.LITERALBOOLEAN -> {
                                inplacemodificationRegisterAwithVariable(operator, "#${value.boolean!!.asInt()}", false)
                                asmgen.out("  tax")
                            }
                            SourceStorageKind.LITERALNUMBER -> {
                                inplacemodificationRegisterAwithVariable(operator, "#${value.number!!.number.toInt()}", false)
                                asmgen.out("  tax")
                            }
                            SourceStorageKind.VARIABLE -> {
                                inplacemodificationRegisterAwithVariable(operator, value.asmVarname, false)
                                asmgen.out("  tax")
                            }
                            SourceStorageKind.REGISTER -> {
                                inplacemodificationRegisterAwithVariable(operator, regName(value), false)
                                asmgen.out("  tax")
                            }
                            SourceStorageKind.MEMORY -> {
                                asmgen.out("  sta  P8ZP_SCRATCH_B1")
                                inplacemodificationByteVariableWithValue("P8ZP_SCRATCH_B1", DataType.UBYTE, operator, value.memory!!)
                                asmgen.out("  ldx  P8ZP_SCRATCH_B1")
                            }
                            SourceStorageKind.ARRAY -> {
                                asmgen.out("  sta  P8ZP_SCRATCH_B1")
                                inplacemodificationByteVariableWithValue("P8ZP_SCRATCH_B1", DataType.UBYTE, operator, value.array!!)
                                asmgen.out("  ldx  P8ZP_SCRATCH_B1")
                            }
                            SourceStorageKind.EXPRESSION -> {
                                val tempVar = asmgen.getTempVarName(DataType.UBYTE)
                                asmgen.out("  sta  $tempVar")
                                if(value.expression is PtTypeCast)
                                    inplacemodificationByteVariableWithValue(tempVar, DataType.UBYTE, operator, value.expression)
                                else
                                    inplacemodificationByteVariableWithValue(tempVar, DataType.UBYTE, operator, value.expression!!)
                                asmgen.out("  ldx  $tempVar")
                            }
                        }
                        asmgen.restoreRegisterStack(CpuRegister.Y, false)
                        asmgen.restoreRegisterStack(CpuRegister.A, false)
                        asmgen.out("  jsr  prog8_lib.write_byte_X_to_address_in_AY")
                    }
                }
            }
            TargetStorageKind.ARRAY -> {
                val indexNum = target.array!!.index as? PtNumber
                if (indexNum!=null) {
                    val index = indexNum.number.toInt()
                    if(target.array.splitWords) {
                        when(value.kind) {
                            SourceStorageKind.LITERALNUMBER -> inplacemodificationSplitWordWithLiteralval(target.asmVarname, target.datatype, index, operator, value.number!!.number.toInt())
                            else -> {
                                // TODO: more optimized code for VARIABLE, REGISTER, MEMORY, ARRAY, EXPRESSION in the case of split-word arrays
                                val scope = target.origAstTarget?.definingISub()
                                val regTarget = AsmAssignTarget.fromRegisters(RegisterOrPair.R0, false, target.position, scope, asmgen)
                                val assignToReg = AsmAssignment(value, regTarget, program.memsizer, target.position)
                                assignmentAsmGen.translateNormalAssignment(assignToReg, scope)
                                inplacemodificationSplitWordWithR0(target.asmVarname, index, operator)
                            }
                        }
                        return
                    }
                    // normal array
                    val targetVarName = "${target.asmVarname} + ${index*program.memsizer.memorySize(target.datatype)}"
                    when (target.datatype) {
                        in ByteDatatypesWithBoolean -> {
                            when(value.kind) {
                                SourceStorageKind.LITERALBOOLEAN -> inplacemodificationByteVariableWithLiteralval(targetVarName, target.datatype, operator, value.boolean!!.asInt())
                                SourceStorageKind.LITERALNUMBER -> inplacemodificationByteVariableWithLiteralval(targetVarName, target.datatype, operator, value.number!!.number.toInt())
                                SourceStorageKind.VARIABLE -> inplacemodificationByteVariableWithVariable(targetVarName, target.datatype, operator, value.asmVarname)
                                SourceStorageKind.REGISTER -> inplacemodificationByteVariableWithVariable(targetVarName, target.datatype, operator, regName(value))
                                SourceStorageKind.MEMORY -> inplacemodificationByteVariableWithValue(targetVarName, target.datatype, operator, value.memory!!)
                                SourceStorageKind.ARRAY -> inplacemodificationByteVariableWithValue(targetVarName, target.datatype, operator, value.array!!)
                                SourceStorageKind.EXPRESSION -> {
                                    if(value.expression is PtTypeCast) {
                                        if (tryInplaceModifyWithRemovedRedundantCast(value.expression, target, operator)) return
                                        inplacemodificationByteVariableWithValue(targetVarName, target.datatype, operator, value.expression)
                                    } else {
                                        inplacemodificationByteVariableWithValue(targetVarName, target.datatype, operator, value.expression!!)
                                    }
                                }
                            }
                        }

                        in WordDatatypes -> {
                            val block = target.origAstTarget?.definingBlock()
                            when(value.kind) {
                                SourceStorageKind.LITERALBOOLEAN -> inplacemodificationWordWithLiteralval(targetVarName, target.datatype, operator, value.boolean!!.asInt(), block)
                                SourceStorageKind.LITERALNUMBER -> inplacemodificationWordWithLiteralval(targetVarName, target.datatype, operator, value.number!!.number.toInt(), block)
                                SourceStorageKind.VARIABLE -> inplacemodificationWordWithVariable(targetVarName, target.datatype, operator, value.asmVarname, value.datatype, block)
                                SourceStorageKind.REGISTER -> inplacemodificationWordWithVariable(targetVarName, target.datatype, operator, regName(value), value.datatype, block)
                                SourceStorageKind.MEMORY -> inplacemodificationWordWithMemread(targetVarName, target.datatype, operator, value.memory!!)
                                SourceStorageKind.ARRAY -> inplacemodificationWordWithValue(targetVarName, target.datatype, operator, value.array!!, block)
                                SourceStorageKind.EXPRESSION -> {
                                    if(value.expression is PtTypeCast) {
                                        if (tryInplaceModifyWithRemovedRedundantCast(value.expression, target, operator)) return
                                        inplacemodificationWordWithValue(targetVarName, target.datatype, operator, value.expression, block)
                                    } else {
                                        inplacemodificationWordWithValue(targetVarName, target.datatype, operator, value.expression!!, block)
                                    }
                                }
                            }
                        }

                        DataType.FLOAT -> {
                            when(value.kind) {
                                SourceStorageKind.LITERALBOOLEAN -> inplacemodificationFloatWithLiteralval(targetVarName, operator, value.boolean!!.asInt().toDouble())
                                SourceStorageKind.LITERALNUMBER -> inplacemodificationFloatWithLiteralval(targetVarName, operator, value.number!!.number)
                                SourceStorageKind.VARIABLE -> inplacemodificationFloatWithVariable(targetVarName, operator, value.asmVarname)
                                SourceStorageKind.REGISTER -> inplacemodificationFloatWithVariable(targetVarName, operator, regName(value))
                                SourceStorageKind.MEMORY -> TODO("memread into float array")
                                SourceStorageKind.ARRAY -> inplacemodificationFloatWithValue(targetVarName, operator, value.array!!)
                                SourceStorageKind.EXPRESSION -> {
                                    if(value.expression is PtTypeCast) {
                                        if (tryInplaceModifyWithRemovedRedundantCast(value.expression, target, operator)) return
                                        inplacemodificationFloatWithValue(targetVarName, operator, value.expression)
                                    } else {
                                        inplacemodificationFloatWithValue(targetVarName, operator, value.expression!!)
                                    }
                                }
                            }
                        }

                        else -> throw AssemblyError("weird type to do in-place modification on ${target.datatype}")
                    }
                }
                else {
                    if(value.kind==SourceStorageKind.LITERALNUMBER
                        && operator in "+-"
                        && value.number!!.number==1.0
                        && tryIndexedIncDec(target.array, operator))
                        return

                    when (target.datatype) {
                        in ByteDatatypesWithBoolean -> {
                            if(value.kind==SourceStorageKind.EXPRESSION
                                && value.expression is PtTypeCast
                                && tryInplaceModifyWithRemovedRedundantCast(value.expression, target, operator))
                                return
                            asmgen.loadScaledArrayIndexIntoRegister(target.array, CpuRegister.Y)
                            asmgen.saveRegisterStack(CpuRegister.Y, false)
                            asmgen.out("  lda  ${target.array.variable.name},y")
                            when(value.kind) {
                                SourceStorageKind.LITERALBOOLEAN -> {
                                    inplacemodificationRegisterAwithVariable(operator, "#${value.boolean!!.asInt()}", target.datatype in SignedDatatypes)
                                    asmgen.restoreRegisterStack(CpuRegister.Y, true)
                                }
                                SourceStorageKind.LITERALNUMBER -> {
                                    inplacemodificationRegisterAwithVariable(operator, "#${value.number!!.number.toInt()}", target.datatype in SignedDatatypes)
                                    asmgen.restoreRegisterStack(CpuRegister.Y, true)
                                }

                                SourceStorageKind.VARIABLE -> {
                                    inplacemodificationRegisterAwithVariable(operator, value.asmVarname, target.datatype in SignedDatatypes)
                                    asmgen.restoreRegisterStack(CpuRegister.Y, true)
                                }

                                SourceStorageKind.REGISTER -> {
                                    inplacemodificationRegisterAwithVariable(operator, regName(value), target.datatype in SignedDatatypes)
                                    asmgen.restoreRegisterStack(CpuRegister.Y, true)
                                }

                                SourceStorageKind.MEMORY -> {
                                    asmgen.out("  sta  P8ZP_SCRATCH_B1")
                                    inplacemodificationByteVariableWithValue("P8ZP_SCRATCH_B1", target.datatype, operator, value.memory!!)
                                    asmgen.restoreRegisterStack(CpuRegister.Y, false)
                                    asmgen.out("  lda  P8ZP_SCRATCH_B1")
                                }

                                SourceStorageKind.ARRAY -> {
                                    asmgen.out("  sta  P8ZP_SCRATCH_B1")
                                    inplacemodificationByteVariableWithValue("P8ZP_SCRATCH_B1", target.datatype, operator, value.array!!)
                                    asmgen.restoreRegisterStack(CpuRegister.Y, false)
                                    asmgen.out("  lda  P8ZP_SCRATCH_B1")
                                }

                                SourceStorageKind.EXPRESSION -> {
                                    val tempVar = asmgen.getTempVarName(DataType.UBYTE)
                                    asmgen.out("  sta  $tempVar")
                                    if(value.expression is PtTypeCast)
                                        inplacemodificationByteVariableWithValue(tempVar, target.datatype, operator, value.expression)
                                    else
                                        inplacemodificationByteVariableWithValue(tempVar, target.datatype, operator, value.expression!!)
                                    asmgen.restoreRegisterStack(CpuRegister.Y, false)
                                    asmgen.out("  lda  $tempVar")
                                }
                            }
                            asmgen.out("  sta  ${target.array.variable.name},y")
                        }

                        in WordDatatypes -> {
                            if(value.kind==SourceStorageKind.EXPRESSION
                                && value.expression is PtTypeCast
                                && tryInplaceModifyWithRemovedRedundantCast(value.expression, target, operator))
                                return
                            asmgen.loadScaledArrayIndexIntoRegister(target.array, CpuRegister.Y)
                            asmgen.saveRegisterStack(CpuRegister.Y, false)
                            if(target.array.splitWords) {
                                asmgen.out("  lda  ${target.array.variable.name}_lsb,y")
                                asmgen.out("  ldx  ${target.array.variable.name}_msb,y")
                            } else {
                                asmgen.out("  lda  ${target.array.variable.name},y")
                                asmgen.out("  ldx  ${target.array.variable.name}+1,y")
                            }
                            val block = target.origAstTarget?.definingBlock()
                            when(value.kind) {
                                SourceStorageKind.LITERALBOOLEAN -> {
                                    val number = value.boolean!!.asInt()
                                    if(!inplacemodificationRegisterAXwithLiteralval(operator, number)) {
                                        asmgen.out("  sta  P8ZP_SCRATCH_W1 |  stx  P8ZP_SCRATCH_W1+1")
                                        inplacemodificationWordWithLiteralval("P8ZP_SCRATCH_W1", target.datatype, operator, number, block)
                                        asmgen.out("  lda  P8ZP_SCRATCH_W1 |  ldx  P8ZP_SCRATCH_W1+1")
                                    }
                                }
                                SourceStorageKind.LITERALNUMBER -> {
                                    val number = value.number!!.number.toInt()
                                    if(!inplacemodificationRegisterAXwithLiteralval(operator, number)) {
                                        asmgen.out("  sta  P8ZP_SCRATCH_W1 |  stx  P8ZP_SCRATCH_W1+1")
                                        inplacemodificationWordWithLiteralval("P8ZP_SCRATCH_W1", target.datatype, operator, number, block)
                                        asmgen.out("  lda  P8ZP_SCRATCH_W1 |  ldx  P8ZP_SCRATCH_W1+1")
                                    }
                                }

                                SourceStorageKind.VARIABLE -> {
                                    if(!inplacemodificationRegisterAXwithVariable(
                                            operator,
                                            value.asmVarname,
                                            value.datatype
                                        )) {
                                        asmgen.out("  sta  P8ZP_SCRATCH_W1 |  stx  P8ZP_SCRATCH_W1+1")
                                        inplacemodificationWordWithVariable("P8ZP_SCRATCH_W1", target.datatype, operator, value.asmVarname, value.datatype, block)
                                        asmgen.out("  lda  P8ZP_SCRATCH_W1 |  ldx  P8ZP_SCRATCH_W1+1")
                                    }
                                }

                                SourceStorageKind.REGISTER -> {
                                    if(!inplacemodificationRegisterAXwithVariable(
                                            operator,
                                            regName(value),
                                            value.datatype
                                        )) {
                                        asmgen.out("  sta  P8ZP_SCRATCH_W1 |  stx  P8ZP_SCRATCH_W1+1")
                                        inplacemodificationWordWithVariable("P8ZP_SCRATCH_W1", target.datatype, operator, regName(value), value.datatype, block)
                                        asmgen.out("  lda  P8ZP_SCRATCH_W1 |  ldx  P8ZP_SCRATCH_W1+1")
                                    }
                                }

                                SourceStorageKind.MEMORY -> {
                                    asmgen.out("  sta  P8ZP_SCRATCH_W1 |  stx  P8ZP_SCRATCH_W1+1")
                                    inplacemodificationWordWithMemread("P8ZP_SCRATCH_W1", target.datatype, operator, value.memory!!)
                                    asmgen.out("  lda  P8ZP_SCRATCH_W1 |  ldx  P8ZP_SCRATCH_W1+1")
                                }

                                SourceStorageKind.ARRAY -> {
                                    asmgen.out("  sta  P8ZP_SCRATCH_W1 |  stx  P8ZP_SCRATCH_W1+1")
                                    inplacemodificationWordWithValue("P8ZP_SCRATCH_W1", target.datatype, operator, value.array!!, block)
                                    asmgen.out("  lda  P8ZP_SCRATCH_W1 |  ldx  P8ZP_SCRATCH_W1+1")
                                }

                                SourceStorageKind.EXPRESSION -> {
                                    val tempVar = asmgen.getTempVarName(DataType.UWORD)
                                    asmgen.out("  sta  $tempVar |  stx  $tempVar+1")
                                    if(value.expression is PtTypeCast)
                                        inplacemodificationWordWithValue(tempVar, target.datatype, operator, value.expression, block)
                                    else
                                        inplacemodificationWordWithValue(tempVar, target.datatype, operator, value.expression!!, block)
                                    asmgen.out("  lda  $tempVar |  ldx  $tempVar+1")
                                }
                            }
                            asmgen.restoreRegisterStack(CpuRegister.Y, true)
                            if(target.array.splitWords)
                                asmgen.out("  sta  ${target.array.variable.name}_lsb,y |  txa |  sta  ${target.array.variable.name}_msb,y")
                            else
                                asmgen.out("  sta  ${target.array.variable.name},y |  txa |  sta  ${target.array.variable.name}+1,y")
                        }

                        DataType.FLOAT -> {
                            // copy array value into tempvar
                            val tempvar = asmgen.getTempVarName(DataType.FLOAT)
                            asmgen.loadScaledArrayIndexIntoRegister(target.array, CpuRegister.A)
                            asmgen.out("""
                                                ldy  #>${target.asmVarname}
                                                clc
                                                adc  #<${target.asmVarname}
                                                bcc  +
                                                iny
            +                                   sta  P8ZP_SCRATCH_W1
                                                sty  P8ZP_SCRATCH_W1+1
                                                pha  ; save array ptr lsb
                                                tya
                                                pha  ; save array ptr msb
                                                lda  #<$tempvar
                                                ldy  #>$tempvar
                                                jsr  floats.copy_float""")

                            // calculate on tempvar
                            when(value.kind) {
                                SourceStorageKind.LITERALBOOLEAN -> inplacemodificationFloatWithLiteralval(tempvar, operator, value.boolean!!.asInt().toDouble())
                                SourceStorageKind.LITERALNUMBER -> inplacemodificationFloatWithLiteralval(tempvar, operator, value.number!!.number)
                                SourceStorageKind.VARIABLE -> inplacemodificationFloatWithVariable(tempvar, operator, value.asmVarname)
                                SourceStorageKind.REGISTER -> inplacemodificationFloatWithVariable(tempvar, operator, regName(value))
                                SourceStorageKind.MEMORY -> TODO("memread into float")
                                SourceStorageKind.ARRAY -> inplacemodificationFloatWithValue(tempvar, operator, value.array!!)
                                SourceStorageKind.EXPRESSION -> {
                                    if(value.expression is PtTypeCast) {
                                        if (tryInplaceModifyWithRemovedRedundantCast(value.expression, target, operator))
                                            return
                                        inplacemodificationFloatWithValue(tempvar, operator, value.expression)
                                    } else {
                                        inplacemodificationFloatWithValue(tempvar, operator, value.expression!!)
                                    }
                                }
                            }

                            // copy tempvar back into array
                            asmgen.out("""
                                                lda  #<$tempvar
                                                ldy  #>$tempvar
                                                sta  P8ZP_SCRATCH_W1
                                                sty  P8ZP_SCRATCH_W1+1
                                                pla  ; restore array ptr msb
                                                tay
                                                pla  ; restore array ptr lsb
                                                jsr  floats.copy_float""")
                        }

                        else -> throw AssemblyError("weird type to do in-place modification on ${target.datatype}")
                    }
                }
            }
            TargetStorageKind.REGISTER -> throw AssemblyError("no asm gen for reg in-place modification")
        }
    }

    private fun tryIndexedIncDec(array: PtArrayIndexer, operator: String): Boolean {
        val arrayvar = asmgen.asmVariableName(array.variable)
        when (array.type) {
            in ByteDatatypes -> {
                asmgen.loadScaledArrayIndexIntoRegister(array, CpuRegister.X)
                if (operator == "+")
                    asmgen.out("  inc  $arrayvar,x")
                else
                    asmgen.out("  dec  $arrayvar,x")
                return true
            }
            in WordDatatypes -> {
                asmgen.loadScaledArrayIndexIntoRegister(array, CpuRegister.X)
                if(array.splitWords) {
                    if(operator=="+") {
                        asmgen.out("""
                            inc  ${arrayvar}_lsb,x
                            bne  +
                            inc  ${arrayvar}_msb,x
+""")
                    } else {
                        asmgen.out("""
                            lda  ${arrayvar}_lsb,x
                            bne  +
                            dec  ${arrayvar}_msb,x
+                           dec  ${arrayvar}_lsb,x""")
                    }
                    return true
                } else {
                    if(operator=="+") {
                        asmgen.out("""
                            inc  $arrayvar,x
                            bne  +
                            inc  $arrayvar+1,x
+""")
                    } else {
                        asmgen.out("""
                            lda  $arrayvar,x
                            bne  +
                            dec  $arrayvar+1,x
+                           dec  $arrayvar,x""")
                    }
                    return true
                }
            }
            DataType.FLOAT -> {
                asmgen.loadScaledArrayIndexIntoRegister(array, CpuRegister.A)
                asmgen.out("""
                    ldy  #>$arrayvar
                    clc
                    adc  #<$arrayvar
                    bcc  +
                    iny
+""")
                asmgen.out(if(operator=="+") "  jsr  floats.inc_var_f" else "  jsr  floats.dec_var_f")
                return true
            }
            else -> return false
        }
    }

    private fun tryOptimizedMemoryInplace(address: PtBinaryExpression, operator: String, value: AsmAssignSource): Boolean {
        if(value.datatype !in ByteDatatypes || operator !in "|&^+-")
            return false

        fun addrIntoZpPointer(): String {
            if(address.left is PtIdentifier && asmgen.isZpVar(address.left as PtIdentifier)) {
                return (address.left as PtIdentifier).name
            } else {
                asmgen.assignExpressionToRegister(address.left, RegisterOrPair.AY, false)
                asmgen.out("  sta  P8ZP_SCRATCH_W2 |  sty  P8ZP_SCRATCH_W2+1")
                return "P8ZP_SCRATCH_W2"
            }
        }

        fun assignValueToA() {
            val assignValue = AsmAssignment(value,
                AsmAssignTarget(TargetStorageKind.REGISTER, asmgen, DataType.UBYTE,
                    address.definingISub(), Position.DUMMY, register = RegisterOrPair.A),
                program.memsizer, Position.DUMMY)
            assignmentAsmGen.translateNormalAssignment(assignValue, address.definingISub())   // calculate value into A
        }

        val rightTc = address.right as? PtTypeCast
        val constOffset = (address.right as? PtNumber)?.number?.toInt()
        if(address.operator=="+" && (address.right.type in ByteDatatypes || (rightTc!=null && rightTc.value.type in ByteDatatypes) || (constOffset!=null && constOffset<256)) ) {
            if(constOffset!=null) {
                val zpPointerVarName = addrIntoZpPointer()
                if(value.number==null) assignValueToA()
                asmgen.out("  ldy  #$constOffset")
                when(operator) {
                    "|" -> {
                        if(value.number!=null) asmgen.out("  lda  #${value.number.number.toInt()}")
                        asmgen.out("  ora  ($zpPointerVarName),y")
                    }
                    "&" -> {
                        if(value.number!=null) asmgen.out("  lda  #${value.number.number.toInt()}")
                        asmgen.out("  and  ($zpPointerVarName),y")
                    }
                    "^" -> {
                        if(value.number!=null) asmgen.out("  lda  #${value.number.number.toInt()}")
                        asmgen.out("  eor  ($zpPointerVarName),y")
                    }
                    "+" -> {
                        // note: there is no  inc (ZP),y  instruction...
                        if (value.number != null) asmgen.out("  lda  #${value.number.number.toInt()}")
                        asmgen.out("  clc |  adc  ($zpPointerVarName),y")
                    }
                    "-" -> {
                        // note: there is no  dec (ZP),y  instruction...
                        if (value.number != null) asmgen.out("  lda  ($zpPointerVarName),y |  sec |  sbc  #${value.number.number.toInt()}")
                        else asmgen.out("  sta  P8ZP_SCRATCH_REG |  lda  ($zpPointerVarName),y |  sec |  sbc  P8ZP_SCRATCH_REG")
                    }
                    else -> throw AssemblyError("invalid op")
                }
                asmgen.out("  sta  ($zpPointerVarName),y")
                return true
            }
            if(rightTc!=null)
                asmgen.assignExpressionToRegister(rightTc.value, RegisterOrPair.A, false)
            else
                asmgen.assignExpressionToRegister(address.right, RegisterOrPair.A, false)
            asmgen.out("  pha")     // offset on stack
            val zpPointerVarName = addrIntoZpPointer()
            assignValueToA()
            asmgen.restoreRegisterStack(CpuRegister.Y, true)   // offset from stack back into Y
            when(operator) {
                "|" -> asmgen.out("  ora  ($zpPointerVarName),y")
                "&" -> asmgen.out("  and  ($zpPointerVarName),y")
                "^" -> asmgen.out("  eor  ($zpPointerVarName),y")
                "+" -> asmgen.out("  clc |  adc  ($zpPointerVarName),y")
                "-" -> asmgen.out("  sta  P8ZP_SCRATCH_REG |  lda  ($zpPointerVarName),y |  sec |  sbc  P8ZP_SCRATCH_REG")
                else -> throw AssemblyError("invalid op")
            }
            asmgen.out("  sta  ($zpPointerVarName),y")
            return true
        }
        return false
    }

    private fun inplacemodificationSplitWordWithR0(arrayVar: String, index: Int, operator: String) {
        when (operator) {
            "+" -> {
                asmgen.out("""
                    lda  ${arrayVar}_lsb+$index
                    clc
                    adc  cx16.r0L
                    sta  ${arrayVar}_lsb+$index
                    lda  ${arrayVar}_msb+$index
                    adc  cx16.r0H
                    sta  ${arrayVar}_msb+$index""")
            }
            "-" -> {
                asmgen.out("""
                    lda  ${arrayVar}_lsb+$index
                    sec
                    sbc  cx16.r0L
                    sta  ${arrayVar}_lsb+$index
                    lda  ${arrayVar}_msb+$index
                    sbc  cx16.r0H
                    sta  ${arrayVar}_msb+$index""")
            }
            else -> TODO("in-place modify split-words array value for operator $operator")
        }
    }

    private fun inplacemodificationSplitWordWithLiteralval(arrayVar: String, dt: DataType, index: Int, operator: String, value: Int) {
        // note: this contains special optimized cases because we know the exact value. Don't replace this with another routine.
        inplacemodificationSomeWordWithLiteralval("${arrayVar}_lsb+$index", "${arrayVar}_msb+$index", dt, operator, value, null)
    }

    private fun inplacemodificationRegisterAXwithVariable(operator: String, variable: String, varDt: DataType): Boolean {
        when(operator) {
            "+" -> {
                return if(varDt in WordDatatypes) {
                    asmgen.out("""
                        clc
                        adc  $variable
                        tay
                        txa
                        adc  $variable+1
                        tax
                        tya""")
                    true
                } else {
                    asmgen.out("""
                        ldy  $variable
                        bpl  +
                        dex     ; sign extend
+                       clc
                        adc  $variable
                        bcc  +
                        inx
+""")
                    true
                }
            }
            "-" -> {
                return if(varDt in WordDatatypes) {
                    asmgen.out("""
                        sec
                        sbc  $variable
                        tay
                        txa
                        sbc  $variable+1
                        tax
                        tya""")
                    true
                } else {
                    asmgen.out("""
                        ldy  $variable
                        bpl  +
                        inx     ; sign extend
+                       sec
                        sbc  $variable
                        bcs  +
                        dex
+""")
                    true
                }
            }
            "|" -> {
                asmgen.out("""
                    ora  $variable
                    tay
                    txa
                    ora  $variable+1
                    tax
                    tya
                """)
                return true
            }
            "&" -> {
                asmgen.out("""
                    and  $variable
                    tay
                    txa
                    and  $variable+1
                    tax
                    tya
                """)
                return true
            }
            "^" -> {
                asmgen.out("""
                    eor  $variable
                    tay
                    txa
                    eor  $variable+1
                    tax
                    tya
                """)
                return true
            }
            else -> return false
        }
    }

    private fun inplacemodificationRegisterAXwithLiteralval(operator: String, number: Int): Boolean {
        when(operator) {
            "+" -> {
                if(number==1) {
                    if(asmgen.isTargetCpu(CpuType.CPU65c02)) {
                        asmgen.out("""
                            inc  a
                            bne  +
                            iny
+""")
                    } else {
                        asmgen.out("""
                            clc
                            adc  #1
                            bne  +
                            iny
+""")
                    }
                    return true
                }
                return if(number in -128..255) {
                    asmgen.out("""
                        clc
                        adc  #$number
                        bcc  +
                        inx
+""")
                    true
                } else {
                    asmgen.out("""
                        clc
                        adc  #<$number
                        tay
                        txa
                        adc  #>$number
                        tax
                        tya""")
                    true
                }
            }
            "-" -> {
                if(number==1) {
                    if(asmgen.isTargetCpu(CpuType.CPU65c02)) {
                        asmgen.out("""
                            cmp  #0
                            bne  +
                            dey
+                           dec  a""")
                    } else {
                        asmgen.out("""
                            cmp  #0
                            bne  +
                            dey
+                           sec
                            sbc  #1""")
                    }
                    return true
                }
                return if(number in -128..255) {
                    asmgen.out("""
                        sec
                        sbc  #$number
                        bcs  +
                        dex
+""")
                    true
                } else {
                    asmgen.out("""
                        sec
                        sbc  #<$number
                        tay
                        txa
                        sbc  #>$number
                        tax
                        tya""")
                    true
                }
            }
            "|" -> {
                asmgen.out("""
                    ora  #<$number
                    tay
                    txa
                    ora  #>$number
                    tax
                    tya
                """)
                return true
            }
            "&" -> {
                asmgen.out("""
                    and  #<$number
                    tay
                    txa
                    and  #>$number
                    tax
                    tya
                """)
                return true
            }
            "^" -> {
                asmgen.out("""
                    eor  #<$number
                    tay
                    txa
                    eor  #>$number
                    tax
                    tya
                """)
                return true
            }
            else -> return false
        }
    }

    private fun tryInplaceModifyWithRemovedRedundantCast(value: PtTypeCast, target: AsmAssignTarget, operator: String): Boolean {
        if (target.datatype == value.type) {
            val childDt = value.value.type
            if (value.type!=DataType.FLOAT && (value.type.equalsSize(childDt) || value.type.largerThan(childDt))) {
                // this typecast is redundant here; the rest of the code knows how to deal with the uncasted value.
                // (works for integer types, not for float.)
                val src = AsmAssignSource.fromAstSource(value.value, program, asmgen)
                inplaceModification(target, operator, src)
                return true
            }
        }
        return false
    }

    private fun inplacemodificationBytePointerWithValue(pointervar: PtIdentifier, operator: String, value: PtExpression) {
        asmgen.assignExpressionToVariable(value, "P8ZP_SCRATCH_B1", DataType.UBYTE)
        inplacemodificationBytePointerWithVariable(pointervar, operator, "P8ZP_SCRATCH_B1")
    }

    private fun inplacemodificationBytePointerWithVariable(pointervar: PtIdentifier, operator: String, otherName: String) {
        val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)

        when (operator) {
            "+" -> asmgen.out("  clc |  adc  $otherName")
            "-" -> asmgen.out("  sec |  sbc  $otherName")
            "*" -> asmgen.out("  ldy  $otherName |  jsr  math.multiply_bytes")
            "/" -> asmgen.out("  ldy  $otherName |  jsr  math.divmod_ub_asm |  tya")
            "%" -> asmgen.out("  ldy  $otherName |  jsr  math.divmod_ub_asm")
            "<<" -> {
                asmgen.out("""
                        ldy  $otherName
                        beq  + 
-                       asl  a
                        dey
                        bne  -
+""")
            }
            ">>" -> {
                asmgen.out("""
                        ldy  $otherName
                        beq  + 
-                       lsr  a
                        dey
                        bne  -
+""")
            }
            "&" -> asmgen.out(" and  $otherName")
            "|" -> asmgen.out(" ora  $otherName")
            "^" -> asmgen.out(" eor  $otherName")
            "==" -> {
                asmgen.out("""
                    cmp  $otherName
                    beq  +
                    lda  #0
                    beq  ++
+                   lda  #1
+""")
            }
            "!=" -> {
                asmgen.out("""
                    cmp  $otherName
                    bne  +
                    lda  #0
                    beq  ++
+                   lda  #1
+""")
            }
            // pretty uncommon, who's going to assign a comparison boolean expression to a pointer?
            "<", "<=", ">", ">=" -> TODO("byte-var-to-pointer comparisons")
            else -> throw AssemblyError("invalid operator for in-place modification $operator")
        }
        asmgen.storeAIntoZpPointerVar(sourceName, false)
    }

    private fun inplacemodificationBytePointerWithLiteralval(pointervar: PtIdentifier, operator: String, value: Int) {
        // note: this contains special optimized cases because we know the exact value. Don't replace this with another routine.
        when (operator) {
            "+" -> {
                if(value==1) {
                    asmgen.assignExpressionToRegister(pointervar, RegisterOrPair.AY)
                    asmgen.out("  sta  (+) + 1 |  sty  (+) + 2")
                    asmgen.out("+\tinc  ${'$'}ffff\t; modified")
                } else {
                    val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)
                    asmgen.out("  clc |  adc  #$value")
                    asmgen.storeAIntoZpPointerVar(sourceName, false)
                }
            }
            "-" -> {
                if(value==1) {
                    asmgen.assignExpressionToRegister(pointervar, RegisterOrPair.AY)
                    asmgen.out("  sta  (+) + 1 |  sty  (+) + 2")
                    asmgen.out("+\tdec  ${'$'}ffff\t; modified")
                } else {
                    val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)
                    asmgen.out("  sec |  sbc  #$value")
                    asmgen.storeAIntoZpPointerVar(sourceName, false)
                }
            }
            "*" -> {
                val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)
                if(value in asmgen.optimizedByteMultiplications)
                    asmgen.out("  jsr  math.mul_byte_${value}")
                else
                    asmgen.out("  ldy  #$value |  jsr  math.multiply_bytes")
                asmgen.storeAIntoZpPointerVar(sourceName, false)
            }
            "/" -> {
                val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)
                if(value==0)
                    throw AssemblyError("division by zero")
                asmgen.out("  ldy  #$value |  jsr  math.divmod_ub_asm |  tya")
                asmgen.storeAIntoZpPointerVar(sourceName, false)
            }
            "%" -> {
                val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)
                if(value==0)
                    throw AssemblyError("division by zero")
                asmgen.out("  ldy  #$value |  jsr  math.divmod_ub_asm")
                asmgen.storeAIntoZpPointerVar(sourceName, false)
            }
            "<<" -> {
                if (value > 0) {
                    val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)
                    repeat(value) { asmgen.out("  asl  a") }
                    asmgen.storeAIntoZpPointerVar(sourceName, false)
                }
            }
            ">>" -> {
                if (value > 0) {
                    val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)
                    repeat(value) { asmgen.out("  lsr  a") }
                    asmgen.storeAIntoZpPointerVar(sourceName, false)
                }
            }
            "&" -> {
                val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out("  and  #$value")
                asmgen.storeAIntoZpPointerVar(sourceName, false)
            }
            "|"-> {
                val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out("  ora  #$value")
                asmgen.storeAIntoZpPointerVar(sourceName, false)
            }
            "^" -> {
                val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out("  eor  #$value")
                asmgen.storeAIntoZpPointerVar(sourceName, false)
            }
            "==" -> {
                val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out("""
                    cmp  #$value
                    beq  +
                    lda  #0
                    beq  ++
+                   lda  #1
+""")
                asmgen.storeAIntoZpPointerVar(sourceName, false)
            }
            "!=" -> {
                val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out("""
                    cmp  #$value
                    bne  +
                    lda  #0
                    beq  ++
+                   lda  #1
+""")
                asmgen.storeAIntoZpPointerVar(sourceName, false)
            }
            // pretty uncommon, who's going to assign a comparison boolean expression to a pointer?:
            "<", "<=", ">", ">=" -> TODO("byte-litval-to-pointer comparisons")
            else -> throw AssemblyError("invalid operator for in-place modification $operator")
        }
    }

    private fun inplacemodificationByteVariableWithValue(name: String, dt: DataType, operator: String, value: PtExpression) {
        if(!value.isSimple()) {
            // attempt short-circuit (McCarthy) evaluation
            when (operator) {
                "and" -> {
                    // short-circuit  LEFT and RIGHT  -->  if LEFT then RIGHT else LEFT   (== if !LEFT then LEFT else RIGHT)
                    val shortcutLabel = asmgen.makeLabel("shortcut")
                    asmgen.out("  lda  $name |  beq  $shortcutLabel")
                    asmgen.assignExpressionToRegister(value, RegisterOrPair.A, dt in SignedDatatypes)
                    asmgen.out("""
                         and  $name
                         sta  $name
$shortcutLabel:""")
                    return
                }
                "or" -> {
                    // short-circuit  LEFT or RIGHT  -->  if LEFT then LEFT else RIGHT
                    val shortcutLabel = asmgen.makeLabel("shortcut")
                    asmgen.out("  lda  $name |  bne  $shortcutLabel")
                    asmgen.assignExpressionToRegister(value, RegisterOrPair.A, dt in SignedDatatypes)
                    asmgen.out("""
                         ora  $name
                         sta  $name
$shortcutLabel:""")
                    return
                }
            }
        }

        if(asmgen.isTargetCpu(CpuType.CPU65c02)) {
            if(operator=="&" && value is PtPrefix && value.operator=="~") {
                // M &= ~A  -->  use TRB 65c02 instruction for that
                asmgen.assignExpressionToRegister(value.value, RegisterOrPair.A, dt in SignedDatatypes)
                asmgen.out("  trb  $name")
                return
            }
            else if(operator=="|") {
                // M |= A --> use TSB 65c02 instruction for that
                asmgen.assignExpressionToRegister(value, RegisterOrPair.A, dt in SignedDatatypes)
                asmgen.out("  tsb  $name")
                return
            }
        }

        // normal evaluation
        asmgen.assignExpressionToRegister(value, RegisterOrPair.A, dt in SignedDatatypes)
        inplacemodificationRegisterAwithVariableWithSwappedOperands(operator, name, dt in SignedDatatypes)
        asmgen.out("  sta  $name")
    }

    private fun inplacemodificationByteVariableWithVariable(name: String, dt: DataType, operator: String, otherName: String) {
        // note: no logical and/or shortcut here, not worth it due to simple right operand

        if(asmgen.isTargetCpu(CpuType.CPU65c02)) {
            if(operator=="|") {
                // M |= A  -->  use TSB 65c02 instruction for that
                asmgen.out("  lda  $otherName |  tsb  $name")
                return
            }
        }

        asmgen.out("  lda  $name")
        inplacemodificationRegisterAwithVariable(operator, otherName, dt in SignedDatatypes)
        asmgen.out("  sta  $name")
    }

    private fun inplacemodificationRegisterAwithVariable(operator: String, variable: String, signed: Boolean) {
        if(operator in "+-" && variable in arrayOf("#1", "#$1", "#$01", "#%1", "#%00000001")) {
            if(asmgen.isTargetCpu(CpuType.CPU65c02)) {
                if(operator=="+")
                    asmgen.out("  inc  a")
                else
                    asmgen.out("  dec  a")
            } else {
                if(operator=="+")
                    asmgen.out("  clc |  adc  #1")
                else
                    asmgen.out("  sec |  sbc  #1")
            }
            return
        }

        // A = A <operator> variable
        when (operator) {
            "+" -> asmgen.out("  clc |  adc  $variable")
            "-" -> asmgen.out("  sec |  sbc  $variable")
            "*" -> asmgen.out("  ldy  $variable  |  jsr  math.multiply_bytes")
            "/" -> {
                if(signed)
                    asmgen.out("  ldy  $variable  |  jsr  math.divmod_b_asm |  tya")
                else
                    asmgen.out("  ldy  $variable  |  jsr  math.divmod_ub_asm |  tya")
            }
            "%" -> {
                if(signed)
                    throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
                asmgen.out("  ldy  $variable  |  jsr  math.divmod_ub_asm")
            }
            "<<" -> {
                asmgen.out("""
                    ldy  $variable
                    beq  +
-                   asl  a
                    dey
                    bne  -
+""")
            }
            ">>" -> {
                if(!signed) {
                    asmgen.out("""
                        ldy  $variable
                        beq  +
-                       lsr  a
                        dey
                        bne  -
+""")
                } else {
                    asmgen.out("""
                        ldy  $variable
                        beq  +
                        sta  P8ZP_SCRATCH_B1
-                       asl  a
                        ror  P8ZP_SCRATCH_B1
                        lda  P8ZP_SCRATCH_B1
                        dey
                        bne  -
+""")
                }
            }
            "&", "and" -> asmgen.out("  and  $variable")
            "|", "or" -> asmgen.out("  ora  $variable")
            "^","xor" -> asmgen.out("  eor  $variable")
            "==" -> {
                asmgen.out("""
                    cmp  $variable
                    beq  +
                    lda  #0
                    beq  ++
+                   lda  #1
+""")
            }
            "!=" -> {
                asmgen.out("""
                    cmp  $variable
                    beq  +
                    lda  #1
                    bne  ++
+                   lda  #0
+""")
            }
            "<" -> {
                if(!signed) {
                    asmgen.out("""
                        cmp  $variable
                        bcc  +
                        lda  #0
                        beq  ++
+                       lda  #1
+""")
                }
                else {
                    // see http://www.6502.org/tutorials/compare_beyond.html
                    asmgen.out("""
                        sec
                        sbc  $variable
                        bvc  +
                        eor  #$80
+                       bmi  +
                        lda  #0
                        beq  ++
+                       lda  #1
+""")
                }
            }
            "<=" -> {
                if(!signed) {
                    asmgen.out("""
                        cmp  $variable
                        bcc  +
                        beq  +
                        lda  #0
                        beq  ++
+                       lda  #1
+""")
                } else {
                    // see http://www.6502.org/tutorials/compare_beyond.html
                    asmgen.out("""
                        clc
                        sbc  $variable
                        bvc  +
                        eor  #$80
+                       bmi  +
                        lda  #0
                        beq  ++
+                       lda  #1
+""")
                }
            }
            ">" -> {
                if(!signed) {
                    asmgen.out("""
                        tay
                        lda  #0
                        cpy  $variable
                        beq  +
                        rol  a
+""")
                } else {
                    // see http://www.6502.org/tutorials/compare_beyond.html
                    asmgen.out("""
                        clc
                        sbc  $variable
                        bvc  +
                        eor  #$80
+                		bpl  +
                        lda  #0
                        beq  ++
+                       lda  #1
+""")
                }
            }
            ">=" -> {
                if(!signed) {
                    asmgen.out("""
                        tay
                        lda  #0
                        cpy  $variable
                        rol  a""")
                } else {
                    // see http://www.6502.org/tutorials/compare_beyond.html
                    asmgen.out("""
                        sec
                        sbc  $variable
                        bvc  +
                        eor  #$80
+                		bpl  +
                        lda  #0
                        beq  ++
+                       lda  #1
+""")
                }
            }
            else -> throw AssemblyError("invalid operator for in-place modification $operator")
        }
    }

    private fun inplacemodificationRegisterAwithVariableWithSwappedOperands(operator: String, variable: String, signed: Boolean) {
        // A = variable <operator> A

        if(operator in AssociativeOperators)
            return inplacemodificationRegisterAwithVariable(operator, variable, signed)     // just reuse existing code for associative operators

        // now implement the non-assiciative operators...
        when (operator) {
            "-" -> {
                // A = variable - A
                val tmpVar = if(variable!="P8ZP_SCRATCH_B1") "P8ZP_SCRATCH_B1" else "P8ZP_SCRATCH_REG"
                asmgen.out("  sta  $tmpVar |  lda  $variable |  sec |  sbc  $tmpVar")
            }
            "/" -> {
                if(signed)
                    asmgen.out("  tay |  lda  $variable  |  jsr  math.divmod_b_asm |  tya")
                else
                    asmgen.out("  tay |  lda  $variable  |  jsr  math.divmod_ub_asm |  tya")
            }
            "%" -> {
                if(signed)
                    throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
                asmgen.out("  tay |  lda  $variable  |  jsr  math.divmod_ub_asm")
            }
            "<<" -> {
                asmgen.out("""
                    tay
                    lda  $variable
                    cpy  #0
                    beq  +
-                   asl  a
                    dey
                    bne  -
+""")
            }
            ">>" -> {
                if(!signed) {
                    asmgen.out("""
                        tay
                        lda  $variable
                        cpy  #0
                        beq  +
-                       lsr  a
                        dey
                        bne  -
+""")
                } else {
                    asmgen.out("""
                        tay
                        lda  $variable
                        cpy  #0
                        beq  +
                        sta  P8ZP_SCRATCH_B1
-                       asl  a
                        ror  P8ZP_SCRATCH_B1
                        lda  P8ZP_SCRATCH_B1
                        dey
                        bne  -
+""")
                }
            }
            "<" -> {
                // variable<A --> A>variable?
                if(!signed) {
                    asmgen.out("""
                        tay
                        lda  #0
                        cpy  $variable
                        beq  +
                        rol  a
+""")
                }
                else {
                    // see http://www.6502.org/tutorials/compare_beyond.html
                    asmgen.out("""
                        clc
                        sbc  $variable
                        bvc  +
                        eor  #$80
+                		bpl  +
                        lda  #0
                        beq  ++
+                       lda  #1
+""")
                }
            }
            "<=" -> {
                // variable<=A --> A>=variable?
                if(!signed) {
                    asmgen.out("""
                        tay
                        lda  #0
                        cpy  $variable
                        rol  a""")
                } else {
                    // see http://www.6502.org/tutorials/compare_beyond.html
                    asmgen.out("""
                        sec
                        sbc  $variable
                        bvc  +
                        eor  #$80
+                		bpl  +
                        lda  #0
                        beq  ++
+                       lda  #1
+""")
                }
            }
            ">" -> {
                // variable>A --> A<variable?
                if(!signed) {
                    asmgen.out("""
                        cmp  $variable
                        bcc  +
                        lda  #0
                        beq  ++
+                       lda  #1
+""")
                } else {
                    // see http://www.6502.org/tutorials/compare_beyond.html
                    asmgen.out("""
                        sec
                        sbc  $variable
                        bvc  +
                        eor  #$80
+                       bmi  +
                        lda  #0
                        beq  ++
+                       lda  #1
+""")
                }
            }
            ">=" -> {
                // variable>=A  --> A<=variable?
                if(!signed) {
                    asmgen.out("""
                        cmp  $variable
                        bcc  +
                        beq  +
                        lda  #0
                        beq  ++
+                       lda  #1
+""")
                } else {
                    // see http://www.6502.org/tutorials/compare_beyond.html
                    asmgen.out("""
                        clc
                        sbc  $variable
                        bvc  +
                        eor  #$80
+                       bmi  +
                        lda  #0
                        beq  ++
+                       lda  #1
+""")
                }
            }
            else -> throw AssemblyError("invalid operator for in-place modification $operator")
        }
    }

    private fun inplacemodificationByteVariableWithLiteralval(name: String, dt: DataType, operator: String, value: Int) {
        // note: this contains special optimized cases because we know the exact value. Don't replace this with another routine.
        // note: no logical and/or shortcut here, not worth it due to simple right operand
        when (operator) {
            "+" -> {
                if(value==1) asmgen.out("  inc  $name")
                else asmgen.out(" lda  $name |  clc |  adc  #$value |  sta  $name")
            }
            "-" -> {
                if(value==1) asmgen.out("  dec  $name")
                else asmgen.out(" lda  $name |  sec |  sbc  #$value |  sta  $name")
            }
            "*" -> {
                if(value in asmgen.optimizedByteMultiplications)
                    asmgen.out("  lda  $name |  jsr  math.mul_byte_$value |  sta  $name")
                else
                    asmgen.out("  lda  $name |  ldy  #$value |  jsr  math.multiply_bytes |  sta  $name")
            }
            "/" -> {
                // replacing division by shifting is done in an optimizer step.
                if (dt == DataType.UBYTE)
                    asmgen.out("  lda  $name |  ldy  #$value |  jsr  math.divmod_ub_asm |  sty  $name")
                else
                    asmgen.out("  lda  $name |  ldy  #$value |  jsr  math.divmod_b_asm |  sty  $name")
            }
            "%" -> {
                if(dt==DataType.BYTE)
                    throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
                asmgen.out("""
                    lda  $name
                    ldy  #$value
                    jsr  math.divmod_ub_asm
                    sta  $name""")
            }
            "<<" -> {
                if(value>=8) {
                    if(asmgen.isTargetCpu(CpuType.CPU65c02))
                        asmgen.out("  stz  $name")
                    else
                        asmgen.out("  lda  #0 |  sta  $name")
                }
                else repeat(value) { asmgen.out("  asl  $name") }
            }
            ">>" -> {
                if(value>0) {
                    if (dt == DataType.UBYTE) {
                        if(value>=8) {
                            if(asmgen.isTargetCpu(CpuType.CPU65c02))
                                asmgen.out("  stz  $name")
                            else
                                asmgen.out("  lda  #0 |  sta  $name")
                        }
                        else repeat(value) { asmgen.out("  lsr  $name") }
                    } else {
                        when {
                            value>=8 -> asmgen.out("""
                                lda  $name
                                bmi  +
                                lda  #0
                                beq  ++
+                               lda  #-1
+                               sta  $name""")
                            value>3 -> asmgen.out("""
                                lda  $name
                                ldy  #$value
                                jsr  math.lsr_byte_A
                                sta  $name""")
                            else -> repeat(value) { asmgen.out("  lda  $name | asl  a |  ror  $name") }
                        }
                    }
                }
            }
            "&", "and" -> immediateAndInplace(name, value)
            "|", "or" -> immediateOrInplace(name, value)
            "^", "xor" -> asmgen.out(" lda  $name |  eor  #$value |  sta  $name")
            "==" -> {
                asmgen.out("""
                    lda  $name
                    cmp  #$value
                    beq  +
                    lda  #0
                    beq  ++
+                   lda  #1
+                   sta  $name""")
            }
            "!=" -> {
                asmgen.out("""
                    lda  $name
                    cmp  #$value
                    beq  +
                    lda  #1
                    bne  ++
+                   lda  #0
+                   sta  $name""")
            }
            "<" -> {
                if(dt==DataType.UBYTE) {
                    asmgen.out("""
                        lda  #0
                        ldy  $name
                        cpy  #$value
                        rol  a
                        eor  #1
                        sta  $name""")
                }
                else {
                    // see http://www.6502.org/tutorials/compare_beyond.html
                    asmgen.out("""
                        lda  $name
                        sec
                        sbc  #$value
                        bvc  +
                        eor  #$80
+                       bmi  +
                        lda  #0
                        beq  ++
+                       lda  #1
+                       sta  $name""")
                }
            }
            "<=" -> {
                if(dt==DataType.UBYTE) {
                    asmgen.out("""
                        lda  #0
                        ldy  #$value
                        cpy  $name
                        rol  a
                        sta  $name""")
                } else {
                    // see http://www.6502.org/tutorials/compare_beyond.html
                    asmgen.out("""
                        lda  $name
                        clc
                        sbc  #$value
                        bvc  +
                        eor  #$80
+                       bmi  +
                        lda  #0
                        beq  ++
+                       lda  #1
+                       sta  $name""")
                }
            }
            ">" -> {
                if(dt==DataType.UBYTE) {
                    asmgen.out("""
                        lda  #0
                        ldy  $name
                        cpy  #$value
                        beq  +
                        rol  a
+                       sta  $name""")
                } else {
                    // see http://www.6502.org/tutorials/compare_beyond.html
                    asmgen.out("""
                        lda  $name
                        clc
                        sbc  #$value
                        bvc  +
                        eor  #$80
+                		bpl  +
                        lda  #0
                        beq  ++
+                       lda  #1
+                       sta  $name""")
                }
            }
            ">=" -> {
                if(dt==DataType.UBYTE) {
                    asmgen.out("""
                        lda  #0
                        ldy  $name
                        cpy  #$value
                        rol  a
                        sta  $name""")
                } else {
                    // see http://www.6502.org/tutorials/compare_beyond.html
                    asmgen.out("""
                        lda  $name
                        sec
                        sbc  #$value
                        bvc  +
                        eor  #$80
+                		bpl  +
                        lda  #0
                        beq  ++
+                       lda  #1
+                       sta  $name""")
                }
            }
            else -> throw AssemblyError("invalid operator for in-place modification $operator")
        }
    }

    private fun immediateAndInplace(name: String, value: Int) {
        if(asmgen.isTargetCpu(CpuType.CPU65c02)) {
            asmgen.out(" lda  #${value xor 255} |  trb  $name")     // reset bit
        } else  {
            asmgen.out(" lda  $name |  and  #$value |  sta  $name")
        }
    }

    private fun immediateOrInplace(name: String, value: Int) {
        if(asmgen.isTargetCpu(CpuType.CPU65c02)) {
            asmgen.out(" lda  #$value |  tsb  $name")       // set bit
        } else  {
            asmgen.out(" lda  $name |  ora  #$value |  sta  $name")
        }
    }

    private fun inplacemodificationWordWithMemread(name: String, dt: DataType, operator: String, memread: PtMemoryByte) {
        when (operator) {
            "+" -> {
                asmgen.translateDirectMemReadExpressionToRegA(memread)
                asmgen.out("""
                    clc
                    adc  $name
                    sta  $name
                    bcc  +
                    inc  $name+1
+""")
            }
            "-" -> {
                // name -= @(memory)
                asmgen.translateDirectMemReadExpressionToRegA(memread)
                val tmpByte = if(name!="P8ZP_SCRATCH_B1") "P8ZP_SCRATCH_B1" else "P8ZP_SCRATCH_REG"
                asmgen.out("""
                    sta  $tmpByte
                    lda  $name
                    sec
                    sbc  $tmpByte
                    sta  $name
                    bcs  +
                    dec  $name+1
+""")
            }
            "|" -> {
                asmgen.translateDirectMemReadExpressionToRegA(memread)
                asmgen.out("  ora  $name  |  sta  $name")
            }
            "&" -> {
                asmgen.translateDirectMemReadExpressionToRegA(memread)
                asmgen.out("  and  $name  |  sta  $name")
                if(dt in WordDatatypes) {
                    if(asmgen.isTargetCpu(CpuType.CPU65c02))
                        asmgen.out("  stz  $name+1")
                    else
                        asmgen.out("  lda  #0 |  sta  $name+1")
                }
            }
            "^" -> {
                asmgen.translateDirectMemReadExpressionToRegA(memread)
                asmgen.out("  eor  $name  |  sta  $name")
            }
            else -> {
                inplacemodificationWordWithValue(name, dt, operator, memread, memread.definingBlock())
            }
        }
    }

        

    private fun inplacemodificationWordWithLiteralval(name: String, dt: DataType, operator: String, value: Int, block: PtBlock?) {
        // note: this contains special optimized cases because we know the exact value. Don't replace this with another routine.
        inplacemodificationSomeWordWithLiteralval(name, "$name+1", dt, operator, value, block)
    }
    
    private fun inplacemodificationSomeWordWithLiteralval(lsb: String, msb: String, dt: DataType, operator: String, value: Int, block: PtBlock?) {
        when (operator) {
            "+" -> {
                when {
                    value==0 -> {}
                    value==1 -> {
                        asmgen.out("""
                            inc  $lsb
                            bne  +
                            inc  $msb
+""")
                    }
                    value in 1..0xff -> asmgen.out("""
                        lda  $lsb
                        clc
                        adc  #$value
                        sta  $lsb
                        bcc  +
                        inc  $msb
+""")
                    value==0x0100 -> asmgen.out(" inc  $msb")
                    value==0x0200 -> asmgen.out(" inc  $msb |  inc  $msb")
                    value==0x0300 -> asmgen.out(" inc  $msb |  inc  $msb |  inc  $msb")
                    value==0x0400 -> asmgen.out(" inc  $msb |  inc  $msb |  inc  $msb |  inc  $msb")
                    value and 255==0 -> asmgen.out(" lda  $msb |  clc |  adc  #>$value |  sta  $msb")
                    else -> asmgen.out("""
                        lda  $lsb
                        clc
                        adc  #<$value
                        sta  $lsb
                        lda  $msb
                        adc  #>$value
                        sta  $msb""")
                }
            }
            "-" -> {
                when {
                    value==0 -> {}
                    value==1 -> {
                        asmgen.out("""
                            lda  $lsb
                            bne  +
                            dec  $msb
+                           dec  $lsb""")
                    }
                    value in 1..0xff -> asmgen.out("""
                        lda  $lsb
                        sec
                        sbc  #$value
                        sta  $lsb
                        bcs  +
                        dec  $msb
+""")
                    value==0x0100 -> asmgen.out(" dec  $msb")
                    value==0x0200 -> asmgen.out(" dec  $msb |  dec  $msb")
                    value==0x0300 -> asmgen.out(" dec  $msb |  dec  $msb |  dec  $msb")
                    value==0x0400 -> asmgen.out(" dec  $msb |  dec  $msb |  dec  $msb |  dec  $msb")
                    value and 255==0 -> asmgen.out(" lda  $msb |  sec |  sbc  #>$value |  sta  $msb")
                    else -> asmgen.out("""
                        lda  $lsb
                        sec
                        sbc  #<$value
                        sta  $lsb
                        lda  $msb
                        sbc  #>$value
                        sta  $msb""")
                }
            }
            "*" -> {
                // the mul code works for both signed and unsigned
                if(value in asmgen.optimizedWordMultiplications) {
                    asmgen.out("  lda  $lsb |  ldy  $msb |  jsr  math.mul_word_$value |  sta  $lsb |  sty  $msb")
                } else {
                    if(block?.options?.veraFxMuls==true)
                        // cx16 verafx hardware mul
                        asmgen.out("""
                            lda  $lsb
                            ldy  $msb
                            sta  cx16.r0
                            sty  cx16.r0+1
                            lda  #<$value
                            ldy  #>$value
                            sta  cx16.r1
                            sty  cx16.r1+1
                            jsr  verafx.muls
                            sta  $lsb
                            sty  $msb""")
                    else
                        asmgen.out("""
                            lda  $lsb
                            sta  math.multiply_words.multiplier
                            lda  $msb
                            sta  math.multiply_words.multiplier+1
                            lda  #<$value
                            ldy  #>$value
                            jsr  math.multiply_words
                            sta  $lsb
                            sty  $msb""")
                }
            }
            "/" -> {
                // replacing division by shifting is done in an optimizer step.
                if(value==0) {
                    throw AssemblyError("division by zero")
                } else {
                    if(dt==DataType.WORD) {
                        asmgen.out("""
                            lda  $lsb
                            ldy  $msb
                            sta  P8ZP_SCRATCH_W1
                            sty  P8ZP_SCRATCH_W1+1
                            lda  #<$value
                            ldy  #>$value
                            jsr  math.divmod_w_asm
                            sta  $lsb
                            sty  $msb
                        """)
                    }
                    else {
                        asmgen.out("""
                            lda  $lsb
                            ldy  $msb
                            sta  P8ZP_SCRATCH_W1
                            sty  P8ZP_SCRATCH_W1+1
                            lda  #<$value
                            ldy  #>$value
                            jsr  math.divmod_uw_asm
                            sta  $lsb
                            sty  $msb
                        """)
                    }
                }
            }
            "%" -> {
                if(value==0)
                    throw AssemblyError("division by zero")
                if(dt==DataType.WORD)
                    throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
                asmgen.out("""
                    lda  $lsb
                    ldy  $msb
                    sta  P8ZP_SCRATCH_W1
                    sty  P8ZP_SCRATCH_W1+1
                    lda  #<$value
                    ldy  #>$value
                    jsr  math.divmod_uw_asm
                    lda  P8ZP_SCRATCH_W2
                    ldy  P8ZP_SCRATCH_W2+1
                    sta  $lsb
                    sty  $msb
                """)
            }
            "<<" -> {
                when {
                    value>=16 -> {
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("  stz  $lsb |  stz  $msb")
                        else
                            asmgen.out("  lda  #0 |  sta  $lsb |  sta  $msb")
                    }
                    value==8 -> {
                        asmgen.out("  lda  $lsb |  sta  $msb")
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("  stz  $lsb")
                        else
                            asmgen.out("  lda  #0 |  sta  $lsb")
                    }
                    value>3 -> asmgen.out("""
                        ldy  #$value
-                       asl  $lsb
                        rol  $msb
                        dey
                        bne  -
                    """)
                    else -> repeat(value) { asmgen.out(" asl  $lsb |  rol  $msb") }
                }
            }
            ">>" -> {
                if (value > 0) {
                    if(dt==DataType.UWORD) {
                        when {
                            value>=16 -> {
                                if(asmgen.isTargetCpu(CpuType.CPU65c02))
                                    asmgen.out("  stz  $lsb |  stz  $msb")
                                else
                                    asmgen.out("  lda  #0 |  sta  $lsb |  sta  $msb")
                            }
                            value==8 -> {
                                asmgen.out("  lda  $msb |  sta  $lsb")
                                if(asmgen.isTargetCpu(CpuType.CPU65c02))
                                    asmgen.out("  stz  $msb")
                                else
                                    asmgen.out("  lda  #0 |  sta  $msb")
                            }
                            value>2 -> asmgen.out("""
                                ldy  #$value
-                               lsr  $msb
                                ror  $lsb
                                dey
                                bne  -""")
                            else -> repeat(value) { asmgen.out("  lsr  $msb |  ror  $lsb")}
                        }
                    } else {
                        when {
                            value>=16 -> asmgen.out("""
                                lda  $msb
                                bmi  +
                                lda  #0
                                beq  ++
+                               lda  #-1
+                               sta  $lsb
                                sta  $msb""")
                            value==8 -> asmgen.out("""
                                 lda  $msb
                                 sta  $lsb
                                 bmi  +
                                 lda  #0
-                                sta  $msb
                                 beq  ++
+                                lda  #-1
                                 sta  $msb
+""")
                            value>2 -> asmgen.out("""
                                ldy  #$value
-                               lda  $msb
                                asl  a
                                ror  $msb
                                ror  $lsb
                                dey
                                bne  -""")
                            else -> repeat(value) { asmgen.out("  lda  $msb |  asl  a |  ror  $msb |  ror  $lsb") }
                        }
                    }
                }
            }
            "&" -> {
                when {
                    value == 0 -> {
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("  stz  $lsb |  stz  $msb")
                        else
                            asmgen.out("  lda  #0 |  sta  $lsb |  sta  $msb")
                    }
                    value == 0x00ff -> {
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("  stz  $msb")
                        else
                            asmgen.out("  lda  #0 |  sta  $msb")
                    }
                    value == 0xff00 -> {
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("  stz  $lsb")
                        else
                            asmgen.out("  lda  #0 |  sta  $lsb")
                    }
                    value and 255 == 0 -> {
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("  stz  $lsb")
                        else
                            asmgen.out("  lda  #0 |  sta  $lsb")
                        asmgen.out("  lda  $msb |  and  #>$value |  sta  $msb")
                    }
                    value < 0x0100 -> {
                        immediateAndInplace(lsb, value)
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("  stz  $msb")
                        else
                            asmgen.out("  lda  #0 |  sta  $msb")
                    }
                    else -> asmgen.out("  lda  $lsb |  and  #<$value |  sta  $lsb |  lda  $msb |  and  #>$value |  sta  $msb")
                }
            }
            "|" -> {
                when {
                    value == 0 -> {}
                    value and 255 == 0 -> asmgen.out("  lda  $msb |  ora  #>$value |  sta  $msb")
                    value < 0x0100 -> immediateOrInplace(lsb, value)
                    else -> asmgen.out("  lda  $lsb |  ora  #<$value |  sta  $lsb |  lda  $msb |  ora  #>$value |  sta  $msb")
                }
            }
            "^" -> {
                when {
                    value == 0 -> {}
                    value and 255 == 0 -> asmgen.out("  lda  $msb |  eor  #>$value |  sta  $msb")
                    value < 0x0100 -> asmgen.out("  lda  $lsb |  eor  #$value |  sta  $lsb")
                    else -> asmgen.out("  lda  $lsb |  eor  #<$value |  sta  $lsb |  lda  $msb |  eor  #>$value |  sta  $msb")
                }
            }
            "==" -> {
                asmgen.out("""
                    lda  $lsb
                    cmp  #<$value
                    bne  +
                    lda  $msb
                    cmp  #>$value
                    bne  +
                    lda  #1
                    bne  ++
+                   lda  #0
+                   sta  $lsb
                    lda  #0
                    sta  $msb""")
            }
            "!=" -> {
                asmgen.out("""
                    lda  $lsb
                    cmp  #<$value
                    bne  +
                    lda  $msb
                    cmp  #>$value
                    bne  +
                    lda  #0
                    beq  ++
+                   lda  #1
+                   sta  $lsb
                    lda  #0
                    sta  $msb""")
            }
            "<" -> {
                if(dt==DataType.UWORD) {
                    asmgen.out("""
                        lda  $msb
                        cmp  #>$value
                        bcc  ++
                        bne  +
                        lda  $lsb
                        cmp  #<$value
                        bcc  ++
+                       lda  #0     ; false
                        sta  $lsb
                        sta  $msb
                        beq  ++
+                       lda  #1     ; true
                        sta  $lsb
                        lda  #0
                        sta  $msb
+""")
                }
                else {
                    // signed
                    asmgen.out("""
                        lda  $lsb
                        cmp  #<$value
                        lda  $msb
                        sbc  #>$value
                        bvc  +
                        eor  #$80
+                       bmi  +
                        lda  #0
                        sta  $lsb
                        sta  $msb
                        beq  ++
+                       lda  #1     ; true
                        sta  $lsb
                        lda  #0
                        sta  $msb
+""")
                }
            }
            "<=" -> {
                if(dt==DataType.UWORD) {
                    asmgen.out("""
                        lda  $msb
                        cmp  #>$value
                        beq  +
                        bcc  ++
-                       lda  #0             ; false
                        sta  $lsb
                        sta  $msb
                        beq  +++
+                       lda  $lsb          ; next
                        cmp  #<$value
                        bcc  +
                        bne  -
+                       lda  #1             ; true
                        sta  $lsb
                        lda  #0
                        sta  $msb
+""")
                }
                else {
                    // signed
                    asmgen.out("""
                        lda  #<$value
                        cmp  $lsb
                        lda  #>$value
                        sbc  $msb
                        bvc  +
                        eor  #$80
+                       bpl  +
                        lda  #0
                        sta  $lsb
                        sta  $msb
                        beq  ++
+                       lda  #1
                        sta  $lsb
                        lda  #0
                        sta  $msb
+""")
                }
            }
            ">" -> {
                // word > value  -->  value < word
                if(dt==DataType.UWORD) {
                    asmgen.out("""
                        lda  #>$value
                        cmp  $msb
                        bcc  ++
                        bne  +
                        lda  #<$value
                        cmp  $lsb
                        bcc  ++
+                       lda  #0         ; false
                        sta  $lsb
                        sta  $msb
                        beq  ++
+                       lda  #1         ; true
                        sta  $lsb
                        lda  #0
                        sta  $msb
+""")
                }
                else {
                    // signed
                    asmgen.out("""
                        lda  #<$value
                        cmp  $lsb
                        lda  #>$value
                        sbc  $msb
                        bvc  +
                        eor  #$80
+                       bmi  +
                        lda  #0
                        sta  $lsb
                        sta  $msb
                        beq  ++
+                       lda  #1         ; true
                        sta  $lsb
                        lda  #0
                        sta  $msb
+""")
                }
            }
            ">=" -> {
                // word >= value  -->  value <= word
                if(dt==DataType.UWORD) {
                    asmgen.out("""
                        lda  #>$value
                        cmp  $msb
                        beq  +
                        bcc  ++
-                       lda  #0             ; false
                        sta  $lsb
                        sta  $msb
                        beq  +++
+                       lda  #<$value        ; next
                        cmp  $lsb
                        bcc  +
                        bne  -
+                       lda  #1             ; true
                        sta  $lsb
                        lda  #0
                        sta  $msb
+""")
                }
                else {
                    // signed
                    asmgen.out("""
                        lda  $lsb
                        cmp  #<$value
                        lda  $msb
                        sbc  #>$value
                        bvc  +
                        eor  #$80
+                       bpl  +
                        lda  #0
                        sta  $lsb
                        sta  $msb
                        beq  ++
+                       lda  #1
                        sta  $lsb
                        lda  #0
                        sta  $msb
+""")
                }
            }
            else -> throw AssemblyError("invalid operator for in-place modification $operator")
        }
    }

    private fun inplacemodificationWordWithVariable(name: String, dt: DataType, operator: String, otherName: String, valueDt: DataType, block: PtBlock?) {
        when (valueDt) {
            in ByteDatatypes -> {
                // the other variable is a BYTE type so optimize for that
                when (operator) {
                    "+" -> {
                        if(valueDt==DataType.UBYTE)
                            asmgen.out("""
                                lda  $name
                                clc
                                adc  $otherName
                                sta  $name
                                bcc  +
                                inc  $name+1
+""")
                        else
                            asmgen.out("""
                                ldy  #0
                                lda  $otherName
                                bpl  +
                                dey     ; sign extend
+                               clc
                                adc  $name
                                sta  $name
                                tya
                                adc  $name+1
                                sta  $name+1""")
                    }
                    "-" -> {
                        if(valueDt==DataType.UBYTE)
                            asmgen.out("""
                                lda  $name
                                sec
                                sbc  $otherName
                                sta  $name
                                bcs  +
                                dec  $name+1
+""")
                        else
                            asmgen.out("""
                                ldy  #255
                                lda  $otherName
                                bpl  +
                                iny     ; sign extend
+                               eor  #255
                                sec
                                adc  $name
                                sta  $name
                                tya
                                adc  $name+1
                                sta  $name+1""")
                    }
                    "*" -> {
                        if(block?.options?.veraFxMuls==true) {
                            // cx16 verafx hardware muls
                            if(valueDt==DataType.UBYTE) {
                                asmgen.out("  lda  $otherName |  sta  cx16.r1")
                                if(asmgen.isTargetCpu(CpuType.CPU65c02))
                                    asmgen.out("  stz  cx16.r1+1")
                                else
                                    asmgen.out("  lda  #0 |  sta  cx16.r1+1")
                            } else {
                                asmgen.out("  lda  $otherName")
                                asmgen.signExtendAYlsb(valueDt)
                                asmgen.out("  sta  cx16.r1 |  sty  cx16.r1+1")
                            }
                            asmgen.out("""
                                lda  $name
                                ldy  $name+1
                                sta  cx16.r0
                                sty  cx16.r0+1
                                jsr  verafx.muls
                                sta  $name
                                sty  $name+1""")
                        } else {
                            if(valueDt==DataType.UBYTE) {
                                asmgen.out("  lda  $otherName |  sta  math.multiply_words.multiplier")
                                if(asmgen.isTargetCpu(CpuType.CPU65c02))
                                    asmgen.out("  stz  math.multiply_words.multiplier+1")
                                else
                                    asmgen.out("  lda  #0 |  sta  math.multiply_words.multiplier+1")
                            } else {
                                asmgen.out("  lda  $otherName")
                                asmgen.signExtendAYlsb(valueDt)
                                asmgen.out("  sta  math.multiply_words.multiplier |  sty  math.multiply_words.multiplier+1")
                            }
                            asmgen.out("""
                                    lda  $name
                                    ldy  $name+1
                                    jsr  math.multiply_words
                                    sta  $name
                                    sty  $name+1""")
                        }
                    }
                    "/" -> {
                        if(dt==DataType.UWORD) {
                            asmgen.out("""
                                lda  $name
                                ldy  $name+1
                                sta  P8ZP_SCRATCH_W1
                                sty  P8ZP_SCRATCH_W1+1
                                lda  $otherName
                                ldy  #0
                                jsr  math.divmod_uw_asm
                                sta  $name
                                sty  $name+1
                            """)
                        } else {
                            asmgen.out("""
                                lda  $name
                                ldy  $name+1
                                sta  P8ZP_SCRATCH_W1
                                sty  P8ZP_SCRATCH_W1+1
                                lda  $otherName
                                ldy  #0
                                jsr  math.divmod_w_asm
                                sta  $name
                                sty  $name+1
                            """)
                        }
                    }
                    "%" -> {
                        if(valueDt!=DataType.UBYTE || dt!=DataType.UWORD)
                            throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
                        asmgen.out("""
                            lda  $name
                            ldy  $name+1
                            sta  P8ZP_SCRATCH_W1
                            sty  P8ZP_SCRATCH_W1+1
                            lda  $otherName
                            ldy  #0
                            jsr  math.divmod_uw_asm
                            lda  P8ZP_SCRATCH_W2
                            sta  $name
                            lda  P8ZP_SCRATCH_W2+1
                            sta  $name+1
                        """)
                    }
                    "<<" -> {
                        asmgen.out("""
                            ldy  $otherName
                            beq  +
-                           asl  $name
                            rol  $name+1
                            dey
                            bne  -
+""")
                    }
                    ">>" -> {
                        if(dt==DataType.UWORD) {
                            asmgen.out("""
                            ldy  $otherName
                            beq  +
-                           lsr  $name+1
                            ror  $name
                            dey
                            bne  -
+""")
                        } else {
                            asmgen.out("""
                            ldy  $otherName
                            beq  +
-                           lda  $name+1
                            asl  a
                            ror  $name+1
                            ror  $name
                            dey
                            bne  -
+""")
                        }
                    }
                    "&" -> {
                        asmgen.out("  lda  $otherName |  and  $name |  sta  $name")
                        if(dt in WordDatatypes) {
                            if(asmgen.isTargetCpu(CpuType.CPU65c02))
                                asmgen.out("  stz  $name+1")
                            else
                                asmgen.out("  lda  #0 |  sta  $name+1")
                        }
                    }
                    "|" -> asmgen.out("  lda  $otherName |  ora  $name |  sta  $name")
                    "^" -> asmgen.out("  lda  $otherName |  eor  $name |  sta  $name")
                    "==" -> {
                        asmgen.out("""
                            lda  $name
                            cmp  $otherName
                            bne  +
                            lda  $name+1
                            bne  +
                            lda  #1
                            bne  ++
+                           lda  #0
+                           sta  $name
                            lda  #0
                            sta  $name+1""")
                    }
                    "!=" -> {
                        asmgen.out("""
                            lda  $name
                            cmp  $otherName
                            bne  +
                            lda  $name+1
                            bne  +
                            lda  #0
                            beq  ++
+                           lda  #1
+                           sta  $name
                            lda  #0
                            sta  $name+1""")
                    }
                    // pretty uncommon, who's going to assign a comparison boolean expression to a word var?:
                    "<", "<=", ">", ">=" -> TODO("word-bytevar-to-var comparisons")
                    else -> throw AssemblyError("invalid operator for in-place modification $operator")
                }
            }
            in WordDatatypes -> {
                // the value is a proper 16-bit word, so use both bytes of it.
                when (operator) {
                    "+" -> asmgen.out("  lda  $name |  clc |  adc  $otherName |  sta  $name |  lda  $name+1 |  adc  $otherName+1 |  sta  $name+1")
                    "-" -> asmgen.out("  lda  $name |  sec |  sbc  $otherName |  sta  $name |  lda  $name+1 |  sbc  $otherName+1 |  sta  $name+1")
                    "*" -> {
                        if(block?.options?.veraFxMuls==true)
                            // cx16 verafx hardware muls
                            asmgen.out("""
                                lda  $name
                                ldy  $name+1
                                sta  cx16.r0
                                sty  cx16.r0+1
                                lda  $otherName
                                ldy  $otherName+1
                                sta  cx16.r1
                                sty  cx16.r1+1
                                jsr  verafx.muls
                                sta  $name
                                sty  $name+1""")
                        else
                            asmgen.out("""
                                lda  $otherName
                                ldy  $otherName+1
                                sta  math.multiply_words.multiplier
                                sty  math.multiply_words.multiplier+1
                                lda  $name
                                ldy  $name+1
                                jsr  math.multiply_words
                                sta  $name
                                sty  $name+1""")
                    }
                    "/" -> {
                        if(dt==DataType.WORD) {
                            asmgen.out("""
                                lda  $name
                                ldy  $name+1
                                sta  P8ZP_SCRATCH_W1
                                sty  P8ZP_SCRATCH_W1+1
                                lda  $otherName
                                ldy  $otherName+1
                                jsr  math.divmod_w_asm
                                sta  $name
                                sty  $name+1""")
                        }
                        else {
                            asmgen.out("""
                                lda  $name
                                ldy  $name+1
                                sta  P8ZP_SCRATCH_W1
                                sty  P8ZP_SCRATCH_W1+1
                                lda  $otherName
                                ldy  $otherName+1
                                jsr  math.divmod_uw_asm
                                sta  $name
                                sty  $name+1""")
                        }
                    }
                    "%" -> {
                        if(dt==DataType.WORD)
                            throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
                        asmgen.out("""
                            lda  $name
                            ldy  $name+1
                            sta  P8ZP_SCRATCH_W1
                            sty  P8ZP_SCRATCH_W1+1
                            lda  $otherName
                            ldy  $otherName+1
                            jsr  math.divmod_uw_asm
                            lda  P8ZP_SCRATCH_W2
                            sta  $name
                            lda  P8ZP_SCRATCH_W2+1
                            sta  $name+1
                        """)
                    }
                    "<<", ">>" -> {
                        throw AssemblyError("shift by a word variable not supported, max is a byte")
                    }
                    "&" -> asmgen.out(" lda  $name |  and  $otherName |  sta  $name |  lda  $name+1 |  and  $otherName+1 |  sta  $name+1")
                    "|" -> asmgen.out(" lda  $name |  ora  $otherName |  sta  $name |  lda  $name+1 |  ora  $otherName+1 |  sta  $name+1")
                    "^" -> asmgen.out(" lda  $name |  eor  $otherName |  sta  $name |  lda  $name+1 |  eor  $otherName+1 |  sta  $name+1")
                    "==" -> {
                        asmgen.out("""
                            lda  $name
                            cmp  $otherName
                            bne  +
                            lda  $name+1
                            cmp  $otherName+1
                            bne  +
                            lda  #1
                            bne  ++
+                           lda  #0
+                           sta  $name
                            lda  #0
                            sta  $name+1""")
                    }
                    "!=" -> {
                        asmgen.out("""
                            lda  $name
                            cmp  $otherName
                            bne  +
                            lda  $name+1
                            cmp  $otherName+1
                            bne  +
                            lda  #0
                            beq  ++
+                           lda  #1
+                           sta  $name
                            lda  #0
                            sta  $name+1""")
                    }
                    "<" -> {
                        val compareRoutine = if(dt==DataType.UWORD) "reg_less_uw" else "reg_less_w"
                        asmgen.out("""
                            lda  $otherName
                            ldy  $otherName+1
                            sta  P8ZP_SCRATCH_W2
                            sty  P8ZP_SCRATCH_W2+1
                            lda  $name
                            ldy  $name+1
                            jsr  prog8_lib.$compareRoutine
                            sta  $name
                            lda  #0
                            sta  $name+1""")
                    }
                    ">" -> {
                        // a > b  -->  b < a
                        val compareRoutine = if(dt==DataType.UWORD) "reg_less_uw" else "reg_less_w"
                        asmgen.out("""
                            lda  $name
                            ldy  $name+1
                            sta  P8ZP_SCRATCH_W2
                            sty  P8ZP_SCRATCH_W2+1
                            lda  $otherName
                            ldy  $otherName+1
                            jsr  prog8_lib.$compareRoutine
                            sta  $name
                            lda  #0
                            sta  $name+1""")
                    }
                    "<=" -> {
                        if(dt==DataType.UWORD) {
                            asmgen.out("""
                            lda  $otherName
                            ldy  $otherName+1
                            sta  P8ZP_SCRATCH_W2
                            sty  P8ZP_SCRATCH_W2+1
                            lda  $name
                            ldy  $name+1
                            jsr  prog8_lib.reg_lesseq_uw
                            sta  $name
                            lda  #0
                            sta  $name+1""")
                        } else {
                            // note: reg_lesseq_w routine takes the arguments in reverse order
                            asmgen.out("""
                            lda  $name
                            ldy  $name+1
                            sta  P8ZP_SCRATCH_W2
                            sty  P8ZP_SCRATCH_W2+1
                            lda  $otherName
                            ldy  $otherName+1
                            jsr  prog8_lib.reg_lesseq_w
                            sta  $name
                            lda  #0
                            sta  $name+1""")
                        }
                    }
                    ">=" -> {
                        // a>=b --> b<=a
                        if(dt==DataType.UWORD) {
                            asmgen.out("""
                            lda  $name
                            ldy  $name+1
                            sta  P8ZP_SCRATCH_W2
                            sty  P8ZP_SCRATCH_W2+1
                            lda  $otherName
                            ldy  $otherName+1
                            jsr  prog8_lib.reg_lesseq_uw
                            sta  $name
                            lda  #0
                            sta  $name+1"""
                            )
                        } else {
                            // note: reg_lesseq_w routine takes the arguments in reverse order
                            asmgen.out("""
                            lda  $otherName
                            ldy  $otherName+1
                            sta  P8ZP_SCRATCH_W2
                            sty  P8ZP_SCRATCH_W2+1
                            lda  $name
                            ldy  $name+1
                            jsr  prog8_lib.reg_lesseq_w
                            sta  $name
                            lda  #0
                            sta  $name+1"""
                            )
                        }
                    }
                    else -> throw AssemblyError("invalid operator for in-place modification $operator")
                }
            }
            else -> {
                throw AssemblyError("can only use integer datatypes here")
            }
        }
    }

    private fun inplacemodificationWordWithValue(name: String, dt: DataType, operator: String, value: PtExpression, block: PtBlock?) {
        fun multiplyVarByWordInAY() {
            if(block?.options?.veraFxMuls==true)
                // cx16 verafx hardware muls
                asmgen.out("""
                    sta  cx16.r1
                    sty  cx16.r1+1
                    lda  $name
                    ldy  $name+1
                    sta  cx16.r0
                    sty  cx16.r0+1
                    jsr  verafx.muls
                    sta  $name
                    sty  $name+1
                """)
            else
                asmgen.out("""
                    sta  math.multiply_words.multiplier
                    sty  math.multiply_words.multiplier+1
                    lda  $name
                    ldy  $name+1
                    jsr  math.multiply_words
                    sta  $name
                    sty  $name+1
                """)
        }

        fun divideVarByWordInAY() {
            asmgen.out("""
                    tax
                    lda  $name
                    sta  P8ZP_SCRATCH_W1
                    lda  $name+1
                    sta  P8ZP_SCRATCH_W1+1
                    txa""")
            if (dt == DataType.WORD)
                asmgen.out("  jsr  math.divmod_w_asm")
            else
                asmgen.out("  jsr  math.divmod_uw_asm")
            asmgen.out("  sta  $name |  sty  $name+1")
        }

        fun remainderVarByWordInAY() {
            if(dt==DataType.WORD)
                throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
            asmgen.out("""
                tax
                lda  $name
                sta  P8ZP_SCRATCH_W1
                lda  $name+1
                sta  P8ZP_SCRATCH_W1+1
                txa
                jsr  math.divmod_uw_asm
                lda  P8ZP_SCRATCH_W2
                ldy  P8ZP_SCRATCH_W2+1
                sta  $name
                sty  $name+1
            """)
        }

        when (val valueDt = value.type) {
            in ByteDatatypes -> {
                // the other variable is a BYTE type so optimize for that
                when (operator) {
                    "+" -> {
                        // name += byteexpression
                        if(valueDt==DataType.UBYTE) {
                            asmgen.assignExpressionToRegister(value, RegisterOrPair.A, false)
                            asmgen.out("""
                                clc
                                adc  $name
                                sta  $name
                                bcc  +
                                inc  $name+1
+""")
                        } else {
                            asmgen.assignExpressionToRegister(value, RegisterOrPair.A, true)
                            asmgen.out("""
                                ldy  #0
                                cmp  #0
                                bpl  +
                                dey         ; sign extend
+                               clc
                                adc  $name
                                sta  $name
                                tya
                                adc  $name+1
                                sta  $name+1""")
                        }
                    }
                    "-" -> {
                        // name -= byteexpression
                        asmgen.assignExpressionToVariable(value, "P8ZP_SCRATCH_B1", valueDt)
                        if(valueDt==DataType.UBYTE)
                            asmgen.out("""
                                lda  $name
                                sec
                                sbc  P8ZP_SCRATCH_B1
                                sta  $name
                                bcs  +
                                dec  $name+1
+""")
                        else
                            asmgen.out("""
                                ldy  #255
                                lda  P8ZP_SCRATCH_B1
                                bpl  +
                                iny         ; sign extend
+                               eor  #255
                                sec
                                adc  $name
                                sta  $name
                                tya
                                adc  $name+1
                                sta  $name+1""")
                    }
                    "*" -> {
                        // value is (u) byte value, sign extend that and proceed with regular 16 bit operation
                        // TODO use an optimized word * byte multiplication routine?
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                        asmgen.signExtendAYlsb(valueDt)
                        multiplyVarByWordInAY()
                    }
                    "/" -> {
                        // value is (u) byte value, sign extend that and proceed with regular 16 bit operation
                        // TODO use an optimized word / byte divmod routine?
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                        asmgen.signExtendAYlsb(valueDt)
                        divideVarByWordInAY()
                    }
                    "%" -> {
                        // value is (u) byte value, sign extend that and proceed with regular 16 bit operation
                        // TODO use an optimized word / byte divmod routine?
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                        asmgen.signExtendAYlsb(valueDt)
                        remainderVarByWordInAY()
                    }
                    "<<" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.Y)
                        asmgen.out("""
                            beq  +
-                   	    asl  $name
                            rol  $name+1
                            dey
                            bne  -
+""")
                    }
                    ">>" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.Y)
                        if(dt==DataType.UWORD)
                            asmgen.out("""
                            beq  +
-                           lsr  $name+1
                            ror  $name
                            dey
                            bne  -
+""")
                        else
                            asmgen.out("""
                            beq  +
-                           lda  $name+1
                            asl  a
                            ror  $name+1
                            ror  $name
                            dey
                            bne  -
+""")
                    }
                    "&" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                        asmgen.out("  and  $name |  sta  $name")
                        if(dt in WordDatatypes) {
                            if(asmgen.isTargetCpu(CpuType.CPU65c02))
                                asmgen.out("  stz  $name+1")
                            else
                                asmgen.out("  lda  #0 |  sta  $name+1")
                        }
                    }
                    "|" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                        asmgen.out("  ora  $name |  sta  $name")
                    }
                    "^" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                        asmgen.out("  eor  $name |  sta  $name")
                    }
                    "==" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                        asmgen.out("""
                            cmp  $name
                            bne  +
                            lda  $name+1
                            bne  +
                            lda  #1
                            bne  ++
+                           lda  #0
+                           sta  $name
                            lda  #0
                            sta  $name+1""")
                    }
                    "!=" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                        asmgen.out("""
                            cmp  $name
                            bne  +
                            lda  $name+1
                            bne  +
                            lda  #0
                            beq  ++
+                           lda  #1
+                           sta  $name
                            lda  #0
                            sta  $name+1""")
                    }
                    // pretty uncommon, who's going to assign a comparison boolean expression to a word var?:
                    "<", "<=", ">", ">=" -> TODO("word-bytevalue-to-var comparisons")
                    else -> throw AssemblyError("invalid operator for in-place modification $operator")
                }
            }
            in WordDatatypes -> {
                // the value is a proper 16-bit word, so use both bytes of it.
                when (operator) {
                    "+" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.AY)
                        asmgen.out("  clc |  adc  $name |  sta  $name |  tya |  adc  $name+1 |  sta  $name+1")
                    }
                    "-" -> {
                        val tmpWord = if(name!="P8ZP_SCRATCH_W1") "P8ZP_SCRATCH_W1" else "P8ZP_SCRATCH_W2"
                        asmgen.assignExpressionToVariable(value, tmpWord, valueDt)
                        asmgen.out(" lda  $name |  sec |  sbc  $tmpWord |  sta  $name |  lda  $name+1 |  sbc  $tmpWord+1 |  sta  $name+1")
                    }
                    "*" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.AY)
                        multiplyVarByWordInAY()
                    }
                    "/" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.AY)
                        divideVarByWordInAY()
                    }
                    "%" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.AY)
                        remainderVarByWordInAY()
                    }
                    "<<", ">>" -> {
                        if(value is PtNumber && value.number<=255) {
                            when (dt) {
                                in WordDatatypes -> TODO("shift a word var by ${value.number}")
                                in ByteDatatypes -> TODO("shift a byte var by ${value.number}")
                                else -> throw AssemblyError("weird dt for shift")
                            }
                        } else {
                            throw AssemblyError("shift by a word value not supported, max is a byte")
                        }
                    }
                    "&" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.AY)
                        asmgen.out("  and  $name |  sta  $name |  tya |  and  $name+1 |  sta  $name+1")
                    }
                    "|" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.AY)
                        asmgen.out("  ora  $name |  sta  $name |  tya |  ora  $name+1 |  sta  $name+1")
                    }
                    "^" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.AY)
                        asmgen.out("  eor  $name |  sta  $name |  tya |  eor  $name+1 |  sta  $name+1")
                    }
                    "==" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.AY)
                        asmgen.out("""
                            cmp  $name
                            bne  +
                            cpy  $name+1
                            bne  +
                            lda  #1
                            bne  ++
+                           lda  #0
+                           sta  $name
                            lda  #0
                            sta  $name+1""")
                    }
                    "!=" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.AY)
                        asmgen.out("""
                            cmp  $name
                            bne  +
                            cpy  $name+1
                            bne  +
                            lda  #0
                            beq  ++
+                           lda  #1
+                           sta  $name
                            lda  #0
                            sta  $name+1""")
                    }
                    // pretty uncommon, who's going to assign a comparison boolean expression to a word var?:
                    "<", "<=", ">", ">=" -> TODO("word-value-to-var comparisons")
                    else -> throw AssemblyError("invalid operator for in-place modification $operator")
                }
            }
            else -> throw AssemblyError("can only use integer datatypes here")
        }
    }

    private fun inplacemodificationFloatWithValue(name: String, operator: String, value: PtExpression) {
        asmgen.assignExpressionToRegister(value, RegisterOrPair.FAC1)
        when (operator) {
            "+" -> {
                asmgen.out("""
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.FADD
                """)
            }
            "-" -> {
                asmgen.out("""
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.FSUB
                """)
            }
            "*" -> {
                asmgen.out("""
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.FMULT
                """)
            }
            "/" -> {
                asmgen.out("""
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.FDIV
                """)
            }
            // pretty uncommon, who's going to assign a comparison boolean expression to a float var:
            "==" -> TODO("float-value-to-var comparison ==")
            "!=" -> TODO("float-value-to-var comparison !=")
            "<", "<=", ">", ">=" -> TODO("float-value-to-var comparisons")
            else -> throw AssemblyError("invalid operator for in-place float modification $operator")
        }
        // store Fac1 back into memory
        asmgen.out("""
            ldx  #<$name
            ldy  #>$name
            jsr  floats.MOVMF
        """)
    }

    private fun inplacemodificationFloatWithVariable(name: String, operator: String, otherName: String) {
        when (operator) {
            "+" -> {
                asmgen.out("""
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.MOVFM
                    lda  #<$otherName
                    ldy  #>$otherName
                    jsr  floats.FADD
                """)
            }
            "-" -> {
                asmgen.out("""
                    lda  #<$otherName
                    ldy  #>$otherName
                    jsr  floats.MOVFM
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.FSUB
                """)
            }
            "*" -> {
                asmgen.out("""
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.MOVFM
                    lda  #<$otherName
                    ldy  #>$otherName
                    jsr  floats.FMULT
                """)
            }
            "/" -> {
                asmgen.out("""
                    lda  #<$otherName
                    ldy  #>$otherName
                    jsr  floats.MOVFM
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.FDIV
                """)
            }
            // pretty uncommon, who's going to assign a comparison boolean expression to a float var:
            "==" -> {
                asmgen.out("""
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.MOVFM
                    lda  #<$otherName
                    ldy  #>$otherName
                    jsr  floats.var_fac1_notequal_f
                    eor  #1
                    jsr  floats.FREADSA""")
            }
            "!=" -> {
                asmgen.out("""
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.MOVFM
                    lda  #<$otherName
                    ldy  #>$otherName
                    jsr  floats.var_fac1_notequal_f
                    jsr  floats.FREADSA""")
            }
            "<" -> {
                asmgen.out("""
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.MOVFM
                    lda  #<$otherName
                    ldy  #>$otherName
                    jsr  floats.var_fac1_less_f
                    jsr  floats.FREADSA""")
            }
            "<=" -> {
                asmgen.out("""
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.MOVFM
                    lda  #<$otherName
                    ldy  #>$otherName
                    jsr  floats.var_fac1_lesseq_f
                    jsr  floats.FREADSA""")
            }
            ">" -> {
                asmgen.out("""
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.MOVFM
                    lda  #<$otherName
                    ldy  #>$otherName
                    jsr  floats.var_fac1_greater_f
                    jsr  floats.FREADSA""")
            }
            ">=" -> {
                asmgen.out("""
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.MOVFM
                    lda  #<$otherName
                    ldy  #>$otherName
                    jsr  floats.var_fac1_greatereq_f
                    jsr  floats.FREADSA""")
            }
            else -> throw AssemblyError("invalid operator for in-place float modification $operator")
        }
        // store Fac1 back into memory
        asmgen.out("""
            ldx  #<$name
            ldy  #>$name
            jsr  floats.MOVMF
        """)
    }

    private fun inplacemodificationFloatWithLiteralval(name: String, operator: String, value: Double) {
        // note: this contains special optimized cases because we know the exact value. Don't replace this with another routine.
        val constValueName = allocator.getFloatAsmConst(value)
        when (operator) {
            "+" -> {
                when (value) {
                    0.0 -> return
                    1.0 -> {
                        asmgen.out("  lda  #<($name) |  ldy  #>($name) |  jsr  floats.inc_var_f")
                        return
                    }
                    0.5 -> asmgen.out("""
                        lda  #<$name
                        ldy  #>$name
                        jsr  floats.MOVFM
                        jsr  floats.FADDH
                    """)
                    else -> asmgen.out("""
                        lda  #<$name
                        ldy  #>$name
                        jsr  floats.MOVFM
                        lda  #<$constValueName
                        ldy  #>$constValueName
                        jsr  floats.FADD
                    """)
                }
            }
            "-" -> {
                if (value == 0.0)
                    return
                if(value==1.0) {
                    asmgen.out("  lda  #<($name) |  ldy  #>($name) |  jsr  floats.dec_var_f")
                    return
                }
                asmgen.out("""
                    lda  #<$constValueName
                    ldy  #>$constValueName
                    jsr  floats.MOVFM
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.FSUB
                """)
            }
            "*" -> {
                // assume that code optimization is already done on the AST level for special cases such as 0, 1, 2...
                if(value==10.0) {
                    asmgen.out("""
                        lda  #<$name
                        ldy  #>$name
                        jsr  floats.MOVFM
                        jsr  floats.MUL10
                    """)
                } else {
                    asmgen.out("""
                        lda  #<$name
                        ldy  #>$name
                        jsr  floats.MOVFM
                        lda  #<$constValueName
                        ldy  #>$constValueName
                        jsr  floats.FMULT
                    """)
                }
            }
            "/" -> {
                if (value == 0.0)
                    throw AssemblyError("division by zero")
                asmgen.out("""
                    lda  #<$constValueName
                    ldy  #>$constValueName
                    jsr  floats.MOVFM
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.FDIV
                """)
            }
            "==" -> {
                asmgen.out("""
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.MOVFM
                    lda  #<$constValueName
                    ldy  #>$constValueName
                    jsr  floats.var_fac1_notequal_f
                    eor  #1
                    jsr  floats.FREADSA""")
            }
            "!=" -> {
                asmgen.out("""
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.MOVFM
                    lda  #<$constValueName
                    ldy  #>$constValueName
                    jsr  floats.var_fac1_notequal_f
                    jsr  floats.FREADSA""")
            }
            "<" -> {
                asmgen.out("""
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.MOVFM
                    lda  #<$constValueName
                    ldy  #>$constValueName
                    jsr  floats.var_fac1_less_f
                    jsr  floats.FREADSA""")
            }
            "<=" -> {
                asmgen.out("""
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.MOVFM
                    lda  #<$constValueName
                    ldy  #>$constValueName
                    jsr  floats.var_fac1_lesseq_f
                    jsr  floats.FREADSA""")
            }
            ">" -> {
                asmgen.out("""
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.MOVFM
                    lda  #<$constValueName
                    ldy  #>$constValueName
                    jsr  floats.var_fac1_greater_f
                    jsr  floats.FREADSA""")
            }
            ">=" -> {
                asmgen.out("""
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.MOVFM
                    lda  #<$constValueName
                    ldy  #>$constValueName
                    jsr  floats.var_fac1_greatereq_f
                    jsr  floats.FREADSA""")
            }
            else -> throw AssemblyError("invalid operator for in-place float modification $operator")
        }
        // store Fac1 back into memory
        asmgen.out("""
            ldx  #<$name
            ldy  #>$name
            jsr  floats.MOVMF
        """)
    }
}
