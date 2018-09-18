Kuhnuri Worker
==============

DITA-OT Worker that communicates with a Queue over REST.

Process
-------

The Worker has a single loop where

1.  Worker polls Queue for work
2.  Queue returns a job
3.  Worker downloads the source
4.  Worker processes job
5.  Worker uploads the results
6.  Worker submits the results to Queue

If Queue has no work available, the process is

1.  Worker polls Queue for work
2.  Queue returns a no work
3.  Worker takes a nap

Source and results support
--------------------------

The following URI schemes are supported for source content:

-   file
-   jar

The following URI schemes are supported for result content:

-   file
-   jar

Building
--------

Compile the code:

1.  `sbt compile`

Develoment
----------

Running a development version:

1.  `sbt run`

Running in Docker
-----------------

The following volumes are used:

-   `/opt/workspace`: Temporary files directory

Deploying
---------

Build a distribution package:

1.  `sbt dist`
