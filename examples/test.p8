%import textio
%import buffers

%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        txt.print("stack\n")
        test_stack()
        txt.print("\nringbuffer\n")
        test_ring()
        txt.print("\nsmallringbuffer\n")
        test_smallring()
    }

    sub test_stack() {
        stack.init(2)
        txt.print_uw(stack.size())
        txt.spc()
        txt.print_uw(stack.free())
        txt.nl()
        stack.push(1)
        stack.push(2)
        stack.push(3)
        stack.pushw(12345)
        txt.print_uw(stack.size())
        txt.spc()
        txt.print_uw(stack.free())
        txt.nl()
        txt.nl()
        txt.print_uw(stack.popw())
        txt.nl()
        txt.print_uw(stack.pop())
        txt.nl()
        txt.print_uw(stack.pop())
        txt.nl()
        txt.print_uw(stack.pop())
        txt.nl()
        txt.nl()
        txt.print_uw(stack.size())
        txt.spc()
        txt.print_uw(stack.free())
        txt.nl()
    }

    sub test_ring() {
        ringbuffer.init(2)
        txt.print_uw(ringbuffer.size())
        txt.spc()
        txt.print_uw(ringbuffer.free())
        txt.nl()
        ringbuffer.put(1)
        ringbuffer.put(2)
        ringbuffer.put(3)
        ringbuffer.putw(12345)
        txt.print_uw(ringbuffer.size())
        txt.spc()
        txt.print_uw(ringbuffer.free())
        txt.nl()
        txt.nl()
        txt.print_uw(ringbuffer.get())
        txt.nl()
        txt.print_uw(ringbuffer.get())
        txt.nl()
        txt.print_uw(ringbuffer.get())
        txt.nl()
        txt.print_uw(ringbuffer.getw())
        txt.nl()
        txt.nl()
        txt.print_uw(ringbuffer.size())
        txt.spc()
        txt.print_uw(ringbuffer.free())
        txt.nl()
    }

    sub test_smallring() {
        smallringbuffer.init()
        txt.print_uw(smallringbuffer.size())
        txt.spc()
        txt.print_uw(smallringbuffer.free())
        txt.nl()
        smallringbuffer.put(1)
        smallringbuffer.put(2)
        smallringbuffer.put(3)
        smallringbuffer.putw(12345)
        txt.print_uw(smallringbuffer.size())
        txt.spc()
        txt.print_uw(smallringbuffer.free())
        txt.nl()
        txt.nl()
        txt.print_uw(smallringbuffer.get())
        txt.nl()
        txt.print_uw(smallringbuffer.get())
        txt.nl()
        txt.print_uw(smallringbuffer.get())
        txt.nl()
        txt.print_uw(smallringbuffer.getw())
        txt.nl()
        txt.nl()
        txt.print_uw(smallringbuffer.size())
        txt.spc()
        txt.print_uw(smallringbuffer.free())
        txt.nl()
    }
}
