package gwi.randagen

import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, Month}

import org.apache.commons.math3.distribution.{NormalDistribution, UniformRealDistribution}

case class SampleEventDefFactory(
          tickUnit: ChronoUnit = ChronoUnit.SECONDS,
          uuidCardinalityRatio: Int = 50,
          sectionDataPointsCount: Int = 10000,
          priceDataPointsCount: Int = 100,
          dynamicMaxSegmentSize: Int = 3,
          dynamicMaxFieldCount: Int = 6,
          dynamicDataPointsCount: Int = 100
      ) extends EventDefFactory {

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

    val pathDef = TimePathDef(Clock("yyyy'/'MM'/'dd'/'HH", tickUnit, start))

    val fieldDefs =
      List(
        FieldDef(
          "time", s"timestamp incremented by one $tickUnit in each event",
          Linear,
          TimeValueDef(Clock("yyyy-MM-dd'T'HH:mm:ss.SSS", tickUnit, start))
        ),
        FieldDef(
          "idx", "growing number from 0 until total-number-of-events",
          Linear,
          new IdentityValueDef[Int]
        ),
        FieldDef(
          "uuid", "randomly generated UUID strings with cardinality 50%",
          Random(uuidCardinalityRatio),
          new UuidValueDef[Int]
        ),
        FieldDef(
          "country", "sample of countries with weighted probability distribution",
          WeightedEnumeration[String](countries),
          new IdentityValueDef[String]
        ),
        FieldDef(
          "section", s"normally distributed Doubles sampled from $sectionDataPointsCount distinct values",
          DistributedDouble(sectionDataPointsCount, new NormalDistribution(0D, 0.2)),
          new IdentityValueDef[Double]
        ),
        FieldDef(
          "purchase", "4 purchase types with weighted probability distribution",
          WeightedEnumeration[String](purchase),
          new IdentityValueDef[String]
        ),
        FieldDef(
          "price", s"randomly distributed Doubles sampled from $priceDataPointsCount distinct values each between 1 and 1000",
          DistributedDouble(priceDataPointsCount, new UniformRealDistribution(1, 1000)),
          new RoundingValueDef(2)
        ),
        FieldDef(
          "multi_shared",
          s"""
             |$dynamicMaxSegmentSize segments/groups/clients each with random count of dimensions <1 - $dynamicMaxFieldCount> which are
             |randomly shared among segments <multi_shared_1 - multi_shared_$dynamicMaxFieldCount>, sampled from $dynamicDataPointsCount distinct values being Normally distributed.
          """.stripMargin,
          DistributedDouble(dynamicDataPointsCount, new NormalDistribution(0D, 0.2)),
          new IdentityValueDef[Double],
          SharedNamesQuantity(dynamicMaxSegmentSize, dynamicMaxFieldCount)
        ),
        FieldDef(
          "multi_unique",
          s"""
             |$dynamicMaxSegmentSize segments/groups/clients each with random count of dimensions <1 - $dynamicMaxFieldCount> which are
             |NOT shared among segments at all (they are entirely distinct), sampled from $dynamicDataPointsCount distinct values being Normally distributed.
          """.stripMargin,
          DistributedDouble(dynamicDataPointsCount, new NormalDistribution(0D, 0.2)),
          new IdentityValueDef[Double],
          DistinctNamesQuantity(dynamicMaxSegmentSize, dynamicMaxFieldCount)
        )
      )

    EventDef(fieldDefs, Some(pathDef))
  }
}
