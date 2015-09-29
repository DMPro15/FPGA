package Core

import Chisel._

class Orchestrator(cols: Int, rows: Int)  extends Module {

    var n_pings =
        3 +
        3 +
        1 +
        2

    val io = new Bundle {
        val reset = Bool(INPUT)

        val pings = Vec.fill(cols/3 + rows + 1 + 2){ Bool(OUTPUT) }
    }

    /*
     *   GRID
     *
     *   0 - Secondary mux
     *   1 - READ 0
     *   2 - PRIMARY MUX 0
     *   3 - READ 1
     *   4 - PRIMARY MUX 1
     *   5 - READ 2
     *   6 - PRIMARY MUX 2
     *
     *   No amounts of commentig is going to make this clear.
     *   View the documentation in order to understand the timing
     *
     *
     *   ALUS
     *
     *   7 - ALU mux shift
     *   8 - Accumulator flush signal
     *
     */

    /*
     * All timers are given in modulo 9, which is a constant we must adhere for all designs using 3 by 3 kernels
     * The calculations are done with respect to the leftmost element in the systolic pixel grid, and since 
     * all signals travel at the same rate this is sufficient. An important point to notice is that these 
     * timestamps describe how much later, mod 9, a signal is dispatched, not the time it is dispatched!!!
     *
     * although modularity is desired this signal box only works for 3 by 3 kernels
     */

    
    
    val T = 9

    // if the mux is register balanced set to 1
    val mux_delay = 1

    // how much a pixel is behind its successor directly one row above
    val row_wait = 5 + mux_delay

    // how much a mux is behind the mux directly above
    val mux_wait = row_wait

    // We use READ 1 as our frame of reference. It does not need to be 0
    val READ1_d = (0)                  % T 
    val MUX1_d  = (READ1 + mux_wait)   % T

    val READ2_d = (MUX1 + mux_delay)   % T
    val MUX2_d  = (READ2 + mux_wait)   % T

    val READ3_d = (MUX2 + mux_delay)   % T
    val MUX3_d  = (READ3 + mux_wait)   % T

    val ALU_MUX_SHIFT_d = 0
    val ACCUMULATOR_FLUSH_d = 0

    // The secondary muxes, being fed by the row muxes, simply needs to wait for the balance register
    val SECONDARYMUX_d = MUX3 + mux_delay
    

    // As mentioned in the comments, we must translate delays to start points relative to T0
    val READ1                = (T - READ1_d)               % T
    val MUX1                 = (T - MUX1_d)                % T
    val READ2                = (T - READ2_d)               % T
    val MUX2                 = (T - MUX2_d)                % T
    val READ3                = (T - READ2_d)               % T
    val MUX3                 = (T - MUX3_d)                % T
    val SECONDARYMUX         = (T - SECONDARYMUX_d)        % T
    val ALU_MUX_SHIFT        = (T - ALU_MUX_SHIFT_d)       % T
    val ACCUMULATOR_FLUSH    = (T - ACCUMULATOR_FLUSH_d)   % T



    val state = Reg(init=UInt(0))


    // State transitions
    when(state === UInt(T)){
        state := UInt(0)
    }
    

    // Default pings
    for(i <- 0 until io.pings.size){
        io.pings(i) := Bool(false) 
    }


    // See comments for descriptions. In scala all matching code will be run
    switch (state) {
        is( UInt(READ1)                    ){ io.pings(1) := Bool(True) }
        is( UInt(READ2)                    ){ io.pings(3) := Bool(True) }
        is( UInt(READ3)                    ){ io.pings(5) := Bool(True) }
        is( UInt(MUX1)                     ){ io.pings(2) := Bool(True) }
        is( UInt(MUX2)                     ){ io.pings(4) := Bool(True) }
        is( UInt(MUX3)                     ){ io.pings(6) := Bool(True) }
        is( UInt(SECONDARYMUX)             ){ io.pings(0) := Bool(True) }
        is( UInt(ALU_MUX_SHIFT)            ){ io.pings(7) := Bool(True) }
        is( UInt(ACCUMULATOR_FLUSH)        ){ io.pings(8) := Bool(True) }
    }

    // io.out := state
}

class OrchestratorTest(c: Orchestrator, cols: Int, rows: Int) extends Tester(c) {
    println("OrchestratorTest")
    step(5)
    poke(c.io.reset, true)
    peek(c.io)
    step(2)
    peek(c.io)
    poke(c.io.reset, false)
    for(i <- 0 until 8){
        step(1)
        peek(c.io)
    }
}
