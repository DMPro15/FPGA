package Core

import Chisel._

// Stole all of this code from some dude on github 
import TidbitsOCM._


class SliceReverseBuffer(row_length: Int, data_width: Int, kernel_dim: Int) extends Module {

    val cols = kernel_dim*kernel_dim - 2
    val total_enqueues = cols*(row_length - 2)
    val total_dequeues = total_enqueues
    
    val io = new Bundle {
        val data_in = UInt(INPUT, data_width)

        val enqueue = Bool(INPUT)
        val dequeue = Bool(INPUT)

        val can_enqueue = Bool(OUTPUT)
        val can_dequeue = Bool(OUTPUT)

        val data_out = UInt(OUTPUT, data_width)
    }

    val row_buffers = for(i <- 0 until cols) yield Module(new RowBuffer(row_length, data_width, i)).io
      
    val enqueue_row = Reg(init=UInt(0, 32))
    val dequeue_row  = Reg(init=UInt(0, 32))

    val row_dequeue_count = Reg(init=UInt(0, 32))

    val enqueues_performed = Reg(init=UInt(0, 32))
    val dequeues_performed = Reg(init=UInt(0, 32))

    val enqueues_finished = Reg(Bool(false))
    val dequeues_finished = Reg(Bool(false))

    io.data_out := UInt(57005)

    io.can_dequeue := Bool(false)
    io.can_enqueue := Bool(false)

    when(row_dequeue_count === UInt(row_length - (1 + 2) )){
        row_dequeue_count := UInt(0)
        when(dequeue_row < UInt(cols)){
            dequeue_row := dequeue_row + UInt(1)
        }.otherwise{
            dequeue_row := UInt(0)
        }
    }

    when(io.dequeue){
        when(row_dequeue_count === UInt(row_length - (1 + 2) )){
            row_dequeue_count := UInt(0)
        }.otherwise{
            row_dequeue_count := row_dequeue_count + UInt(1)
        }
    }

    // Maintain enqueue row
    when(io.enqueue){
        when(enqueue_row < UInt(cols - 1)){
            enqueue_row := enqueue_row  + UInt(1)    
        }.otherwise{
            enqueue_row  := UInt(0)
        }
    }

    // enqueue data 
    for(i <- 0 until cols){
        when(enqueue_row  === UInt(i)){
            row_buffers(i).push := io.enqueue
            row_buffers(i).data_in := io.data_in
        }.otherwise{
            row_buffers(i).push := Bool(false)
            row_buffers(i).data_in := UInt(57005)
        }
    }

    // deq data
    for(i <- 0 until cols){
        when(dequeue_row === UInt(i)){
            row_buffers(i).pop := io.dequeue
            io.data_out := row_buffers(i).data_out
        }.otherwise{
            io.data_out := UInt(57005)
            row_buffers(i).pop := Bool(false)
        }
    }

    when(io.dequeue){
        dequeues_performed := enqueues_performed + UInt(1)

        when(dequeues_performed === UInt(total_dequeues - 1)){
            dequeues_finished := Bool(true)
        }
    }

    when(io.enqueue){
        enqueues_performed := enqueues_performed + UInt(1)

        when(enqueues_performed === UInt(total_enqueues - 1)){
            enqueues_finished := Bool(true)
        }
    }

    when(!dequeues_finished){
        io.can_dequeue := Bool(true)
    }

    when(!enqueues_finished){
        io.can_enqueue := Bool(true)
    }

    when( dequeues_finished && enqueues_finished ){
        dequeues_finished := Bool(false)
        enqueues_finished := Bool(false)
    }
}
