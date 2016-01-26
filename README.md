**High performance generator of random data ( WIP !!! )**

It can persist a billion events to FileSystem with 60GB of data in 20 minutes using just 6GB of Heap (s3 upload is way slower)
It is able to generate randomly distributed data with predefined cardinality : 

It shuffles data which is important for generating data with :
 - Random distribution
 - Zero duplication (100% cardinality or less - customizable)
  
Note that : 
 - for shuffling 1 billion integers you'd need 4GB+ of Heap
    - because the primitive 32bits Ints itself take 4GBs
    - random shuffling cannot be done lazily !!!
 - it is an incubator project, defining data-set can be done only programmatically !!!
 
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