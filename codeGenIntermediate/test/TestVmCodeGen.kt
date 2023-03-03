import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import prog8.code.SymbolTableMaker
import prog8.code.ast.*
import prog8.code.core.*
import prog8.code.target.VMTarget
import prog8.codegen.vm.VmAssemblyProgram
import prog8.codegen.vm.VmCodeGen
import prog8.intermediate.IRSubroutine

class TestVmCodeGen: FunSpec({

    fun getTestOptions(): CompilationOptions {
        val target = VMTarget()
        return CompilationOptions(
            OutputType.RAW,
            CbmPrgLauncherType.NONE,
            ZeropageType.DONTUSE,
            zpReserved = emptyList(),
            floats = true,
            noSysInit = false,
            compTarget = target,
            loadAddress = target.machine.PROGRAM_LOAD_ADDRESS
        )
    }

    test("augmented assigns") {
//main {
//    sub start() {
//        ubyte[] particleX = [1,2,3]
//        ubyte[] particleDX = [1,2,3]
//        particleX[2] += particleDX[2]
//
//        word @shared xx = 1
//        xx = -xx
//        xx += 42
//        xx += cx16.r0
//    }
//}
        val codegen = VmCodeGen()
        val program = PtProgram("test", DummyMemsizer, DummyStringEncoder)
        val block = PtBlock("main", null, false, false, PtBlock.BlockAlignment.NONE, SourceCode.Generated("test"), Position.DUMMY)
        val sub = PtSub("start", emptyList(), null, Position.DUMMY)
        sub.add(PtVariable("pi", DataType.UBYTE, ZeropageWish.DONTCARE, PtNumber(DataType.UBYTE, 0.0, Position.DUMMY), null, Position.DUMMY))
        sub.add(PtVariable("particleX", DataType.ARRAY_UB, ZeropageWish.DONTCARE, null, 3u, Position.DUMMY))
        sub.add(PtVariable("particleDX", DataType.ARRAY_UB, ZeropageWish.DONTCARE, null, 3u, Position.DUMMY))
        sub.add(PtVariable("xx", DataType.WORD, ZeropageWish.DONTCARE, PtNumber(DataType.WORD, 1.0, Position.DUMMY), null, Position.DUMMY))

        val assign = PtAugmentedAssign("+=", Position.DUMMY)
        val target = PtAssignTarget(Position.DUMMY).also {
            val targetIdx = PtArrayIndexer(DataType.UBYTE, Position.DUMMY).also { idx ->
                idx.add(PtIdentifier("main.start.particleX", DataType.ARRAY_UB, Position.DUMMY))
                idx.add(PtNumber(DataType.UBYTE, 2.0, Position.DUMMY))
            }
            it.add(targetIdx)
        }
        val value = PtArrayIndexer(DataType.UBYTE, Position.DUMMY)
        value.add(PtIdentifier("main.start.particleDX", DataType.ARRAY_UB, Position.DUMMY))
        value.add(PtNumber(DataType.UBYTE, 2.0, Position.DUMMY))
        assign.add(target)
        assign.add(value)
        sub.add(assign)

        val prefixAssign = PtAugmentedAssign("-", Position.DUMMY)
        val prefixTarget = PtAssignTarget(Position.DUMMY).also {
            it.add(PtIdentifier("main.start.xx", DataType.WORD, Position.DUMMY))
        }
        prefixAssign.add(prefixTarget)
        prefixAssign.add(PtIdentifier("main.start.xx", DataType.WORD, Position.DUMMY))
        sub.add(prefixAssign)

        val numberAssign = PtAugmentedAssign("+=", Position.DUMMY)
        val numberAssignTarget = PtAssignTarget(Position.DUMMY).also {
            it.add(PtIdentifier("main.start.xx", DataType.WORD, Position.DUMMY))
        }
        numberAssign.add(numberAssignTarget)
        numberAssign.add(PtNumber(DataType.WORD, 42.0, Position.DUMMY))
        sub.add(numberAssign)

        val cxregAssign = PtAugmentedAssign("+=", Position.DUMMY)
        val cxregAssignTarget = PtAssignTarget(Position.DUMMY).also {
            it.add(PtIdentifier("main.start.xx", DataType.WORD, Position.DUMMY))
        }
        cxregAssign.add(cxregAssignTarget)
        cxregAssign.add(PtIdentifier("cx16.r0", DataType.UWORD, Position.DUMMY))
        sub.add(cxregAssign)

        block.add(sub)
        program.add(block)

        // define the "cx16.r0" virtual register
        val cx16block = PtBlock("cx16", null, false, false, PtBlock.BlockAlignment.NONE, SourceCode.Generated("test"), Position.DUMMY)
        cx16block.add(PtMemMapped("r0", DataType.UWORD, 100u, null, Position.DUMMY))
        program.add(cx16block)

        val options = getTestOptions()
        val st = SymbolTableMaker(program, options).make()
        val errors = ErrorReporterForTests()
        val result = codegen.generate(program, st, options, errors) as VmAssemblyProgram
        val irChunks = (result.irProgram.blocks.first().children.single() as IRSubroutine).chunks
        irChunks.size shouldBeGreaterThan 4
    }
})