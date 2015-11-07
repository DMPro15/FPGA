package Core

import Chisel._

class Multiplier(data_width: Int) extends Module {

    val io = new Bundle { 
        val pixel_in = UInt(INPUT, data_width)
        val kernel_in = SInt(INPUT, data_width)

        val data_out = SInt(OUTPUT, data_width) 
        val kernel_out = SInt(OUTPUT, data_width)
    } 

    val kernel = Reg(UInt(width=data_width))

    val color1 = io.pixel_in(7,0)
    val color2 = io.pixel_in(15,8)
    val color3 = io.pixel_in(23,16)

    kernel := io.kernel_in
    io.kernel_out := kernel

    io.data_out := UInt(0)

    io.data_out(7, 0) := color1*kernel
    io.data_out(15, 8) := color2*kernel
    io.data_out(23, 16) := color3*kernel
}


class Accumulator(data_width: Int) extends Module {

    val io = new Bundle { 
        val pixel_in = SInt(INPUT, data_width)
        val flush = Bool(INPUT)
        val active = Bool(INPUT)

        val data_out = UInt(OUTPUT, data_width) 
        val valid_out = Bool(OUTPUT)
    } 

    // val accumulator = Reg(SInt(init=0, width=data_width))
    val accumulator = Reg(init=SInt(0, data_width))
    when(io.flush){
        accumulator := io.pixel_in
    }
    .otherwise{
        accumulator := accumulator + io.pixel_in
    }
    

    val color1 = io.pixel_in(7,0)
    val color2 = io.pixel_in(15,8)
    val color3 = io.pixel_in(23,16)

    when(io.active){
        when(io.flush){ 
            accumulator(7, 0) := color1
            accumulator(15, 8) := color2
            accumulator(23, 16) := color3

        }.otherwise{
            accumulator(7, 0) := accumulator(7, 0) + color1
            accumulator(15, 8) := accumulator(15, 8) + color2
            accumulator(23, 16) := accumulator(23, 16) + color3
        }
    }

    io.data_out := accumulator

}


class ALUrow(data_width: Int, cols: Int, rows: Int) extends Module{

    val n_ALUs = cols - 2  

    val io = new Bundle { 
        val data_in = Vec.fill(rows){ UInt(INPUT, width=data_width) }
        val kernel_in = SInt(INPUT, width=data_width)
        val accumulator_flush = Bool(INPUT)
        val selector_shift = Bool(INPUT)
        val active = Bool(INPUT)

        val data_out = UInt(OUTPUT, width=data_width)
        val kernel_out = SInt(OUTPUT, width=data_width)
        val valid_out = Bool(OUTPUT)

        val dbg_accumulators_out = Vec.fill(n_ALUs){ UInt(OUTPUT, width=data_width) }
        val dbg_multipliers_in  = Vec.fill(n_ALUs){ UInt(OUTPUT, width=data_width) }
        val dbg_kernel_out  = Vec.fill(n_ALUs){ SInt(OUTPUT, width=data_width) }
    } 

    val multipliers = Vec.fill(n_ALUs){ Module(new Multiplier(data_width)).io }
    val accumulators = Vec.fill(n_ALUs){ Module(new Accumulator(data_width)).io }

    val selectors = Vec.fill(n_ALUs){ Module(new ShiftMux3(data_width, 3, 0)).io }
    val shift_enablers = Vec.fill(n_ALUs){ Reg(Bool()) }
    val flush_signals = Vec.fill(n_ALUs){ Reg(Bool()) }
    
    
    // Wire ALU selectors
    for(i <- 0 until n_ALUs){
        for(j <- 0 until 3){
            selectors(i).data_in(j) := io.data_in(j)
            selectors(i).active := io.active
        }
        multipliers(i).pixel_in := selectors(i).data_out 
        selectors(i).shift := shift_enablers(i)
    }

    daisy_chain(io.selector_shift, shift_enablers)
    daisy_chain(io.accumulator_flush, flush_signals)
    
    // wire_all(flush_signals, accumulators

    // Wire flush enablers
    // wire_all(accumulators, flush_signals, (x: Accumulator) => x.io.flush)
    for(i <- 0 until (n_ALUs)){
        accumulators(i).flush := flush_signals(i)
    }

    // wire valid output
    io.valid_out := Bool(false)
    for(i <- 0 until n_ALUs){
        when(accumulators(i).valid_out){
            io.valid_out := Bool(true)
        }
    }

    // Wire kernel chain
    multipliers(0).kernel_in := io.kernel_in
    multipliers(0).pixel_in := selectors(0).data_out
    accumulators(0).pixel_in := multipliers(0).data_out
    
    for(i <- 1 until n_ALUs){
        multipliers(i).kernel_in := multipliers(i-1).kernel_out    
        multipliers(i).pixel_in := selectors(i).data_out
        accumulators(i).pixel_in := multipliers(i).data_out
    }
    // Since the kernel chain is cyclic it is needed outside this scope
    io.kernel_out := multipliers(n_ALUs - 1).kernel_out


    io.data_out := UInt(0)
    for(i <- 0 until n_ALUs){
        when(flush_signals(i)){ io.data_out := accumulators(i).data_out }

        io.dbg_accumulators_out(i) := accumulators(i).data_out
        io.dbg_multipliers_in(i) := multipliers(i).pixel_in
    }

    for(i <- 0 until n_ALUs){
        io.dbg_kernel_out(i) := multipliers(i).kernel_out
    }



    def daisy_chain[T <: Data](input: T, elements: Vec[T]){
        elements(0) := input
        for(i <- 1 until elements.length){
            elements(i) := elements(i-1)
        }
    }

    // def wire_all[T <: Data](inputs: Vec[T], outputs: Vec[T], f: T => T){
    // // def wire_all[T <: Data](inputs: Vec[T], outputs: Vec[T]){
    //     for(i <- 0 until inputs.length){
    //         f(inputs(i)) := outputs(i)
    //     }
    // }
}