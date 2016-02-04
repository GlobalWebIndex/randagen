package gwi.randagen

import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, Month}

import org.apache.commons.math3.distribution.{NormalDistribution, UniformRealDistribution}

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
        Linear,
        TimeMapper("yyyy-MM-dd'T'HH:mm:ss.SSS", ChronoUnit.MILLIS, LocalDateTime.of(2015,Month.JANUARY, 1, 0, 0, 0))
      ),
      FieldDef(
        "gwid",
        Random(50),
        new UuidMapper[Int]
      ),
      FieldDef(
        "country",
        WeightedEnumeration[String](countries),
        new IdentityMapper[String]
      ),
      FieldDef(
        "section",
        DistributedDouble(10000, new NormalDistribution(0D, 0.2)),
        new IdentityMapper[Double]
      ),
      FieldDef(
        "purchase",
        WeightedEnumeration[String](purchase),
        new IdentityMapper[String]
      ),
      FieldDef(
        "kv_shared",
        DistributedDouble(100, new NormalDistribution(0D, 0.2)),
        new IdentityMapper[Double],
        SharedNamesQuantity(12, 20)
      ),
      FieldDef(
        "kv_unique",
        DistributedDouble(100, new NormalDistribution(0D, 0.2)),
        new IdentityMapper[Double],
        DistinctNamesQuantity(12, 20)
      ),
      FieldDef(
        "price",
        DistributedDouble(100, new UniformRealDistribution(1, 1000)),
        new RoundingMapper(2)
      )
    )
  }
}
