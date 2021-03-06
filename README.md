**High performance generator of data**

[![Build Status](https://drone.globalwebindex.com/api/badges/GlobalWebIndex/randagen/status.svg)](https://drone.in.globalwebindex.com/GlobalWebIndex/randagen)

It is able to generate data :
 - randomly distributed
    - On SSD it can persist a billion events to FileSystem with 60GB of data in 20 minutes using just 6GB of Heap 
    - it is basically as fast as your SSD, I benched ~ **90MB/s** 
    - note that s3 upload is way slower
 - with precise specific cardinality
    - fast, not CPU intensive, only memory consuming 
    - generating billion events requires 4.5GB+ of Heap because in order to implement value Cardinality you need to
      keep 1 billion of Ints in memory and these primitive 32bits Ints itself take 4GBs
 - variety of [probability distributions](https://commons.apache.org/proper/commons-math/userguide/distribution.html)
    - slower, CPU intensive, not memory consuming
    - expect performance decrease in case you are not running on quad-core processor, bottleneck can move from IO to CPU (not on s3)
    - ie. events of 1000 fields each with its own Normal distribution(mean 0, variance 0.2) are generated **70MB/s** on quad-core with parallelism 4
 - on custom automatically generated paths
    - TimeSeries data is commonly stored to paths having pattern like yyyy/MM/dd/HH because having a directory millions of files is a nightmare
 - to files/s3Objects of a certain size limit
    - technologies mostly cannot deal with huge files, so having a chance to limit file size to say 50MB is a MUST
 
## How to

Data can be generated to blob storages, if you do so, you should have these variables exported or stored in file :
```
$ cat ~/.aws/aws.env 
AWS_ACCESS_KEY_ID=???
AWS_SECRET_ACCESS_KEY=???
AWS_DEFAULT_REGION=???
```
or in case of google :
```
GOOGLE_APPLICATION_CREDENTIALS=???
```

Then you're all set, use `gwiq/randagen` docker image with sample data definition :
 - use `-v` flag to make output data (in case of FS storage) accessible 
 - don't forget to change `-Xmx` appropriately 

```
docker run --rm --env-file=/home/ubuntu/.aws/aws.env -v /home/ubuntu/tmp:/tmp -e JAVA_TOOL_OPTIONS=-Xmx4g gwiq/randagen-app ARGS
```

Just use real arguments instead of `ARGS` ^, usage :
Usage
```
 randagen <format> [<batch-flush-megabytes-limit>] <records-count> <parallelism> <storage> <compress> <path>
```
Arguments
```
   <format>                      : [ tsv | csv | json ]
   <batch-flush-megabytes-limit> : When to flush in-memory data to disk or network
   <records-count>               : How many records to generate
   <parallelism>                 : How many threads should be leveraged for data generation
   <storage>                     : [ s3 | gcs | fs ]
   <compress>                    : Whether to gzip output or not
   <path>                        : S3 of FS path: [ bucket@foo/bar  | /tmp/data ]
```
Example arguments :
```
format  batch-flush-megabytes-limit  records-count  parallelism  storage  compress  path
---------------------------------------------------------------------------------------------
tsv          50                         10000000         2          s3       true     bucket@foo/bar
csv          50                         10000000         4          fs       false    /tmp/data
json         50                         10000000         4          fs       false    /tmp/data
```

Note ^^^ that 
 - parallelism is decreased to just 2 cores when storing data to `s3` because it is way slower outside ec2 cloud
 - `batch-flush-megabytes-limit` is a batch **maximum** byte size restriction
    - data will by stored to max 50MB big files or s3Objects in case of `fs` or `s3` storage 

Or use it as a dependency / project / library :

```
resolvers ++= Seq("GitHub Package Registry (GlobalWebIndex/randagen)" at s"https://maven.pkg.github.com/GlobalWebIndex/randagen")
libraryDependencies += "net.globalwebindex" %% "randagen" % "x.y.x"
```
or
```
dependsOn(ProjectRef(uri("https://github.com/GlobalWebIndex/randagen.git#vx.y.x"), "randagen-core"))
```
or
```
RanDaGen.run(50, 10000000, Parallelism(4), JsonEventGenerator, FsEventConsumer(targetPath), eventDefFactory)
```

`eventDefFactory` describes the whole data set, see the [Sample Event Definition!](core/src/main/scala/gwi/randagen/SampleEventDefFactory.scala)
