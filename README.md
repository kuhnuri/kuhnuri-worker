Kuhnuri Worker
======================

DITA-OT Worker that communicates with a Queue over REST.

Process
-------

The Worker has a single loop where

1. Worker polls Queue for work
2. Queue returns a job
3. Worker downloads the source
4. Worker processes job
5. Worker uploads the results
6. Worker submits the results to Queue

If Queue has no work available, the process is

1. Worker polls Queue for work
2. Queue returns a no wor
3. Worker takes a nap

Source and results support
--------------------------

The following URI schemes are supported for source content:

*   file

The following URI schemes are supported for result content:

*   file
