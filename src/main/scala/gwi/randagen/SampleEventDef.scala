package gwi.randagen

import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, Month}

import org.apache.commons.math3.distribution.{NormalDistribution, UniformIntegerDistribution, UniformRealDistribution}

object SampleEventDef extends EventDef {

  def apply(implicit p: Parallelism): List[FieldDef] = {
    def purchase = Array("micro" -> 0.1,"small" -> 0.2,"medium" -> 0.4,"large" -> 0.3)
    def countries = {
      val list = List(
        "bra","nzl","bel","bgr","idn","egy","tur","nor","pol","jpn","esp","irl","cze","dnk","che","nld",
        "ita","rus","pri","deu","eur","pry","usa","dom","gtm","ury","col","fra","isr","arg","mex","gbr"
      )
      list.zip(list.foldLeft(List(0.2)) { case (acc, _) => (acc.head * 1.1) :: acc }).toArray
    }

    List(
      FieldDef(
        "time",
        Constant(1),
        Linear,
        TimeMapper("yyyy-MM-dd'T'HH:mm:ss.SSS", ChronoUnit.MILLIS, LocalDateTime.of(2015,Month.JANUARY, 1, 0, 0, 0))
      ),
      FieldDef(
        "gwid",
        Constant(1),
        Random(50),
        new UuidMapper[Int]
      ),
      FieldDef(
        "country",
        Constant(1),
        WeightedEnumeration[String](countries),
        new IdentityMapper[String]
      ),
      FieldDef(
        "section",
        Constant(1),
        DistributedDouble(10000, new NormalDistribution(0D, 0.2)),
        new IdentityMapper[Double]
      ),
      FieldDef(
        "purchase",
        Constant(1),
        WeightedEnumeration[String](purchase),
        new IdentityMapper[String]
      ),
      FieldDef(
        "kv",
        DistributedInteger(100, new UniformIntegerDistribution(1, 1000)),
        DistributedDouble(12, new NormalDistribution(0D, 0.2)),
        new IdentityMapper[Double]
      ),
      FieldDef(
        "price",
        Constant(1),
        DistributedDouble(100, new UniformRealDistribution(1, 1000)),
        new RoundingMapper(2)
      )
    )
  }
}
