import org.apache.spark.streaming.dstream.DStream

object DirectKafkaAverageHouseholdIncome {
  import org.apache.spark.streaming.kafka._
  import org.apache.spark.{SparkConf,SparkContext}
  import org.apache.spark.streaming._

  val conf = new SparkConf().setMaster("local[2]").setAppName(this.getClass.toString)
  val ssc = new StreamingContext(conf, Seconds(1))
  val hdfsPath = "hdfs:///user/hive/warehouse/income"
  val incomeCsv = ssc.textFileStream(hdfsPath)
  // Format of the data is
  //GEO.id,GEO.id2,GEO.display-label,VD01
  //Id,Id2,Geography,Median family income in 1999
  //8600000US998HH,998HH,"998HH 5-Digit ZCTA, 998 3-Digit ZCTA",0
  val parsedCsv = parse(incomeCsv)
  // 2nd element (index 1) is zip code, last element (index 3) is income
  // We take the first 3 digits of zip code and find average income in that geographic area
  val areaIncomeStream = parsedCsv.map(record => (record(1).substring(0,3), record(3).toInt))

  // First element of the tuple in DStream are total incomes, second is total number of zip codes
  // in that geographic area, for which the income is shown
  val runningTotals = areaIncomeStream.mapValues(x => (x, 1))
    .reduceByKey((x,y) => (x._1 + y._1, x._2 + y._2)).mapValues(divide _.tupled)

  runningTotals.print(20)

  def divide(x: Int, y: Int): Double = {
    x/y
  }

  def parse(incomeCsv: DStream[String]): DStream[List[String]] = {
    val builder = StringBuilder.newBuilder
    incomeCsv.map(x => {
      var result = List[String]()
      var withinQuotes = false
      x.foreach(c => {
        if (c.equals(',') && !withinQuotes) {
          result = result :+ builder.toString
          builder.clear()
        } else if (c.equals('\"')) {
          builder.append(c)
          withinQuotes = !withinQuotes
        } else {
          builder.append(c)
        }
      })
      result :+ builder.toString
    })
  }
}
