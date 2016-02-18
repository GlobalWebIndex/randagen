package gwi.randagen

/**
  * PathDef is a specification of where events should be generated to
  * TimeSeries data is commonly stored to paths having this pattern yyyy/MM/dd/HH
  * because having a directory with terabytes or millions of files is a nightmare
  */
trait PathDef {
  def generate(p: Progress): String
}

case class TimePathDef(clock: Clock) extends PathDef {
  def generate(p: Progress): String =  clock.rewindForwardBy(p.idx)
}
