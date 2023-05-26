The dockerfile in this directory will build an image that will run the traffic comparator on the json-formatted triples
that it receives and prints performance statistic.

It will run the necessary steps to setup the traffic comparator "run_app.sh" script which will run the traffic 
comparator on the triples it receives via port 9220.OPTIONALLY give a logfile that contains the diffs between the 
responses which can be saved (S3 bucket, shared volume, etc) by inserting a command that does that within the 
"run_app.sh", after the command that runs the traffic-comparator.

This part (--export-reports DiffReport diffs.log) of the command in "run_app.sh" should only be included if the user
wants the diffs. This part will give a logfile that contains the diffs between the responses which can be 
saved (S3 bucket, shared volume, etc) by inserting a command that does that within the "run_app.sh", after the command
that runs the traffic-comparator
'nc -l -p 9220 | trafficcomparator -v stream | trafficcomparator stream-report --export-reports DiffReport diffs.log'