"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
This is the code generator for gotos and subroutine calls.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

from ..plyparse import Goto, SubCall, LiteralValue, SymbolName, Dereference, Register
from . import Context, CodeError, to_hex


def generate_goto(ctx: Context) -> None:
    stmt = ctx.stmt
    assert isinstance(stmt, Goto)
    ctx.out("\v\t\t\t; " + ctx.stmt.lineref)
    if stmt.condition:
        if stmt.if_stmt:
            _gen_goto_special_if_cond(ctx, stmt)
        else:
            _gen_goto_cond(ctx, stmt)
    else:
        if stmt.if_stmt:
            _gen_goto_special_if(ctx, stmt)
        else:
            _gen_goto_unconditional(ctx, stmt)


def _gen_goto_special_if(ctx: Context, stmt: Goto) -> None:
    # a special if, but no conditional expression
    if isinstance(stmt.target, Dereference):
        # dereference is symbol, literal, or register (pair)
        if isinstance(stmt.target.operand, LiteralValue):
            targetstr = to_hex(stmt.target.operand.value)
        elif isinstance(stmt.target.operand, SymbolName):
            targetstr = stmt.target.operand.name
        else:
            # register pair
            ctx.out("\vst{:s}  il65_lib.SCRATCH_ZPWORD1".format(stmt.target.operand.name[0]))
            ctx.out("\vst{:s}  il65_lib.SCRATCH_ZPWORD1+1".format(stmt.target.operand.name[1]))
            targetstr = "il65_lib.SCRATCH_ZPWORD1"
        if stmt.if_cond == "true":
            ctx.out("\vbeq  +")
            ctx.out("\vjmp  ({:s})".format(targetstr))
            ctx.out("+")
        elif stmt.if_cond in ("not", "zero"):
            ctx.out("\vbne  +")
            ctx.out("\vjmp  ({:s})".format(targetstr))
            ctx.out("+")
        elif stmt.if_cond in ("cc", "cs", "vc", "vs", "eq", "ne"):
            if stmt.if_cond == "cc":
                ctx.out("\vbcs  +")
            elif stmt.if_cond == "cs":
                ctx.out("\vbcc  +")
            elif stmt.if_cond == "vc":
                ctx.out("\vbvs  +")
            elif stmt.if_cond == "vs":
                ctx.out("\vbvc  +")
            elif stmt.if_cond == "eq":
                ctx.out("\vbne  +")
            elif stmt.if_cond == "ne":
                ctx.out("\vbeq  +")
            ctx.out("\vjmp  ({:s})".format(targetstr))
            ctx.out("+")
        elif stmt.if_cond == "lt":
            ctx.out("\vbcs  +")
            ctx.out("\vjmp  ({:s})".format(targetstr))
            ctx.out("+")
        elif stmt.if_cond == "gt":
            ctx.out("\vbcc  +")
            ctx.out("\vbeq  +")
            ctx.out("\vjmp  ({:s})".format(targetstr))
            ctx.out("+")
        elif stmt.if_cond == "ge":
            ctx.out("\vbcc  +")
            ctx.out("\vjmp  ({:s})".format(targetstr))
            ctx.out("+")
        elif stmt.if_cond == "le":
            ctx.out("\vbeq  +")
            ctx.out("\vbcs  ++")
            ctx.out("+\t\tjmp  ({:s})".format(targetstr))
            ctx.out("+")
        else:
            raise CodeError("invalid if status " + stmt.if_cond)
    else:
        if isinstance(stmt.target, LiteralValue) and type(stmt.target.value) is int:
            targetstr = to_hex(stmt.target.value)
        elif isinstance(stmt.target, SymbolName):
            targetstr = stmt.target.name
        else:
            raise CodeError("invalid goto target type", stmt)
        if stmt.if_cond == "true":
            ctx.out("\vbne  " + targetstr)
        elif stmt.if_cond in ("not", "zero"):
            ctx.out("\vbeq  " + targetstr)
        elif stmt.if_cond in ("cc", "cs", "vc", "vs", "eq", "ne"):
            ctx.out("\vb{:s}  {:s}".format(stmt.if_cond, targetstr))
        elif stmt.if_cond == "pos":
            ctx.out("\vbpl  " + targetstr)
        elif stmt.if_cond == "neg":
            ctx.out("\vbmi  " + targetstr)
        elif stmt.if_cond == "lt":
            ctx.out("\vbcc  " + targetstr)
        elif stmt.if_cond == "gt":
            ctx.out("\vbeq  +")
            ctx.out("\vbcs  " + targetstr)
            ctx.out("+")
        elif stmt.if_cond == "ge":
            ctx.out("\vbcs  " + targetstr)
        elif stmt.if_cond == "le":
            ctx.out("\vbcc  " + targetstr)
            ctx.out("\vbeq  " + targetstr)
        else:
            raise CodeError("invalid if status " + stmt.if_cond)


def _gen_goto_unconditional(ctx: Context, stmt: Goto) -> None:
    # unconditional jump to <target>
    if isinstance(stmt.target, LiteralValue) and type(stmt.target.value) is int:
        ctx.out("\vjmp  " + to_hex(stmt.target.value))
    elif isinstance(stmt.target, SymbolName):
        ctx.out("\vjmp  " + stmt.target.name)
    elif isinstance(stmt.target, Dereference):
        # dereference is symbol, literal, or register (pair)
        if isinstance(stmt.target.operand, LiteralValue):
            ctx.out("\vjmp  ({:s})".format(to_hex(stmt.target.operand.value)))
        elif isinstance(stmt.target.operand, SymbolName):
            ctx.out("\vjmp  ({:s})".format(stmt.target.operand.name))
        else:
            # register pair
            ctx.out("\vst{:s}  il65_lib.SCRATCH_ZPWORD1".format(stmt.target.operand.name[0]))
            ctx.out("\vst{:s}  il65_lib.SCRATCH_ZPWORD1+1".format(stmt.target.operand.name[1]))
            ctx.out("\vjmp  (il65_lib.SCRATCH_ZPWORD1)")
    else:
        raise CodeError("invalid goto target type", stmt)


def _gen_goto_special_if_cond(ctx: Context, stmt: Goto) -> None:
    pass    # @todo  special if WITH conditional expression


def _gen_goto_cond(ctx: Context, stmt: Goto) -> None:
    pass    # @todo  regular if WITH conditional expression


def generate_subcall(ctx: Context) -> None:
    stmt = ctx.stmt
    assert isinstance(stmt, SubCall)
    ctx.out("\v\t\t\t; " + ctx.stmt.lineref)
    ctx.out("\v; @todo sub call: {}".format(ctx.stmt.target))
    # @todo subcall
