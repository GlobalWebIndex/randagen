package gwi.randagen

import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDateTime, ZoneOffset}

import org.apache.commons.math3.distribution.{NormalDistribution, UniformRealDistribution}

case class SampleEventDefFactory(
          tickUnit: ChronoUnit = ChronoUnit.SECONDS,
          start: Long = LocalDateTime.of(2015, 1, 1, 0, 0, 0).atZone(ZoneOffset.UTC).toLocalDateTime.toInstant(ZoneOffset.UTC).toEpochMilli,
          timePathPattern: String = SampleEventDefFactory.TimePathPattern,
          timeStampPattern: String = SampleEventDefFactory.TimeStampPattern,
          uuidCardinalityRatio: Int = 50,
          sectionDataPointsCount: Int = 100,
          priceDataPointsCount: Int = 100,
          dynamicMaxSegmentSize: Int = 3,
          dynamicMaxFieldCount: Int = 6,
          dynamicDataPointsCount: Int = 100
      ) extends EventDefFactory {

  def apply(implicit p: Parallelism): EventDef = {
    import SampleEventDefFactory._

    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(start), ZoneOffset.UTC)

    val pathDef = TimePathDef(Clock(timePathPattern, tickUnit, dateTime))

    val fieldDefs =
      List(
        FieldDef(
          timeFieldName, s"timestamp incremented by one $tickUnit in each event",
          Linear,
          TimeValueDef(Clock(timeStampPattern, tickUnit, dateTime))
        ),
        FieldDef(
          idxFieldName, "growing number from 0 until total-number-of-events",
          Linear,
          new IdentityValueDef[Int]
        ),
        FieldDef(
          uuidFieldName, "randomly generated UUID strings with cardinality 50%",
          Random(uuidCardinalityRatio),
          new UuidValueDef[Int]
        ),
        FieldDef(
          countryFieldName, "sample of countries with weighted probability distribution",
          WeightedEnumeration[String](countries),
          new IdentityValueDef[String]
        ),
        FieldDef(
          sectionFieldName, s"normally distributed Doubles sampled from $sectionDataPointsCount distinct values",
          DistributedDouble(sectionDataPointsCount, new NormalDistribution(0D, 0.2)),
          new IdentityValueDef[Double]
        ),
        FieldDef(
          purchaseFieldName, "4 purchase types with weighted probability distribution",
          WeightedEnumeration[String](weightedPurchasePMF),
          new IdentityValueDef[String]
        ),
        FieldDef(
          priceFieldName, s"randomly distributed Doubles sampled from $priceDataPointsCount distinct values each between 1 and 1000",
          DistributedDouble(priceDataPointsCount, new UniformRealDistribution(1, 1000)),
          new RoundingValueDef(2)
        ),
        FieldDef(
          "ms", // multi-fields shared
          s"""
             |$dynamicMaxSegmentSize segments/groups/clients each with random count of dimensions <1 - $dynamicMaxFieldCount> which are
             |randomly shared among segments <multi_shared_1 - multi_shared_$dynamicMaxFieldCount>, sampled from $dynamicDataPointsCount distinct values being Normally distributed.
          """.stripMargin,
          DistributedDouble(dynamicDataPointsCount, new NormalDistribution(0D, 0.2)),
          new IdentityValueDef[Double],
          SharedNamesQuantity(dynamicMaxSegmentSize, dynamicMaxFieldCount)
        ),
        FieldDef(
          "mu", // multi-fields distinct
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

object SampleEventDefFactory {
  val TimeStampPattern = "yyyy-MM-dd'T'HH:mm:ss.SSS"
  val TimePathPattern = "yyyy'/'MM'/'dd'/'HH"

  val timeFieldName = "time"
  val idxFieldName = "idx"
  val uuidFieldName = "uuid"
  val countryFieldName = "country"
  val sectionFieldName = "section"
  val purchaseFieldName = "purchase"
  val priceFieldName = "price"

  def purchases = Array("s","m","l","xl")
  def weightedPurchasePMF = purchases.zip(Seq(0.1,0.2,0.4,0.3))
  def countries = {
    val list = List(
      "bra","nzl","bel","bgr","idn","egy","tur","nor","pol","jpn","esp","irl","cze","dnk","che","nld",
      "ita","rus","pri","deu","eur","pry","usa","dom","gtm","ury","col","fra","isr","arg","mex","gbr"
    )
    list.zip(list.foldLeft(List(0.2)) { case (acc, _) => (acc.head * 1.1) :: acc }).toArray
  }
}