package eu.themerius.docelemstore.utils

object Stats {
  def time[A](msg: String = "Default")(f: => A) = {
    val s = System.nanoTime
    val ret = f
    println(s"${msg} time: ${(System.nanoTime-s)/1e6} ms")
    ret
  }
}
