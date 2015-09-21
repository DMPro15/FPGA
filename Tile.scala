package Core

import Chisel._

class Tile(data_width: Int, cols: Int) extends Module{

    val io = new Bundle {
        val data_in = UInt(INPUT, data_width)
        val ping_key = Bool(INPUT)
        val data_out = UInt(OUTPUT, data_width)
    }
    val memory = Module(new PixelArray(data_width, cols))

    io.data_out := memory.io.data_out
    memory.io.data_in := io.data_in
    memory.io.ping_key := io.ping_key
    
}

class CoreTest(c: Tile) extends Tester(c) {
    poke(c.io.ping_key, true)
    poke(c.io.data_in, 0) 
    step(1)
    poke(c.io.ping_key, false)
    for(i <- 0 until 20){
        poke(c.io.data_in, i) 
        peek(c.io.data_out)
        step(1)
    }
}

object CoreMain {
    def main(args: Array[String]): Unit = {
        chiselMainTest(args, () => Module(new Tile(24, 9))) {
            c => new CoreTest(c)
        }
    }
}
    
