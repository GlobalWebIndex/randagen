**High performance generator of data**

It can persist a billion events to FileSystem with 60GB of data in 20 minutes using just 6GB of Heap (s3 upload is way slower)
It is able to generate data with :
 - specific cardinality
    - fast, not CPU intensive, only memory consuming 
    - generating billion events requires 4.5GB+ of Heap because in order to implement value Cardinality you need to
      keep 1 billion of Ints in memory and these primitive 32bits Ints itself take 4GBs

 - variety of [probability distributions](https://commons.apache.org/proper/commons-math/userguide/distribution.html)
    - slow, very CPU intensive, not memory consuming, expect 8x performance decrease - it was not optimized yet
 
## How to

Data can be generated to s3, if you do so, you should have :
```
$ cat ~/.aws/aws.env 
AWS_ACCESS_KEY_ID=???
AWS_SECRET_ACCESS_KEY=???
AWS_DEFAULT_REGION=???
```

Then you're all set, use `gwiq/randagen` docker image (use `-v` flag if you use FS and don't forget to change `-Xmx`):

```
docker run --rm --env-file=/home/ubuntu/.aws/aws.env -e JAVA_TOOL_OPTIONS=-Xmx4g gwiq/randagen ARGS
```

Just use real arguments instead of `ARGS` ^, examples :
```
dataType   dataSet   batchSize   eventCount   storage       path          jsonDataSetDefinition
-----------------------------------------------------------------------------------------------
tsv         gwiq      200000      10000000    s3       bucket@foo/bar       /tmp/def.json
csv         gwiq      200000      10000000    fs       /tmp                 /tmp/def.json
json        gwiq      200000      10000000    fs,s3    /tmp,bucket@foo/bar  /tmp/def.json
```

Or use it as a dependency : 

```
"net.globalwebindex" %% "randagen" % "0.1-SNAPSHOT"
```

See example data-set sample [definition](deploy/sample.json) :