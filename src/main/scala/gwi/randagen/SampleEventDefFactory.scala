package gwi.randagen

import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, Month}

import org.apache.commons.math3.distribution.{NormalDistribution, UniformRealDistribution}

object SampleEventDefFactory extends EventDefFactory {

  def apply(implicit p: Parallelism): EventDef = {
    def purchase = Array("micro" -> 0.1,"small" -> 0.2,"medium" -> 0.4,"large" -> 0.3)
    def countries = {
      val list = List(
        "bra","nzl","bel","bgr","idn","egy","tur","nor","pol","jpn","esp","irl","cze","dnk","che","nld",
        "ita","rus","pri","deu","eur","pry","usa","dom","gtm","ury","col","fra","isr","arg","mex","gbr"
      )
      list.zip(list.foldLeft(List(0.2)) { case (acc, _) => (acc.head * 1.1) :: acc }).toArray
    }

    val start = LocalDateTime.of(2015,Month.JANUARY, 1, 0, 0, 0)

    val pathDef = TimePathDef(Clock("yyyy'/'MM'/'dd'/'HH", ChronoUnit.SECONDS, start))

    val fieldDefs =
      List(
        FieldDef(
          "time", "timestamp incremented by one second in each event",
          Linear,
          TimeValueDef(Clock("yyyy-MM-dd'T'HH:mm:ss.SSS", ChronoUnit.SECONDS, start))
        ),
        FieldDef(
          "idx", "growing number from 0 until total-number-of-events",
          Linear,
          new IdentityValueDef[Int]
        ),
        FieldDef(
          "uuid", "randomly generated UUID strings with cardinality 50%",
          Random(50),
          new UuidValueDef[Int]
        ),
        FieldDef(
          "country", "sample of countries with weighted probability distribution",
          WeightedEnumeration[String](countries),
          new IdentityValueDef[String]
        ),
        FieldDef(
          "section", "normally distributed Doubles sampled from 10 000 distinct values",
          DistributedDouble(10000, new NormalDistribution(0D, 0.2)),
          new IdentityValueDef[Double]
        ),
        FieldDef(
          "purchase", "4 purchase types with weighted probability distribution",
          WeightedEnumeration[String](purchase),
          new IdentityValueDef[String]
        ),
        FieldDef(
          "price", "randomly distributed Doubles sampled from 100 distinct values each between 1 and 1000",
          DistributedDouble(100, new UniformRealDistribution(1, 1000)),
          new RoundingValueDef(2)
        ),
        FieldDef(
          "multi_shared",
          """
             |3 segments/groups/clients each with random count of dimensions <1 - 6> which are
             |randomly shared among segments <multi_shared_1 - multi_shared_6>, sampled from 100 distinct values being Normally distributed.
          """.stripMargin,
          DistributedDouble(100, new NormalDistribution(0D, 0.2)),
          new IdentityValueDef[Double],
          SharedNamesQuantity(3, 6)
        ),
        FieldDef(
          "multi_unique",
          """
             |3 segments/groups/clients each with random count of dimensions <1 - 6> which are
             |NOT shared among segments at all (they are entirely distinct), sampled from 100 distinct values being Normally distributed.
          """.stripMargin,
          DistributedDouble(100, new NormalDistribution(0D, 0.2)),
          new IdentityValueDef[Double],
          DistinctNamesQuantity(3, 6)
        )
      )

    EventDef(fieldDefs, Some(pathDef))
  }
}
