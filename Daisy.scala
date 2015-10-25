package Core

import Chisel._

object DaisyMain {
    def main(args: Array[String]) {
        chiselMainTest(args, () => Module(new Tile(24, 9, 3))) { c => new CoreTest(c) }
    }
}